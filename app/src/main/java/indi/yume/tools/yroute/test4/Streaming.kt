package indi.yume.tools.yroute.test4

import arrow.Kind
import arrow.core.*
import arrow.core.Either.*
import arrow.effects.ForIO
import arrow.effects.IO
import arrow.effects.extensions.io.fx.fx
import arrow.effects.extensions.io.monadDefer.binding
import arrow.effects.fix
import arrow.effects.internal.Platform
import arrow.effects.typeclasses.Async
import arrow.effects.typeclasses.Proc
import arrow.typeclasses.Monad
import arrow.typeclasses.MonadThrow
import java.io.BufferedReader
import java.io.FileReader
import java.io.FileWriter
import java.util.*
import java.util.concurrent.LinkedBlockingQueue

data class Await<F, A, O>(
    val req: Kind<F, A>,
    val recv: (Either<Throwable, A>) -> Process<F, O>) : Process<F, O>() {

    val anyRecv: (Either<Throwable, Any?>) -> Process<F, O> = { either ->
        when (either) {
            is Either.Left -> recv(either)
            is Either.Right -> recv(Either.right(either.b as A))
        }
    }
}

data class Emit<F, O>(
    val head: O,
    val tail: Process<F, O>) : Process<F, O>()

data class Halt<F, O>(val err: Throwable) : Process<F, O>()


/* Our generalized `Process` type can represent sources! */

/* Special exception indicating normal termination */
object End : Exception()

/* Special exception indicating forceful termination */
object Kill : Exception()


fun <F, O> emit(
    head: O,
    tail: Process<F, O> = Halt<F, O>(End)): Process<F, O> =
    Emit(head, tail)

fun <F, A, O> await(req: Kind<F, A>, recv: (Either<Throwable, A>) -> Process<F, O>): Process<F, O> =
    Await(req, recv)

sealed class Process<F, O> {
    /*
     * Many of the same operations can be defined for this generalized
     * `Process` type, regardless of the choice of `F`.
     */

    fun <O2> map(f: (O) -> O2): Process<F, O2> = when (this) {
        is Await<F, *, O> -> await(req, anyRecv andThen { it.map(f) })
        is Emit -> Try { Emit(f(head), tail.map(f)) }
        is Halt -> Halt(err)
    }

    operator fun plus(p: () -> Process<F, O>): Process<F, O> =
        this.onHalt {
            when (it) {
                is End -> Try(p) // we consult `p` only on normal termination
                else -> Halt(it)
            }
        }

    /*
     * Like `plus`, but _always_ runs `p`, even if `this` halts with an error.
     */
    fun onComplete(p: () -> Process<F, O>): Process<F, O> =
        this.onHalt {
            when (it) {
                is End -> p().asFinalizer()
                else -> p().asFinalizer() + { Halt(it) } // we always run `p`, but preserve any errors
            }
        }

    fun asFinalizer(): Process<F, O> = when (this) {
        is Emit -> Emit(head, tail.asFinalizer())
        is Halt -> Halt(err)
        is Await<F, *, O> -> await(req) {
            when {
                it is Either.Left && it.a == Kill -> this.asFinalizer()
                else -> anyRecv(it)
            }
        }
    }

    fun onHalt(f: (Throwable) -> Process<F, O>): Process<F, O> = when (this) {
        is Halt -> Try { f(err) }
        is Emit -> Emit(head, tail.onHalt(f))
        is Await<F, *, O> -> Await(req, anyRecv andThen { it.onHalt(f) })
    }

    /*
     * Anywhere we _call_ `f`, we catch exceptions and convert them to `Halt`.
     * See the helper function `Try` defined below.
     */
    fun <O2> flatMap(f: (O) -> Process<F, O2>): Process<F, O2> =
        when (this) {
            is Halt -> Halt(err)
            is Emit -> Try { f(head) } + { tail.flatMap(f) }
            is Await<F, *, O> ->
                Await(req, anyRecv andThen { it.flatMap(f) })
        }

    fun repeat(): Process<F, O> =
        this + this::repeat

    fun repeatNonempty(): Process<F, O> {
        val cycle = this.map { o -> o.some() } + { emit<F, Option<O>>(none()).repeat() }
        // cut off the cycle when we see two `None` values in a row, as this
        // implies `this` has produced no values during an iteration
        val trimmed = cycle pipeTo window2() pipeTo (takeWhile {
            val (i, i2) = it
            when {
                i is Some && i.t is None && i2 is None -> false
                else -> true
            }
        })
        return trimmed.map { it.second }.flatMap {
            when (it) {
                is None -> Halt(End)
                is Some -> emit<F, O>(it.t)
            }
        }
    }

    /*
     * Exercise 10: This function is defined only if given a `MonadCatch[F]`.
     * Unlike the simple `runLog` interpreter defined in the companion object
     * below, this is not tail recursive and responsibility for stack safety
     * is placed on the `Monad` instance.
     */
    fun runLog(MC: MonadThrow<F>): Kind<F, List<O>> {
        tailrec fun go(cur: Process<F, O>, acc: List<O>): Kind<F, List<O>> =
            when (cur) {
                is Emit -> go(cur.tail, acc + cur.head)
                is Halt -> if (cur.err == End) MC.just(acc) else MC.raiseError(cur.err)
                is Await<F, *, O> -> MC.run {
                    cur.req.attempt().flatMap { e -> go(Try { cur.anyRecv(e) }, acc) }
                }
            }
        return go(this, emptyList())
    }

    /*
     * We define `Process1` as a type alias - see the companion object
     * for `Process` below. Using that, we can then define `|>` once
     * more. The definition is extremely similar to our previous
     * definition. We again use the helper function, `feed`, to take
     * care of the is where `this` is emitting values while `p2`
     * is awaiting these values.
     *
     * The one subtlety is we make sure that if `p2` halts, we
     * `kill` this process, giving it a chance to run any cleanup
     * actions (like closing file handles, etc).
     */
    infix fun <O2> pipeTo(p2: Process1<O, O2>): Process<F, O2> =
        when (p2) {
            is Halt -> this.kill<O2>().onHalt { e2 -> Halt<F, O2>(p2.err) + { Halt(e2) } }
            is Emit -> Emit(p2.head, this pipeTo p2.tail)
            is Await<Is<O>.f, *, O2> -> when (this) {
                is Halt -> Halt<F, O>(err) pipeTo (p2.anyRecv(Left(err))).kill<O2>()
                is Emit -> tail pipeTo Try { p2.anyRecv(Right(head)) }
                is Await<F, *, O> -> await(req, anyRecv andThen { it pipeTo p2 })
            }
        }

    fun <O2> kill(): Process<F, O2> {
        tailrec fun <O2> kill(p: Process<F, O>): Process<F, O2> = when (p) {
            is Await<F, *, O> -> p.recv(Either.Left(Kill)).drain<O2>().onHalt {
                when (it) {
                    is Kill -> Halt(End) // we convert the `Kill` exception back to normal termination
                    else -> Halt(it)
                }
            }
            is Halt -> Halt(p.err)
            is Emit -> kill(p.tail)
        }

        return kill(this)
    }

    /** Alias for `this |> p2`. */
    fun <O2> pipe(p2: Process1<O, O2>): Process<F, O2> =
        this pipeTo p2

    fun <O2> drain(): Process<F, O2> = when (this) {
        is Halt -> Halt(err)
        is Emit -> tail.drain()
        is Await<F, *, O> -> Await(req, anyRecv andThen { it.drain<O2>() })
    }

    fun filter(f: (O) -> Boolean): Process<F, O> =
        this pipeTo Process.filter(f)

    fun take(n: Int): Process<F, O> =
        this pipeTo Process.take(n)

    fun once(): Process<F, O> = take(1)

    /*
     * Use a `Tee` to interleave or combine the outputs of `this` and
     * `p2`. This can be used for zipping, interleaving, and so forth.
     * Nothing requires that the `Tee` read elements from each
     * `Process` in lockstep. It could read fifty elements from one
     * side, then two elements from the other, then combine or
     * interleave these values in some way, etc.
     *
     * This definition uses two helper functions, `feedL` and `feedR`,
     * which feed the `Tee` in a tail-recursive loop as long as
     * it is awaiting input.
     */

    interface TeeFun<F, O, O2> {
        operator fun <T> invoke(t: Tee<O, O2, T>): Process<F, T>
    }

    infix fun <O2> tee(p2: Process<F, O2>): TeeFun<F, O, O2> = object : TeeFun<F, O, O2> {
        override fun <T> invoke(t: Tee<O, O2, T>): Process<F, T> = tee(p2, t)
    }

    fun <O2, O3> tee(p2: Process<F, O2>, t: Tee<O, O2, O3>): Process<F, O3> {
        return when (t) {
            is Halt -> this.kill<O3>().onComplete { p2.kill() }.onComplete { Halt(t.err) }
            is Emit -> Emit(t.head, (this tee p2)(t.tail))
            is Await<T<O, O2>.f, *, O3> -> when (t.req.fix().get) {
                is Left -> when (this) {
                    is Halt -> p2.kill<O3>().onComplete { Halt(err) }
                    is Emit -> (tail tee p2)(Try { t.anyRecv(Right(head)) })
                    is Await<F, *, O> -> await(req, anyRecv andThen { this2 -> (this2 tee p2)(t) })
                }
                is Right -> when (p2) {
                    is Halt -> this.kill<O3>().onComplete { Halt(p2.err) }
                    is Emit -> this.tee(p2.tail)(Try { t.anyRecv(Right(p2.head)) })
                    is Await<F, *, O2> -> await(p2.req, p2.anyRecv andThen { p3 -> (this tee p3)(t) })
                }
            }
        }
    }

    fun <O2, O3> zipWith(p2: Process<F, O2>, f: (O, O2) -> O3): Process<F, O3> =
        (this tee p2)(zipWith(f))

    fun <O2> zip(p2: Process<F, O2>): Process<F, Pair<O, O2>> =
        zipWith(p2) { o, o2 -> o to o2 }

    fun to(sink: Sink<F, O>): Process<F, Unit> =
        join((this.zipWith(sink) { o, f -> f(o) }))

    fun <O2> through(p2: Channel<F, O, O2>): Process<F, O2> =
        join(this.zipWith(p2) { o, f -> f(o) })

    companion object {
        /**
         * Helper function to safely produce `p`, or gracefully halt
         * with an error if an exception is thrown.
         */
        fun <F, O> Try(p: () -> Process<F, O>): Process<F, O> =
            try {
                p()
            } catch (e: Throwable) {
                Halt(e)
            }

        /*
         * Safely produce `p`, or run `cleanup` and halt gracefully with the
         * exception thrown while evaluating `p`.
         */
        fun <F, O> TryOr(p: () -> Process<F, O>, cleanup: Process<F, O>): Process<F, O> =
            try {
                p()
            } catch (e: Throwable) {
                cleanup + { Halt(e) }
            }

        /*
         * Safely produce `p`, or run `cleanup` or `fallback` if an exception
         * occurs while evaluating `p`.
         */
        fun <F, O> TryAwait(p: () -> Process<F, O>, fallback: Process<F, O>, cleanup: Process<F, O>): Process<F, O> =
            try {
                p()
            } catch (e: Throwable) {
                when (e) {
                    is End -> fallback
                    else -> cleanup + { Halt(e) }
                }
            }

        /*
         * A `Process<F, O>` where `F` is a monad like `IO` can be thought of
         * as a source.
         */

        /*
         * Here is a simple tail recursive function to collect all the
         * output of a `Process<IO, O>`. Notice we are using the fact
         * that `IO` can be `run` to produce either a result or an
         * exception.
         */
        fun <O> runLog(src: Process<ForIO, O>): IO<List<O>> = IO.defer {
            tailrec fun go(cur: Process<ForIO, O>, acc: List<O>): IO<List<O>> =
                when (cur) {
                    is Emit -> go(cur.tail, acc + cur.head)
                    is Halt -> if (cur.err == End) IO.just(acc) else IO.raiseError(cur.err)
                    is Await<ForIO, *, O> -> binding {
                        val result: Any? = !cur.req
                        val next = cur.anyRecv(result.right())
                        !go(next, acc)
                    }
                }

            go(src, emptyList())
        }

        fun <F, O> runLog(MT: MonadThrow<F>, src: Process<F, O>): Kind<F, List<O>> {
            tailrec fun go(cur: Process<F, O>, acc: List<O>): Kind<F, List<O>> =
                when (cur) {
                    is Emit -> go(cur.tail, acc + cur.head)
                    is Halt -> if (cur.err == End) MT.just(acc) else MT.raiseError(cur.err)
                    is Await<F, *, O> -> MT.bindingCatch {
                        val result: Any? = !cur.req
                        val next = cur.anyRecv(result.right())
                        !go(next, acc)
                    }
                }
            IO.fx()

            return MT.bindingCatch {
                !go(src, emptyList())
            }
        }

        /*
         * Generic combinator for producing a `Process<IO, O>` from some
         * effectful `O` source. The source is tied to some resource,
         * `R` (like a file handle) that we want to ensure is released.
         * See `lines` below for an example use.
         */
        fun <R, O> resource(acquire: IO<R>,
                            use: (R) -> Process<ForIO, O>,
                            release: (R) -> Process<ForIO, O>): Process<ForIO, O> =
            eval(acquire).flatMap { r -> use(r).onComplete { release(r) } }

        /*
         * Like `resource`, but `release` is a single `IO` action.
         */
        fun <R, O> resource_(acquire: IO<R>,
                             use: (R) -> Process<ForIO, O>,
                             release: (R) -> IO<Unit>): Process<ForIO, O> =
            resource(acquire, use, release andThen { eval_<ForIO, Unit, O>(it) })

        /* Exercise 11: Implement `eval`, `eval_`, and use these to implement `lines`. */
        fun <F, A> eval(a: Kind<F, A>): Process<F, A> =
            await<F, A, A>(a) {
                when (it) {
                    is Left -> Halt(it.a)
                    is Right -> Emit(it.b, Halt(End))
                }
            }

        /* Evaluate the action purely for its effects. */
        fun <F, A, B> eval_(a: Kind<F, A>): Process<F, B> =
            eval<F, A>(a).drain<B>()

        /* Helper function with better type inference. */
        fun <A> evalIO(a: IO<A>): Process<ForIO, A> = eval(a)


        /* Some helper functions to improve type inference. */

        fun <I, O> await1(recv: (I) -> Process1<I, O>,
                          fallback: () -> Process1<I, O> = { halt1<I, O>() }): Process1<I, O> =
            Await(Get<I>()) { e: Either<Throwable, I> ->
                when {
                    e is Left && e.a == End -> fallback()
                    e is Left -> Halt(e.a)
                    e is Right -> Try { recv(e.b) }
                    else -> throw RuntimeException("Unreachable")
                }
            }

        fun <I, O> emit1(h: O, tl: Process1<I, O> = halt1<I, O>()): Process1<I, O> =
            emit(h, tl)

        fun <I, O> halt1(): Process1<I, O> = Halt<Is<I>.f, O>(End)

        fun <I, O> lift(f: (I) -> O): Process1<I, O> =
            await1<I, O>({ i: I -> emit(f(i)) }).repeat()

        fun <I> filter(f: (I) -> Boolean): Process1<I, I> =
            await1<I, I>({ i -> if (f(i)) emit(i) else halt1() }).repeat()

        // we can define take, takeWhile, and so on as before

        fun <I> take(n: Int): Process1<I, I> =
            if (n <= 0) halt1()
            else await1<I, I>({ i -> emit(i, take(n - 1)) })

        fun <I> takeWhile(f: (I) -> Boolean): Process1<I, I> =
            await1({ i ->
                if (f(i)) emit(i, takeWhile(f))
                else halt1()
            })

        fun <I> dropWhile(f: (I) -> Boolean): Process1<I, I> =
            await1({ i ->
                if (f(i)) dropWhile(f)
                else emit(i, id())
            })

        fun <I> id(): Process1<I, I> =
            await1({ i: I -> emit(i, id()) })

        fun <I> window2(): Process1<I, Pair<Option<I>, I>> {
            fun go(prev: Option<I>): Process1<I, Pair<Option<I>, I>> =
                await1({ i -> emit1<I, Pair<Option<I>, I>>(prev to i) + { go(Some(i)) } })

            return go(None)
        }

        /** Emits `sep` in between each input received. */
        fun <I> intersperse(sep: I): Process1<I, I> =
            await1<I, I>(recv = { i -> emit1<I, I>(i) + { id<I>().flatMap { i2 -> emit1<I, I>(sep) + { emit1(i2) } } } })


        fun <I, I2, A> narrow(value: Kind<T<I, I2>.f, A>): T<I, I2>.Tf<A> = value as T<I, I2>.Tf<A>

        fun <I, I2, A> Kind<T<I, I2>.f, A>.fix(): T<I, I2>.Tf<A> = narrow(this)

        /* Again some helper functions to improve type inference. */

        fun <I, I2, O> haltT(): Tee<I, I2, O> =
            Halt<T<I, I2>.f, O>(End)

        fun <I, I2, O> awaitL(recv: (I) -> Tee<I, I2, O>,
                              fallback: () -> Tee<I, I2, O> = { haltT<I, I2, O>() }): Tee<I, I2, O> =
            await<T<I, I2>.f, I, O>(L())
            {
                when {
                    it is Left && it.a == End -> fallback()
                    it is Left -> Halt(it.a)
                    it is Right -> Try { recv(it.b) }
                    else -> throw RuntimeException("Unreachable")
                }
            }

        fun <I, I2, O> awaitR(recv: (I2) -> Tee<I, I2, O>,
                              fallback: () -> Tee<I, I2, O> = { haltT<I, I2, O>() }): Tee<I, I2, O> =
            await<T<I, I2>.f, I2, O>(R())
            {
                when {
                    it is Left && it.a == End -> fallback()
                    it is Left -> Halt(it.a)
                    it is Right -> Try { recv(it.b) }
                    else -> throw RuntimeException("Unreachable")
                }
            }

        fun <I, I2, O> emitT(h: O, tl: Tee<I, I2, O> = haltT<I, I2, O>()): Tee<I, I2, O> =
            emit(h, tl)

        fun <I, I2, O> zipWith(f: (I, I2) -> O): Tee<I, I2, O> =
            awaitL<I, I2, O>({ i ->
                awaitR({ i2 -> emitT(f(i, i2)) })
            }).repeat()

        fun <I, I2> zip(): Tee<I, I2, Pair<I, I2>> = zipWith({ i1, i2 -> i1 to i2 })

        /* Ignores all input from left. */
        fun <I, I2> passR(): Tee<I, I2, I2> = awaitR({ emitT(it, passR()) })

        /* Ignores input from the right. */
        fun <I, I2> passL(): Tee<I, I2, I> = awaitL({ emitT(it, passL()) })

        /* Alternate pulling values from the left and the right inputs. */
        fun <I> interleaveT(): Tee<I, I, I> =
            awaitL<I, I, I>({ i ->
                awaitR({ i2 -> emitT<I, I, I>(i) + { emitT(i2) } })
            }).repeat()

        /* The infinite, constant stream. */
        fun <A> constant(a: A): Process<ForIO, A> =
            eval(IO { a }).flatMap { a -> Emit(a, constant(a)) }

        /* Exercise 12: Implement `join`. Notice this is the standard monadic combinator! */
        fun <F, A> join(p: Process<F, Process<F, A>>): Process<F, A> =
            p.flatMap { pa -> pa }

        fun <F, T> fromList(MD: Monad<F>, list: List<T>): Process<F, T> {
            val iter by lazy { list.listIterator() } // a stateful iterator
            fun step() = if (iter.hasNext()) Some(iter.next()) else None

            fun lines(): Process<F, T> =
                Process.eval(MD.just(step())).flatMap {
                    when (it) {
                        is None -> Halt<F, T>(End)
                        is Some -> Emit(it.t, lines())
                    }
                }

            return lines()
        }

        fun <F, T> create(AS: Async<F>, starter: (Emitter<T>) -> Unit): Process<F, T> {
            val creator: Proc<EventType<T>> = { cb ->
                println("Create start")
                val emitter = object : Emitter<T> {
                    override fun onNext(t: T) = cb(EventType.OnNext(t).right())

                    override fun onComplete() = cb(EventType.OnComplete.right())
                }
                starter(emitter)
            }

            val step = AS.async(Platform.onceOnly(creator))

            fun process(): Process<F, T> =
                    Process.eval(step).flatMap { event ->
                        when (event) {
                            is EventType.OnComplete -> Halt<F, T>(End)
                            is EventType.OnNext -> Emit(event.t, process())
                        }
                    }

            return process()
        }
    }
}

interface Emitter<T> {
    fun onNext(t: T)

    fun onComplete()
}

sealed class EventType<out T> {
    data class OnNext<T>(val t: T) : EventType<T>()

    object OnComplete: EventType<Nothing>()
}


/*
 * We now have nice, resource safe effectful sources, but we don't
 * have any way to transform them or filter them. Luckily we can
 * still represent the single-input `Process` type we introduced
 * earlier, which we'll now call `Process1`.
 */

class Is<I> {
    inner class f

    inner class IsfK : Kind<f, I>

    val Get = IsfK()
}

fun <I> Get(): Kind<Is<I>.f, I> = Is<I>().Get

typealias Process1<I, O> = Process<Is<I>.f, O>


/*
We sometimes need to construct a `Process` that will pull values
from multiple input sources. For instance, suppose we want to
'zip' together two files, `f1.txt` and `f2.txt`, combining
corresponding lines in some way. Using the same trick we used for
`Process1`, we can create a two-input `Process` which can request
values from either the 'left' stream or the 'right' stream. We'll
call this a `Tee`, after the letter 'T', which looks like a
little diagram of two inputs being combined into one output.
 */

class T<I, I2> {
    abstract inner class Tf<X> : Kind<f, X> {
        abstract val get: Either<(I) -> X, (I2) -> X>
    }

    inner class f

    val L = object : Tf<I>() {
        override val get: Either<(I) -> I, (I2) -> I> = Left({ i: I -> i })
    }
    val R = object : Tf<I2>() {
        override val get: Either<(I) -> I2, (I2) -> I2> = Right({ i: I2 -> i })
    }
}

fun <I, I2> L() = T<I, I2>().L
fun <I, I2> R() = T<I, I2>().R

typealias Tee<I, I2, O> = Process<T<I, I2>.f, O>


/*
Our `Process` type can also represent effectful sinks (like a file).
A `Sink` is simply a source of effectful functions! See the
definition of `to` in `Process` for an example of how to feed a
`Process` to a `Sink`.
 */

typealias Sink<F, O> = Process<F, (O) -> Process<F, Unit>>


/*
More generally, we can feed a `Process` through an effectful
channel which returns a value other than `Unit`.
 */

typealias Channel<F, I, O> = Process<F, (I) -> Process<F, O>>



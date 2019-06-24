package indi.yume.tools.yroute.datatype.lazy

import arrow.core.*
import arrow.data.Reader
import arrow.data.ReaderT
import arrow.data.StateT
import arrow.data.extensions.kleisli.monad.monad
import arrow.data.runId
import arrow.effects.ForIO
import arrow.effects.IO
import arrow.effects.extensions.io.monad.monad
import arrow.effects.extensions.io.monadDefer.binding
import arrow.generic.coproduct2.Coproduct2
import arrow.generic.coproduct2.First
import arrow.generic.coproduct2.Second
import arrow.generic.coproduct2.fold
import arrow.generic.coproduct3.Coproduct3
import arrow.generic.coproduct3.fold
import arrow.optics.Lens
import indi.yume.tools.yroute.*
import indi.yume.tools.yroute.datatype.*
import indi.yume.tools.yroute.datatype.Success


typealias LazyYRoute<S, P, R> = Reader<P, YRoute<S, R>>

fun <S, P, R> lazyR1(f: (P) -> YRoute<S, R>): LazyYRoute<S, P, R> = Reader { Id(f(it)) }

fun <S, P1, P2, R> lazyR2(f: (P1, P2) -> YRoute<S, R>): LazyYRoute<S, Tuple2<P1, P2>, R> = Reader { Id(f(it.a, it.b)) }

fun <S, P1, P2, P3, R> lazyR3(f: (P1, P2, P3) -> YRoute<S, R>): LazyYRoute<S, Tuple3<P1, P2, P3>, R> =
    Reader { Id(f(it.a, it.b, it.c)) }

fun <S, P1, P2, P3, P4, R> lazyR4(f: (P1, P2, P3, P4) -> YRoute<S, R>): LazyYRoute<S, Tuple4<P1, P2, P3, P4>, R> =
    Reader { Id(f(it.a, it.b, it.c, it.d)) }


inline fun <S, T, R> route(crossinline f: (S) -> (Tuple2<RouteCxt, T>) -> IO<Tuple2<S, YResult<R>>>): LazyYRoute<S, T, R> =
    Reader { t ->
        Id(StateT(ReaderT.monad<ForIO, RouteCxt>(IO.monad()))
        { state -> ReaderT { f(state)(it toT t) } })
    }

inline fun <S, T, R> routeF(crossinline f: (S, RouteCxt, T) -> IO<Tuple2<S, YResult<R>>>): LazyYRoute<S, T, R> =
    Reader { t ->
        Id(StateT(ReaderT.monad<ForIO, RouteCxt>(IO.monad()))
        { state -> ReaderT { cxt -> f(state, cxt, t) } })
    }

fun <S, T, R> LazyYRoute<S, T, R>.runRoute(state: S, cxt: RouteCxt, t: T): IO<Tuple2<S, YResult<R>>> =
    this.runId(t).runRoute(state, cxt)

fun <S, T, R> LazyYRoute<S, T, R>.toAction(param: T): EngineAction<S, T, R> =
    this@toAction toT param

fun <S, T> routeId(): LazyYRoute<S, T, T> = routeF { s, c, t -> IO.just(s toT Success(t)) }

fun <S, T> routeGetState(): LazyYRoute<S, T, S> = routeF { s, _, _ -> IO.just(s toT Success(s)) }

fun <S, T> routeFromIO(io: IO<Unit>): LazyYRoute<S, T, Unit> =
    routeF { state, _, _ -> io.map { state toT Success(Unit) } }

fun <S, T, R> routeFromF(f: (T) -> R): LazyYRoute<S, T, R> =
    routeF { state, cxt, param -> IO.just(state toT Success(f(param))) }

fun <S, T, R, R2> LazyYRoute<S, T, R>.flatMapR(f: (R) -> LazyYRoute<S, T, R2>): LazyYRoute<S, T, R2> =
    routeF { state, cxt, param ->
        binding {
            val (newState1, result1) = !this@flatMapR.runRoute(state, cxt, param)

            when (result1) {
                is Fail -> newState1 toT result1
                is Success -> !f(result1.t).runRoute(newState1, cxt, param)
            }
        }
    }

fun <S1, T1, S2, T2, R> LazyYRoute<S1, T1, Lens<S1, S2>>.composeState(route: LazyYRoute<S2, T2, R>): LazyYRoute<S1, Tuple2<T1, T2>, R> =
    routeF { state1, cxt, (t1, t2) ->
        binding {
            val (innerState1, lensResult) = !this@composeState.runRoute(state1, cxt, t1)

            when (lensResult) {
                is Fail -> innerState1 toT lensResult
                is Success -> {
                    val state2 = lensResult.t.get(state1)
                    val (newState2, result) = !route.runRoute(state2, cxt, t2)
                    val newState1 = lensResult.t.set(innerState1, newState2)
                    newState1 toT result
                }
            }
        }
    }

fun <S : Any, T, R> LazyYRoute<S, T, R>.stateNullable(): LazyYRoute<S?, T, R> =
    routeF { state, cxt, param ->
        if (state == null)
            IO.just(state toT Fail("State is null, can not get state."))
        else
            this@stateNullable.runRoute(state, cxt, param)
    }

fun <S1, T1, T, S2, R> LazyYRoute<S2, T1, R>.changeState(f: (T) -> Lens<S1, S2>): LazyYRoute<S1, Tuple2<T, T1>, R> =
    mapParam(type<Tuple2<T, T1>>()) { it.b }.transStateByParam { f(it.a) }

fun <S1, T, S2, R> LazyYRoute<S2, T, R>.transStateByParam(f: (T) -> Lens<S1, S2>): LazyYRoute<S1, T, R> =
    routeF { state1, cxt, param ->
        binding {
            val lens = f(param)
            val state2 = lens.get(state1)
            val (newState1, result) = !this@transStateByParam.runRoute(state2, cxt, param)

            val newState2 = lens.set(state1, newState1)
            newState2 toT result
        }
    }

fun <S1, S2, R> LazyYRoute<S2, Lens<S1, S2>, R>.apForState(): LazyYRoute<S1, Lens<S1, S2>, R> =
    routeF { state1, cxt, lens ->
        binding {
            val state2 = lens.get(state1)
            val (newState1, result) = !this@apForState.runRoute(state2, cxt, lens)
            val newState2 = lens.set(state1, newState1)
            newState2 toT result
        }
    }

fun <S, T, T1, R> LazyYRoute<S, T, T1>.transform(f: (S, RouteCxt, T1) -> IO<Tuple2<S, YResult<R>>>): LazyYRoute<S, T, R> =
    routeF { state, cxt, param ->
        binding {
            val (tuple2) = this@transform.runRoute(state, cxt, param)
            val (newState, resultT1) = tuple2

            when(resultT1) {
                is Success -> !f(newState, cxt, resultT1.t)
                is Fail -> newState toT resultT1
            }
        }
    }

fun <S1, S2, T, R> LazyYRoute<S1, T, R>.mapStateF(lensF: (T) -> Lens<S2, S1>): LazyYRoute<S2, T, R> =
    routeF { state2, cxt, param ->
        val lens = lensF(param)
        val state1 = lens.get(state2)
        binding {
            val (newState1, result) = !this@mapStateF.runRoute(state1, cxt, param)
            val newState2 = lens.set(state2, newState1)
            newState2 toT result
        }
    }

fun <S1, S2, T, R> LazyYRoute<S1, T, R>.mapState(lens: Lens<S2, S1>): LazyYRoute<S2, T, R> =
    routeF { state2, cxt, param ->
        val state1 = lens.get(state2)
        binding {
            val (newState1, result) = !this@mapState.runRoute(state1, cxt, param)
            val newState2 = lens.set(state2, newState1)
            newState2 toT result
        }
    }

fun <S, T1, T2, R> LazyYRoute<S, T1, R>.plusParam(type: TypeCheck<T2>): LazyYRoute<S, Tuple2<T1, T2>, R> =
    mapParam { it.a }

fun <S, T1, T2, R> LazyYRoute<S, T1, R>.mapParam(type: TypeCheck<T2> = type(), f: (T2) -> T1): LazyYRoute<S, T2, R> =
    routeF { state, cxt, param -> this@mapParam.runRoute(state, cxt, f(param)) }

fun <S, T, T1, R> LazyYRoute<S, T, T1>.mapResult(f: (T1) -> R): LazyYRoute<S, T, R> =
    transform { state, cxt, t1 -> IO.just(state toT Success(f(t1))) }

fun <S, T, R : Any> LazyYRoute<S, T, R?>.resultNonNull(tag: String = "resultNonNull()"): LazyYRoute<S, T, R> =
    transform { state, cxt, t1 ->
        IO.just(state toT if (t1 != null) Success(t1) else Fail("Tag $tag | YResult can not be Null."))
    }

fun <S, T, T1, R> LazyYRoute<S, T, T1>.composeWith(f: (S, RouteCxt, T, T1) -> IO<Tuple2<S, YResult<R>>>): LazyYRoute<S, T, R> =
    routeF { state, cxt, param ->
        binding {
            val (tuple2) = this@composeWith.runRoute(state, cxt, param)
            val (newState, resultT1) = tuple2

            when(resultT1) {
                is Success -> !f(newState, cxt, param, resultT1.t)
                is Fail -> newState toT resultT1
            }
        }
    }

fun <S, T1, T2, R1, R2> LazyYRoute<S, T1, R1>.zipWithFunc(f: (T2) -> R2): LazyYRoute<S, Tuple2<T1, T2>, Tuple2<R1, R2>> =
    routeF { state, cxt, (param1, param2) ->
        binding {
            val (newState, result1) = !this@zipWithFunc.runRoute(state, cxt, param1)
            newState toT result1.map { it toT f(param2) }
        }
    }

fun <S1, S2, T1, T2, R1, R2> zipRoute(route1: LazyYRoute<S1, T1, R1>, route2: LazyYRoute<S2, T2, R2>): LazyYRoute<Tuple2<S1, S2>, Tuple2<T1, T2>, Tuple2<R1, R2>> =
    routeF { (state1, state2), cxt, (param1, param2) ->
        binding {
            val (newState1, result1) = !route1.runRoute(state1, cxt, param1)
            val (newState2, result2) = !route2.runRoute(state2, cxt, param2)
            (newState1 toT newState2) toT result1.flatMap { r1 -> result2.map { r2 -> r1 toT r2 } }
        }
    }

fun <S, T, R1, R2> LazyYRoute<S, T, Tuple2<R1, R2>>.ignoreLeft(): LazyYRoute<S, T, R2> = mapResult { it.b }

fun <S, T, R1, R2> LazyYRoute<S, T, Tuple2<R1, R2>>.ignoreRight(): LazyYRoute<S, T, R1> = mapResult { it.a }

fun <S, T, R> LazyYRoute<S, T, R>.packageParam(): LazyYRoute<S, T, Tuple2<T, R>> =
    routeF { state, cxt, param ->
        this@packageParam.runRoute(state, cxt, param)
            .map { it.a toT it.b.map { r -> param toT r } }
    }

fun <S, T, R1, R2> LazyYRoute<S, T, Tuple2<R1, R2>>.switchResult(): LazyYRoute<S, T, Tuple2<R2, R1>> = mapResult { it.b toT it.a }

infix fun <S, T, T1, R> LazyYRoute<S, T, T1>.compose(route2: LazyYRoute<S, T1, R>): LazyYRoute<S, T, R> =
    routeF { state, cxt, param ->
        binding {
            val (tuple2) = this@compose.runRoute(state, cxt, param)
            val (newState, resultT1) = tuple2

            when(resultT1) {
                is Success -> !route2.runRoute(newState, cxt, resultT1.t)
                is Fail -> newState toT resultT1
            }
        }
    }

fun <S, SS, T, R> LazyYRoute<SS, T, R>.subRoute(type: TypeCheck<S>, mapper: (S) -> SS, demapper: (SS, S) -> S): LazyYRoute<S, T, R> =
    routeF { state, cxt, param ->
        binding {
            val sstate = mapper(state)

            val (newSS, result) = !this@subRoute.runRoute(sstate, cxt, param)

            val newS = demapper(newSS, state)

            newS toT result
        }
    }

fun <S, SS, T, R> LazyYRoute<S, T, LazyYRoute<SS, T, R>>.flatten(type: TypeCheck<T>, mapper: (S) -> SS, demapper: (SS) -> S): LazyYRoute<S, T, R> =
    routeF { state, cxt, param ->
        binding {
            val (innerS, innerLazyYRouteResult) = !this@flatten.runRoute(state, cxt, param)

            val sstate = mapper(innerS)

            when (innerLazyYRouteResult) {
                is Success -> {
                    val (newSS, innerResult) = !innerLazyYRouteResult.t.runRoute(sstate, cxt, param)

                    val newS = demapper(newSS)
                    newS toT innerResult
                }
                is Fail -> innerS toT innerLazyYRouteResult
            }
        }
    }

fun <S, T1, T2, R1, R2> stick(route1: LazyYRoute<S, T1, R1>, route2: LazyYRoute<S, T2, R2>): LazyYRoute<S, Coproduct2<T1, T2>, Coproduct2<R1, R2>> =
    routeF { state, cxt, param ->
        param.fold(
            { t1 -> route1.runRoute(state, cxt, t1).map { it.map { result -> result.map { r1 -> First<R1, R2>(r1) } } } },
            { t2 -> route2.runRoute(state, cxt, t2).map { it.map { result -> result.map { r2 -> Second<R1, R2>(r2) } } } }
        )
    }

fun <S, T1, T2, R1, R2> LazyYRoute<S, Coproduct2<T1, T2>, Coproduct2<R1, R2>>.runRoute21(state: S, cxt: RouteCxt, t1: T1): IO<Tuple2<S, YResult<R1>>> =
    runRoute(state, cxt, First<T1, T2>(t1)).map { tuple -> tuple.map { result -> result.map { (it as First).a } } }

fun <S, T1, T2, R1, R2> LazyYRoute<S, Coproduct2<T1, T2>, Coproduct2<R1, R2>>.runRoute22(state: S, cxt: RouteCxt, t2: T2): IO<Tuple2<S, YResult<R2>>> =
    runRoute(state, cxt, Second<T1, T2>(t2)).map { tuple -> tuple.map { result -> result.map { (it as Second).b } } }


fun <S, T1, T2, T3, R1, R2, R3> stick(route1: LazyYRoute<S, T1, R1>, route2: LazyYRoute<S, T2, R2>, route3: LazyYRoute<S, T3, R3>): LazyYRoute<S, Coproduct3<T1, T2, T3>, Coproduct3<R1, R2, R3>> =
    routeF { state, cxt, param ->
        param.fold(
            { t1 -> route1.runRoute(state, cxt, t1).map { it.map { result -> result.map { r1 -> arrow.generic.coproduct3.First<R1, R2, R3>(r1) } } } },
            { t2 -> route2.runRoute(state, cxt, t2).map { it.map { result -> result.map { r2 -> arrow.generic.coproduct3.Second<R1, R2, R3>(r2) } } } },
            { t3 -> route3.runRoute(state, cxt, t3).map { it.map { result -> result.map { r3 -> arrow.generic.coproduct3.Third<R1, R2, R3>(r3) } } } }
        )
    }

fun <S, T1, T2, T3, R1, R2, R3> LazyYRoute<S, Coproduct3<T1, T2, T3>, Coproduct3<R1, R2, R3>>.runRoute31(state: S, cxt: RouteCxt, t1: T1): IO<Tuple2<S, YResult<R1>>> =
    runRoute(state, cxt, arrow.generic.coproduct3.First<T1, T2, T3>(t1))
        .map { tuple -> tuple.map { result -> result.map { (it as arrow.generic.coproduct3.First).a } } }

fun <S, T1, T2, T3, R1, R2, R3> LazyYRoute<S, Coproduct3<T1, T2, T3>, Coproduct3<R1, R2, R3>>.runRoute32(state: S, cxt: RouteCxt, t2: T2): IO<Tuple2<S, YResult<R2>>> =
    runRoute(state, cxt, arrow.generic.coproduct3.Second<T1, T2, T3>(t2))
        .map { tuple -> tuple.map { result -> result.map { (it as arrow.generic.coproduct3.Second).b } } }

fun <S, T1, T2, T3, R1, R2, R3> LazyYRoute<S, Coproduct3<T1, T2, T3>, Coproduct3<R1, R2, R3>>.runRoute33(state: S, cxt: RouteCxt, t3: T3): IO<Tuple2<S, YResult<R3>>> =
    runRoute(state, cxt, arrow.generic.coproduct3.Third<T1, T2, T3>(t3))
        .map { tuple -> tuple.map { result -> result.map { (it as arrow.generic.coproduct3.Third).c } } }

//</editor-fold>

typealias EngineAction<S, T, R> = Tuple2<LazyYRoute<S, T, R>, T>



fun <S, T, R> EngineAction<S, T, R>.startAsync(core: CoreEngine<S>, callback: (YResult<R>) -> Unit): IO<Unit> =
    core.runAsync(a.runId(b), callback)

fun <S, T, R> EngineAction<S, T, R>.start(core: CoreEngine<S>): IO<YResult<R>> =
    core.run(a.runId(b))

fun <S, R> LazyYRoute<S, Unit, R>.startAsync(core: CoreEngine<S>, callback: (YResult<R>) -> Unit): IO<Unit> =
    (this toT Unit).startAsync(core, callback)

fun <S, R> LazyYRoute<S, Unit, R>.start(core: CoreEngine<S>): IO<YResult<R>> = (this toT Unit).start(core)



fun <S, T, R> LazyYRoute<S, T, R>.withParam(t: T): EngineAction<S, T, R> = this toT t

fun <S, T1, T2, R> LazyYRoute<S, Tuple2<T1, T2>, R>.withParams(t1: T1, t2: T2): EngineAction<S, Tuple2<T1, T2>, R> =
    this toT (t1 toT t2)

fun <S, T1, T2, T3, R> LazyYRoute<S, Tuple3<T1, T2, T3>, R>.withParams(t1: T1, t2: T2, t3: T3)
        : EngineAction<S, Tuple3<T1, T2, T3>, R> =
    this toT (Tuple3(t1, t2, t3))

fun <S, T1, T2, T3, T4, R> LazyYRoute<S, Tuple4<T1, T2, T3, T4>, R>.withParams(t1: T1, t2: T2, t3: T3, t4: T4)
        : EngineAction<S, Tuple4<T1, T2, T3, T4>, R> =
    this toT (Tuple4(t1, t2, t3, t4))

fun <S, T1, T2, T3, T4, T5, R> LazyYRoute<S, Tuple5<T1, T2, T3, T4, T5>, R>.withParams(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5)
        : EngineAction<S, Tuple5<T1, T2, T3, T4, T5>, R> =
    this toT (Tuple5(t1, t2, t3, t4, t5))
//</editor-fold>


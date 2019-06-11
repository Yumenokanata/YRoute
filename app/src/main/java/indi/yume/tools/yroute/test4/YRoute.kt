package indi.yume.tools.yroute.test4

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.annotation.CheckResult
import arrow.core.*
import arrow.data.ReaderT
import arrow.data.ReaderTPartialOf
import arrow.data.StateT
import arrow.data.extensions.kleisli.monad.monad
import arrow.data.fix
import arrow.effects.ForIO
import arrow.effects.IO
import arrow.effects.MVar
import arrow.effects.extensions.io.applicativeError.handleError
import arrow.effects.extensions.io.async.async
import arrow.effects.extensions.io.monad.monad
import arrow.effects.extensions.io.monadDefer.binding
import arrow.effects.fix
import arrow.generic.coproduct2.Coproduct2
import arrow.generic.coproduct2.First
import arrow.generic.coproduct2.Second
import arrow.generic.coproduct2.fold
import arrow.generic.coproduct3.Coproduct3
import arrow.generic.coproduct3.fold
import arrow.optics.Lens
import indi.yume.tools.yroute.Logger
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import kotlin.random.Random


//<editor-fold desc="Result<T>">
sealed class Result<out T> {
    companion object {
        fun <T> success(t: T): Result<T> = Success(t)

        fun <T> fail(message: String, error: Throwable? = null): Result<T> = Fail(message, error)
    }
}
data class Success<T>(val t: T) : Result<T>()
data class Fail(val message: String, val error: Throwable? = null) : Result<Nothing>()

fun <T, R> Result<T>.map(mapper: (T) -> R): Result<R> = flatMap { Success(mapper(it)) }

inline fun <T, R> Result<T>.flatMap(f: (T) -> Result<R>): Result<R> = when(this) {
    is Success -> f(t)
    is Fail -> this
}

fun <T> Result<T>.toEither(): Either<Fail, T> = when (this) {
    is Fail -> left<Fail>()
    is Success -> t.right()
}
//</editor-fold>


//<editor-fold desc="YRoute">
typealias YRoute<S, T, R> = StateT<ReaderTPartialOf<ForIO, Tuple2<RouteCxt, T>>, S, Result<R>>



inline fun <S, T, R> route(crossinline f: (S) -> (Tuple2<RouteCxt, T>) -> IO<Tuple2<S, Result<R>>>): YRoute<S, T, R> =
    StateT(ReaderT.monad<ForIO, Tuple2<RouteCxt, T>>(IO.monad()))
    { state -> ReaderT { f(state)(it) } }

inline fun <S, T, R> routeF(crossinline f: (S, RouteCxt, T) -> IO<Tuple2<S, Result<R>>>): YRoute<S, T, R> =
    StateT(ReaderT.monad<ForIO, Tuple2<RouteCxt, T>>(IO.monad()))
    { state -> ReaderT { (cxt, t) -> f(state, cxt, t) } }

fun <S, T, R> YRoute<S, T, R>.runRoute(state: S, cxt: RouteCxt, t: T): IO<Tuple2<S, Result<R>>> =
    this.run(ReaderT.monad(IO.monad()), state).fix().run(cxt toT t).fix()

fun <S, T, R> YRoute<S, T, R>.toAction(param: T): EngineAction<S, T, R> =
    this@toAction toT param

fun <S, T> routeJustIO(io: IO<Unit>): YRoute<S, T, Unit> =
    routeF { state, _, _ -> io.map { state toT Success(Unit) } }

fun <S, T, R, R2> YRoute<S, T, R>.flatMapR(f: (R) -> YRoute<S, T, R2>): YRoute<S, T, R2> =
        routeF { state, cxt, param ->
            binding {
                val (newState1, result1) = !this@flatMapR.runRoute(state, cxt, param)

                when (result1) {
                    is Fail -> newState1 toT result1
                    is Success -> !f(result1.t).runRoute(newState1, cxt, param)
                }
            }
        }

fun <S, T, T1, R> YRoute<S, T, T1>.transform(f: (S, RouteCxt, T1) -> IO<Tuple2<S, Result<R>>>): YRoute<S, T, R> =
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

fun <S, T, R> transformRoute(f: (T) -> R): YRoute<S, T, R> =
    routeF { state, cxt, param -> IO.just(state toT Success(f(param))) }

fun <S1, S2, T, R> YRoute<S1, T, R>.mapState(lens: Lens<S2, S1>): YRoute<S2, T, R> =
    routeF { state2, cxt, param ->
        val state1 = lens.get(state2)
        binding {
            val (newState1, result) = !this@mapState.runRoute(state1, cxt, param)
            val newState2 = lens.set(state2, newState1)
            newState2 toT result
        }
    }

fun <S, T1, T2, R> YRoute<S, T1, R>.mapParam(type: TypeCheck<T2> = type(), f: (T2) -> T1): YRoute<S, T2, R> =
    routeF { state, cxt, param -> this@mapParam.runRoute(state, cxt, f(param)) }

fun <S, T, T1, R> YRoute<S, T, T1>.mapResult(f: (T1) -> R): YRoute<S, T, R> =
    transform { state, cxt, t1 -> IO.just(state toT Success(f(t1))) }

fun <S, T, T1, R> YRoute<S, T, T1>.composeWith(f: (S, RouteCxt, T, T1) -> IO<Tuple2<S, Result<R>>>): YRoute<S, T, R> =
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

fun <S, T1, T2, R1, R2> YRoute<S, T1, R1>.zipWithFunc(f: (T2) -> R2): YRoute<S, Tuple2<T1, T2>, Tuple2<R1, R2>> =
    routeF { state, cxt, (param1, param2) ->
        binding {
            val (newState, result1) = !this@zipWithFunc.runRoute(state, cxt, param1)
            newState toT result1.map { it toT f(param2) }
        }
    }

fun <S1, S2, T1, T2, R1, R2> zipRoute(route1: YRoute<S1, T1, R1>, route2: YRoute<S2, T2, R2>): YRoute<Tuple2<S1, S2>, Tuple2<T1, T2>, Tuple2<R1, R2>> =
    routeF { (state1, state2), cxt, (param1, param2) ->
        binding {
            val (newState1, result1) = !route1.runRoute(state1, cxt, param1)
            val (newState2, result2) = !route2.runRoute(state2, cxt, param2)
            (newState1 toT newState2) toT result1.flatMap { r1 -> result2.map { r2 -> r1 toT r2 } }
        }
    }

fun <S, T, R> YRoute<S, T, R>.packageParam(): YRoute<S, T, Tuple2<T, R>> =
    routeF { state, cxt, param ->
        this@packageParam.runRoute(state, cxt, param)
            .map { it.a toT it.b.map { r -> param toT r } }
    }

infix fun <S, T, T1, R> YRoute<S, T, T1>.compose(route2: YRoute<S, T1, R>): YRoute<S, T, R> =
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

fun <S, SS, T, R> YRoute<SS, T, R>.subRoute(type: TypeCheck<S>, mapper: (S) -> SS, demapper: (SS, S) -> S): YRoute<S, T, R> =
    routeF { state, cxt, param ->
        binding {
            val sstate = mapper(state)

            val (newSS, result) = !this@subRoute.runRoute(sstate, cxt, param)

            val newS = demapper(newSS, state)

            newS toT result
        }
    }

fun <S, SS, T, R> YRoute<S, T, YRoute<SS, T, R>>.flatten(type: TypeCheck<T>, mapper: (S) -> SS, demapper: (SS) -> S): YRoute<S, T, R> =
    routeF { state, cxt, param ->
        binding {
            val (innerS, innerYRouteResult) = !this@flatten.runRoute(state, cxt, param)

            val sstate = mapper(state)

            when (innerYRouteResult) {
                is Success -> {
                    val (newSS, innerResult) = !innerYRouteResult.t.runRoute(sstate, cxt, param)

                    val newS = demapper(newSS)
                    newS toT innerResult
                }
                is Fail -> innerS toT innerYRouteResult
            }
        }
    }

fun <S, T1, T2, R1, R2> stick(route1: YRoute<S, T1, R1>, route2: YRoute<S, T2, R2>): YRoute<S, Coproduct2<T1, T2>, Coproduct2<R1, R2>> =
    routeF { state, cxt, param ->
        param.fold(
            { t1 -> route1.runRoute(state, cxt, t1).map { it.map { result -> result.map { r1 -> First<R1, R2>(r1) } } } },
            { t2 -> route2.runRoute(state, cxt, t2).map { it.map { result -> result.map { r2 -> Second<R1, R2>(r2) } } } }
        )
    }

fun <S, T1, T2, R1, R2> YRoute<S, Coproduct2<T1, T2>, Coproduct2<R1, R2>>.runRoute21(state: S, cxt: RouteCxt, t1: T1): IO<Tuple2<S, Result<R1>>> =
    runRoute(state, cxt, First<T1, T2>(t1)).map { tuple -> tuple.map { result -> result.map { (it as First).a } } }

fun <S, T1, T2, R1, R2> YRoute<S, Coproduct2<T1, T2>, Coproduct2<R1, R2>>.runRoute22(state: S, cxt: RouteCxt, t2: T2): IO<Tuple2<S, Result<R2>>> =
    runRoute(state, cxt, Second<T1, T2>(t2)).map { tuple -> tuple.map { result -> result.map { (it as Second).b } } }


fun <S, T1, T2, T3, R1, R2, R3> stick(route1: YRoute<S, T1, R1>, route2: YRoute<S, T2, R2>, route3: YRoute<S, T3, R3>): YRoute<S, Coproduct3<T1, T2, T3>, Coproduct3<R1, R2, R3>> =
    routeF { state, cxt, param ->
        param.fold(
            { t1 -> route1.runRoute(state, cxt, t1).map { it.map { result -> result.map { r1 -> arrow.generic.coproduct3.First<R1, R2, R3>(r1) } } } },
            { t2 -> route2.runRoute(state, cxt, t2).map { it.map { result -> result.map { r2 -> arrow.generic.coproduct3.Second<R1, R2, R3>(r2) } } } },
            { t3 -> route3.runRoute(state, cxt, t3).map { it.map { result -> result.map { r3 -> arrow.generic.coproduct3.Third<R1, R2, R3>(r3) } } } }
        )
    }

fun <S, T1, T2, T3, R1, R2, R3> YRoute<S, Coproduct3<T1, T2, T3>, Coproduct3<R1, R2, R3>>.runRoute31(state: S, cxt: RouteCxt, t1: T1): IO<Tuple2<S, Result<R1>>> =
    runRoute(state, cxt, arrow.generic.coproduct3.First<T1, T2, T3>(t1))
        .map { tuple -> tuple.map { result -> result.map { (it as arrow.generic.coproduct3.First).a } } }

fun <S, T1, T2, T3, R1, R2, R3> YRoute<S, Coproduct3<T1, T2, T3>, Coproduct3<R1, R2, R3>>.runRoute32(state: S, cxt: RouteCxt, t2: T2): IO<Tuple2<S, Result<R2>>> =
    runRoute(state, cxt, arrow.generic.coproduct3.Second<T1, T2, T3>(t2))
        .map { tuple -> tuple.map { result -> result.map { (it as arrow.generic.coproduct3.Second).b } } }

fun <S, T1, T2, T3, R1, R2, R3> YRoute<S, Coproduct3<T1, T2, T3>, Coproduct3<R1, R2, R3>>.runRoute33(state: S, cxt: RouteCxt, t3: T3): IO<Tuple2<S, Result<R3>>> =
    runRoute(state, cxt, arrow.generic.coproduct3.Third<T1, T2, T3>(t3))
        .map { tuple -> tuple.map { result -> result.map { (it as arrow.generic.coproduct3.Third).c } } }

//</editor-fold>

//<editor-fold desc="CoreEngine">
class CoreEngine<S>(val state: MVar<ForIO, S>,
                    val routeCxt: RouteCxt) {
    private val streamSubject: Subject<Completable> = PublishSubject.create()

    @CheckResult
    fun runIO(io: IO<*>): IO<Unit> = IO { streamSubject.onNext(io.toSingle().ignoreElement()) }

    @CheckResult
    fun putStream(stream: Completable): IO<Unit> = IO { streamSubject.onNext(stream) }

    @CheckResult
    fun <T, R> runAsync(route: YRoute<S, T, R>, param: T, callback: (Result<R>) -> Unit): IO<Unit> =
        putStream(runActual(route, param).toCompletable { either ->
            val result = either.fold({ Fail("Has error.", it) }, { it })
            callback(result)
        })

    @CheckResult
    fun <T, R> run(route: YRoute<S, T, R>, param: T): IO<Result<R>> =
        IO.async { connection, cb ->
            val io = runAsync(route, param) { cb(it.right()) }
            val disposable = io.unsafeRunAsyncCancellable(cb = { if (it is Either.Left) cb(it) })
            connection.push(IO { disposable() })
        }

    private fun <T, R> runActual(route: YRoute<S, T, R>, param: T): IO<Result<R>> = binding {
        val code = Random.nextInt()
        Logger.d("CoreEngine", "================>>>>>>>>>>>> $code")
        Logger.d("CoreEngine", "start run: route=$route, param=$param")
        val oldState = !state.take()
        Logger.d("CoreEngine", "get oldState: $oldState")
        val (newState, result) = !route.runRoute(oldState, routeCxt, param)
            .handleError {
                // TODO force restore all state.
                oldState toT Fail("A very serious exception has occurred when run route, Status may become out of sync.", it)
            }
        Logger.d("CoreEngine", "return result: result=$result, newState=$newState")
        !state.put(newState)
        if (newState == oldState)
            Logger.d("CoreEngine", "put newState: state not changed")
        else
            Logger.d("CoreEngine", "put newState: newState=$newState")
        Logger.d("CoreEngine", "================<<<<<<<<<<<< $code")
        result
    }

    @CheckResult
    fun start(): Completable = streamSubject
        .concatMapCompletable { completable ->
            completable.doOnError { it.printStackTrace() }
                .onErrorComplete()
                .doFinally {
                    Logger.d("concatMapCompletable", "event over")
                }
        }

    companion object {
        fun <S> create(app: Application, initState: S): IO<CoreEngine<S>> = binding {
            val mvar = !MVar.uncancelableOf(initState, IO.async())
            val cxt = !RouteCxt.create(app)
            CoreEngine(mvar, cxt)
        }
    }
}

typealias EngineAction<S, T, R> = Tuple2<YRoute<S, T, R>, T>



fun <S, T, R> EngineAction<S, T, R>.startAsync(core: CoreEngine<S>, callback: (Result<R>) -> Unit): IO<Unit> =
    core.runAsync(a, b, callback)

fun <S, T, R> EngineAction<S, T, R>.start(core: CoreEngine<S>): IO<Result<R>> =
    core.run(a, b)

fun <S, R> YRoute<S, Unit, R>.startAsync(core: CoreEngine<S>, callback: (Result<R>) -> Unit): IO<Unit> =
    (this toT Unit).startAsync(core, callback)

fun <S, R> YRoute<S, Unit, R>.start(core: CoreEngine<S>): IO<Result<R>> = (this toT Unit).start(core)



fun <S, T, R> YRoute<S, T, R>.withParam(t: T): EngineAction<S, T, R> = this toT t

fun <S, T1, T2, R> YRoute<S, Tuple2<T1, T2>, R>.withParams(t1: T1, t2: T2): EngineAction<S, Tuple2<T1, T2>, R> =
        this toT (t1 toT t2)

fun <S, T1, T2, T3, R> YRoute<S, Tuple3<T1, T2, T3>, R>.withParams(t1: T1, t2: T2, t3: T3)
        : EngineAction<S, Tuple3<T1, T2, T3>, R> =
    this toT (Tuple3(t1, t2, t3))

fun <S, T1, T2, T3, T4, R> YRoute<S, Tuple4<T1, T2, T3, T4>, R>.withParams(t1: T1, t2: T2, t3: T3, t4: T4)
        : EngineAction<S, Tuple4<T1, T2, T3, T4>, R> =
    this toT (Tuple4(t1, t2, t3, t4))

fun <S, T1, T2, T3, T4, T5, R> YRoute<S, Tuple5<T1, T2, T3, T4, T5>, R>.withParams(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5)
        : EngineAction<S, Tuple5<T1, T2, T3, T4, T5>, R> =
    this toT (Tuple5(t1, t2, t3, t4, t5))
//</editor-fold>

//<editor-fold desc="RouteCxt">

class RouteCxt private constructor(val app: Application) {

    private val streamSubject: Subject<Completable> = PublishSubject.create()

    val globalActivityLife = object : ActivityLifecycleOwner {
        override val lifeSubject: Subject<ActivityLifeEvent> = ActivityLifecycleOwner.defaultLifeSubject()
    }

    val callback: Application.ActivityLifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityPaused(activity: Activity?) {
            if (activity != null)
                globalActivityLife.makeState(ActivityLifeEvent.OnPause(activity))
        }

        override fun onActivityResumed(activity: Activity?) {
            if (activity != null)
                globalActivityLife.makeState(ActivityLifeEvent.OnResume(activity))
        }

        override fun onActivityStarted(activity: Activity?) {
            if (activity != null)
                globalActivityLife.makeState(ActivityLifeEvent.OnStart(activity))
        }

        override fun onActivityDestroyed(activity: Activity?) {
            if (activity != null)
                globalActivityLife.makeState(ActivityLifeEvent.OnDestroy(activity))
        }

        override fun onActivitySaveInstanceState(activity: Activity?, outState: Bundle?) {
            if (activity != null)
                globalActivityLife.makeState(ActivityLifeEvent.OnSaveInstanceState(activity, outState))
        }

        override fun onActivityStopped(activity: Activity?) {
            if (activity != null)
                globalActivityLife.makeState(ActivityLifeEvent.OnStop(activity))
        }

        override fun onActivityCreated(activity: Activity?, savedInstanceState: Bundle?) {
            if (activity != null)
                globalActivityLife.makeState(ActivityLifeEvent.OnCreate(activity, savedInstanceState))
        }

    }

    init {
        app.registerActivityLifecycleCallbacks(callback)
    }

    internal fun start(): Completable =
        streamSubject.flatMapCompletable {
            it.doOnError { it.printStackTrace() }
                .onErrorComplete()
        }

    fun bindNextActivity(): Observable<Activity> = globalActivityLife.bindActivityLife()
        .ofType(ActivityLifeEvent.OnCreate::class.java)
        .map { it.activity }

    fun putStream(stream: Completable): IO<Unit> = IO { streamSubject.onNext(stream) }

    companion object {
        fun create(app: Application): IO<RouteCxt> = IO {
            val cxt = RouteCxt(app)
            cxt.start().subscribe()
            cxt
        }
    }
}
//</editor-fold>
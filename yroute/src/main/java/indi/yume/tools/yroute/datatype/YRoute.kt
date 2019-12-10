package indi.yume.tools.yroute.datatype

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.annotation.CheckResult
import arrow.core.*
import arrow.fx.IO
import arrow.fx.ForIO
import arrow.fx.MVar
import arrow.fx.extensions.fx
import arrow.fx.extensions.io.async.async
import arrow.higherkind
import arrow.optics.Lens
import indi.yume.tools.yroute.*
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import java.util.concurrent.TimeUnit
import kotlin.random.Random


//<editor-fold desc="YResult<T>">
@higherkind
sealed class YResult<out T> : YResultOf<T> {
    companion object {
        fun <T> success(t: T): YResult<T> = Success(t)

        fun <T> fail(message: String, error: Throwable? = null): YResult<T> = Fail(message, error)
    }
}
data class Success<T>(val t: T) : YResult<T>()
data class Fail(val message: String, val error: Throwable? = null) : YResult<Nothing>()

fun <T, R> YResult<T>.map(mapper: (T) -> R): YResult<R> = flatMap { Success(mapper(it)) }

inline fun <T, R> YResult<T>.flatMap(f: (T) -> YResult<R>): YResult<R> = when(this) {
    is Success -> f(t)
    is Fail -> this
}

fun <T> YResult<T>.toEither(): Either<Fail, T> = when (this) {
    is Fail -> left<Fail>()
    is Success -> t.right()
}
//</editor-fold>


//<editor-fold desc="YRoute">
@higherkind
data class YRoute<S, R>(val run: (S) -> (RouteCxt) -> IO<Tuple2<S, YResult<R>>>) : YRouteOf<S, R> {
    companion object
}


fun <S, R> route(f: (S) -> (RouteCxt) -> IO<Tuple2<S, YResult<R>>>): YRoute<S, R> =
        YRoute(f)

inline fun <S, R> routeF(crossinline f: (S, RouteCxt) -> IO<Tuple2<S, YResult<R>>>): YRoute<S, R> =
        YRoute { s -> { c -> f(s, c) } }

fun <S, R> YRoute<S, R>.runRoute(state: S, cxt: RouteCxt): IO<Tuple2<S, YResult<R>>> =
    this.run(state)(cxt)

fun <S> routeId(): YRoute<S, Unit> = routeF { s, c -> IO.just(s toT Success(Unit)) }

fun <S> routeGetState(): YRoute<S, S> = routeF { s, _ -> IO.just(s toT Success(s)) }

fun <S, R> routeFromState(f: (S) -> R): YRoute<S, R> = routeF { s, _ -> IO.just(s toT Success(f(s))) }

fun <S, R> routeFail(msg: String): YRoute<S, R> = routeF { s, _ -> IO.just(s toT Fail(msg)) }

fun <S, T> routeFromIO(io: IO<T>): YRoute<S, T> =
    routeF { state, _ -> io.map { state toT Success(it) } }

fun <S, R, R2> YRoute<S, R>.flatMapR(f: (R) -> YRoute<S, R2>): YRoute<S, R2> =
    routeF { state, cxt ->
        IO.fx {
            val (newState1, result1) = !this@flatMapR.runRoute(state, cxt)

            when (result1) {
                is Fail -> newState1 toT result1
                is Success -> !f(result1.t).runRoute(newState1, cxt)
            }
        }
    }

infix fun <S, R, R2> YRoute<S, R>.andThen(r2: YRoute<S, R2>): YRoute<S, R2> = flatMapR { r2 }

fun <S1, S2, R> YRoute<S1, Lens<S1, S2>>.composeState(route: YRoute<S2, R>): YRoute<S1, R> =
    routeF { state1, cxt ->
        IO.fx {
            val (innerState1, lensResult) = !this@composeState.runRoute(state1, cxt)

            when (lensResult) {
                is Fail -> innerState1 toT lensResult
                is Success -> {
                    val state2 = lensResult.t.get(innerState1)
                    val (newState2, result) = !route.runRoute(state2, cxt)
                    val newState1 = lensResult.t.set(innerState1, newState2)
                    newState1 toT result
                }
            }
        }
    }

fun <S : Any, R> YRoute<S, R>.stateNullable(tag: String = "stateNullable"): YRoute<S?, R> =
    routeF { state, cxt ->
        if (state == null)
            IO.just(state toT Fail("$tag | State is null, can not get state."))
        else
            this@stateNullable.runRoute(state, cxt)
    }

//fun <S1, T1, S2, R> YRoute<S2, R>.changeState(f: (R) -> Lens<S1, S2>): YRoute<S1, R> =
//    mapParam(type<Tuple2<T, T1>>()) { it.b }.transStateByParam { f(it.a) }
//
//fun <S1, S2, R> YRoute<S2, R>.transStateByParam(f: (R) -> Lens<S1, S2>): YRoute<S1, R> =
//    routeF { state1, cxt ->
//        binding {
//            val lens = f()
//            val state2 = lens.get(state1)
//            val (newState1, result) = !this@transStateByParam.runRoute(state2, cxt)
//
//            val newState2 = lens.set(state1, newState1)
//            newState2 toT result
//        }
//    }

//fun <S1, S2, R> YRoute<S2, Lens<S1, S2>>.apForState(): YRoute<S1, Lens<S1, S2>> =
//    routeF { state1, cxt ->
//        binding {
//            val (newState1, result) = !this@apForState.runRoute(state2, cxt, lens)
//
//
//            val state2 = lens.get(state1)
//            val (newState1, result) = !this@apForState.runRoute(state2, cxt, lens)
//            val newState2 = lens.set(state1, newState1)
//            newState2 toT result
//        }
//    }

infix fun <S, R1, R2> YRoute<S, R1>.compose(route: YRoute<S, R2>): YRoute<S, Tuple2<R1, R2>> =
        routeF { state, cxt ->
            IO.fx {
                val (tuple2) = this@compose.runRoute(state, cxt)
                val (innerState, resultT1) = tuple2

                when (resultT1) {
                    is Success -> {
                        val (newState, resultT2) = !route.runRoute(innerState, cxt)
                        newState toT resultT2.map { t2 -> resultT1.t toT t2 }
                    }
                    is Fail -> innerState toT resultT1
                }
            }
        }

fun <S, T1, R> YRoute<S, T1>.transform(f: (S, RouteCxt, T1) -> IO<Tuple2<S, YResult<R>>>): YRoute<S, R> =
    routeF { state, cxt ->
        IO.fx {
            val (tuple2) = this@transform.runRoute(state, cxt)
            val (newState, resultT1) = tuple2

            when (resultT1) {
                is Success -> !f(newState, cxt, resultT1.t)
                is Fail -> newState toT resultT1
            }
        }
    }

fun <S1, S2, R> YRoute<S1, R>.mapStateF(lensF: () -> Lens<S2, S1>): YRoute<S2, R> =
    routeF { state2, cxt ->
        val lens = lensF()
        val state1 = lens.get(state2)
        IO.fx {
            val (newState1, result) = !this@mapStateF.runRoute(state1, cxt)
            val newState2 = lens.set(state2, newState1)
            newState2 toT result
        }
    }

fun <S1, S2, R> YRoute<S1, R>.mapState(lens: Lens<S2, S1>): YRoute<S2, R> =
    routeF { state2, cxt ->
        val state1 = lens.get(state2)
        IO.fx {
            val (newState1, result) = !this@mapState.runRoute(state1, cxt)
            val newState2 = lens.set(state2, newState1)
            newState2 toT result
        }
    }

fun <S1 : Any, S2, R> YRoute<S1, R>.mapStateNullable(lens: Lens<S2, S1?>): YRoute<S2, R> =
    routeF { state2, cxt ->
        val state1 = lens.get(state2)
            ?: return@routeF IO.just(state2 toT Fail("mapStateNullable | Can not get target State, get target State result is null from lens."))
        IO.fx {
            val (newState1, result) = !this@mapStateNullable.runRoute(state1, cxt)
            val newState2 = lens.set(state2, newState1)
            newState2 toT result
        }
    }

fun <S, T, K : T> YRoute<S, T>.ofType(type: TypeCheck<K>): YRoute<S, K> =
        mapResult { it as K }

fun <S, T1, R> YRoute<S, T1>.mapResult(f: (T1) -> R): YRoute<S, R> =
    transform { state, cxt, t1 -> IO.just(state toT Success(f(t1))) }

fun <S, R : Any> YRoute<S, R?>.resultNonNull(tag: String = "resultNonNull()"): YRoute<S, R> =
    transform { state, cxt, r ->
        IO.just(state toT if (r != null) Success(r) else Fail("Tag $tag | YResult can not be Null."))
    }

fun <S, T1, R> YRoute<S, T1>.composeWith(f: (S, RouteCxt, T1) -> IO<Tuple2<S, YResult<R>>>): YRoute<S, R> =
    routeF { state, cxt ->
        IO.fx {
            val (tuple2) = this@composeWith.runRoute(state, cxt)
            val (newState, resultT1) = tuple2

            when (resultT1) {
                is Success -> !f(newState, cxt, resultT1.t)
                is Fail -> newState toT resultT1
            }
        }
    }

fun <S1, S2, R1, R2> zipRoute(route1: YRoute<S1, R1>, route2: YRoute<S2, R2>): YRoute<Tuple2<S1, S2>, Tuple2<R1, R2>> =
    routeF { (state1, state2), cxt ->
        IO.fx {
            val (newState1, result1) = !route1.runRoute(state1, cxt)
            val (newState2, result2) = !route2.runRoute(state2, cxt)
            (newState1 toT newState2) toT result1.flatMap { r1 -> result2.map { r2 -> r1 toT r2 } }
        }
    }

fun <S> YRoute<S, *>.ignoreResult(): YRoute<S, Unit> = mapResult { Unit }

fun <S, R1, R2> YRoute<S, Tuple2<R1, R2>>.ignoreLeft(): YRoute<S, R2> = mapResult { it.b }

fun <S, R1, R2> YRoute<S, Tuple2<R1, R2>>.ignoreRight(): YRoute<S, R1> = mapResult { it.a }

fun <S, R1, R2> YRoute<S, Tuple2<R1, R2>>.switchResult(): YRoute<S, Tuple2<R2, R1>> = mapResult { it.b toT it.a }

infix fun <S, R1, R2> YRoute<S, R1>.zipWith(route2: YRoute<S, R2>): YRoute<S, Tuple2<R1, R2>> =
    routeF { state, cxt ->
        IO.fx {
            val (newState, resultT1) = !this@zipWith.runRoute(state, cxt)

            when (resultT1) {
                is Success -> !route2.runRoute(newState, cxt)
                    .map { it.map { r -> r.map { t2 -> resultT1.t toT t2 } } }
                is Fail -> newState toT resultT1
            }
        }
    }

fun <S, SS, R> YRoute<S, YRoute<SS, R>>.flatten(lens: Lens<S, SS>): YRoute<S, R> =
    routeF { state, cxt ->
        IO.fx {
            val (innerS, innerYRouteResult) = !this@flatten.runRoute(state, cxt)

            val sstate = lens.get(innerS)

            when (innerYRouteResult) {
                is Success -> {
                    val (newSS, innerResult) = !innerYRouteResult.t.runRoute(sstate, cxt)

                    val newS = lens.set(innerS, newSS)
                    newS toT innerResult
                }
                is Fail -> innerS toT innerYRouteResult
            }
        }
    }
//</editor-fold>

//<editor-fold desc="CoreEngine">

interface CoreEngine<S> {
    val routeCxt: RouteCxt

    @CheckResult
    fun runIO(io: IO<*>): IO<Unit>

    @CheckResult
    fun putStream(stream: Completable): IO<Unit>

    @CheckResult
    fun <R> runAsync(route: YRoute<S, R>, callback: (YResult<R>) -> Unit): IO<Unit>

    @CheckResult
    fun <R> run(route: YRoute<S, R>): IO<YResult<R>>
}

typealias CoreContainer = Map<String, *>

typealias ContainerEngine = MainCoreEngine<CoreContainer>

fun <S> ContainerEngine.createBranch(initState: S): SubCoreEngine<S> {
    val key = CoreID.get().toString()
    @Suppress("UNCHECKED_CAST")
    val lens: Lens<CoreContainer, S> = Lens(
        get = { container -> (container[key] as? S) ?: initState },
        set = { container, subState -> container + (key to subState) }
    )
    return subCore(lens)
}

class MainCoreEngine<S>(val state: MVar<ForIO, S>,
                        override val routeCxt: RouteCxt
): CoreEngine<S> {
    val streamSubject: Subject<Completable> = PublishSubject.create()

    @CheckResult
    override fun runIO(io: IO<*>): IO<Unit> = IO { streamSubject.onNext(io.toSingle().ignoreElement()) }

    @CheckResult
    override fun putStream(stream: Completable): IO<Unit> = IO { streamSubject.onNext(stream) }

    @CheckResult
    override fun <R> runAsync(route: YRoute<S, R>, callback: (YResult<R>) -> Unit): IO<Unit> =
        putStream(runActual(route).toCompletable { either ->
            val result = either.fold({ Fail("Has error.", it) }, { it })
            callback(result)
        })

    @CheckResult
    override fun <R> run(route: YRoute<S, R>): IO<YResult<R>> =
        IO.cancelable { cb ->
            val io = runAsync(route) { cb(it.right()) }
            val disposable = io.unsafeRunAsyncCancellable(cb = { if (it is Either.Left) cb(it) })
            IO { disposable() }
        }

    private fun <R> runActual(route: YRoute<S, R>): IO<YResult<R>> = IO.fx {
        val code = Random.nextInt()
        Logger.d("CoreEngine", "================>>>>>>>>>>>> $code")
        Logger.d("CoreEngine", "start run: route=$route")
        val oldState = !state.take()
        Logger.d("CoreEngine", "get oldState: $oldState")
        val (newState, result) = !route.runRoute(oldState, routeCxt)
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

    fun <S2> subCore(lens: Lens<S, S2>): SubCoreEngine<S2> {
        val subDelegate = object : CoreEngine<S2> {
            override val routeCxt: RouteCxt = this@MainCoreEngine.routeCxt

            override fun runIO(io: IO<*>): IO<Unit> = this@MainCoreEngine.runIO(io)

            override fun putStream(stream: Completable): IO<Unit> =
                this@MainCoreEngine.putStream(stream)

            override fun <R> runAsync(route: YRoute<S2, R>, callback: (YResult<R>) -> Unit): IO<Unit> =
                this@MainCoreEngine.runAsync(route.mapState(lens), callback)

            override fun <R> run(route: YRoute<S2, R>): IO<YResult<R>> =
                this@MainCoreEngine.run(route.mapState(lens))
        }

        return SubCoreEngine(subDelegate)
    }

    @CheckResult
    fun start(): Completable = streamSubject
        .concatMapCompletable { completable ->
            val timeout = YRouteConfig.taskRunnerTimeout
            if (timeout == null) completable
            else completable.timeout(timeout, TimeUnit.MILLISECONDS)
                .doOnError { it.printStackTrace() }
                .onErrorComplete()
                .doFinally {
                    Logger.d("concatMapCompletable", "event over")
                }
        }

    companion object {
        fun <S> create(app: Application, initState: S): IO<MainCoreEngine<S>> = IO.fx {
            val mvar = !MVar(initState)
            val cxt = !RouteCxt.create(app)
            MainCoreEngine(mvar, cxt)
        }
    }
}

class SubCoreEngine<S>(delegate: CoreEngine<S>): CoreEngine<S> by delegate {
    fun <S2> subCore(lens: Lens<S, S2>): SubCoreEngine<S2> {
        val subDelegate = object : CoreEngine<S2> {
            override val routeCxt: RouteCxt = this@SubCoreEngine.routeCxt

            override fun runIO(io: IO<*>): IO<Unit> = this@SubCoreEngine.runIO(io)

            override fun putStream(stream: Completable): IO<Unit> =
                this@SubCoreEngine.putStream(stream)

            override fun <R> runAsync(route: YRoute<S2, R>, callback: (YResult<R>) -> Unit): IO<Unit> =
                this@SubCoreEngine.runAsync(route.mapState(lens), callback)

            override fun <R> run(route: YRoute<S2, R>): IO<YResult<R>> =
                this@SubCoreEngine.run(route.mapState(lens))
        }

        return SubCoreEngine(subDelegate)
    }
}


fun <S, R> YRoute<S, R>.startAsync(core: CoreEngine<S>, callback: (YResult<R>) -> Unit): IO<Unit> =
    core.runAsync(this, callback)

fun <S, R> YRoute<S, R>.start(core: CoreEngine<S>): IO<YResult<R>> = core.run(this)

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

        override fun onActivitySaveInstanceState(activity: Activity?, outState: Bundle) {
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
            cxt.start().catchSubscribe()
            cxt
        }
    }
}
//</editor-fold>
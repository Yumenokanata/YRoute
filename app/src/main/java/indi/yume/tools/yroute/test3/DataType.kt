package indi.yume.tools.yroute.test3

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import arrow.core.*
import arrow.data.StateT
import arrow.core.Function1
import arrow.core.extensions.function1.monad.flatten
import arrow.data.ReaderT
import arrow.data.ReaderTPartialOf
import arrow.data.extensions.kleisli.applicative.applicative
import arrow.data.extensions.kleisli.monad.monad
import arrow.data.extensions.statet.monad.flatMap
import arrow.data.extensions.statet.monad.monad
import arrow.data.run
import arrow.effects.ForIO
import arrow.effects.IO
import arrow.effects.extensions.io.applicative.applicative
import arrow.effects.extensions.io.functor.functor
import arrow.effects.extensions.io.monad.binding
import arrow.effects.extensions.io.monad.monad
import arrow.effects.fix
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import java.util.concurrent.Future

/**
class RouteCxt(val app: Application) {
    val onStartSubject: Subject<Activity> = PublishSubject.create<Activity>().toSerialized()

    val callback: Application.ActivityLifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityPaused(activity: Activity?) {

        }

        override fun onActivityResumed(activity: Activity?) {

        }

        override fun onActivityStarted(activity: Activity?) {
            if (activity != null)
                onStartSubject.onNext(activity)
        }

        override fun onActivityDestroyed(activity: Activity?) {

        }

        override fun onActivitySaveInstanceState(activity: Activity?, outState: Bundle?) {

        }

        override fun onActivityStopped(activity: Activity?) {

        }

        override fun onActivityCreated(activity: Activity?, savedInstanceState: Bundle?) {

        }

    }

    fun bindNextActivity(): Observable<Activity> = onStartSubject

    //    val store: Store
    init {
        app.registerActivityLifecycleCallbacks(callback)
    }
}


sealed class Result<out T> {
    companion object {
        fun <T> success(t: T): Result<T> = Success(t)

        fun <T> fail(message: String, error: Throwable?): Result<T> = Fail(message, error)
    }
}
class Success<T>(val t: T) : Result<T>()
class Fail(val message: String, val error: Throwable?) : Result<Nothing>()

fun <T, R> Result<T>.map(mapper: (T) -> R): Result<R> = flatMap { Success(mapper(it)) }

fun <T, R> Result<T>.flatMap(f: (T) -> Result<R>): Result<R> = when(this) {
    is Success -> f(t)
    is Fail -> this
}


typealias RouteAction<R> = ReaderT<ForIO, RouteCxt, R>

typealias Jumper<T, R> = (T) -> RouteAction<Result<R>>

typealias YRoute<VD, T> = StateT<ReaderTPartialOf<ForIO, RouteCxt>, VD, T>


fun <T, R> jumper(f: Jumper<T, R>): Jumper<T, R> = f

fun <R> action(f: (RouteCxt) -> IO<R>): RouteAction<R> = ReaderT { f(it) }

fun <R> actionIO(f: (RouteCxt) -> R): RouteAction<R> = ReaderT { IO { f(it) } }


fun <T, R> run1(f: CxtRunner<T>.() -> R): Function1<T, R> = { t: T ->
    CxtRunner(t).f()
}.k()

fun <T, R> run(f: CxtRunner<T>.() -> R): (T) -> R = {
    CxtRunner(it).f()
}

class CxtRunner<T>(val cxt: T) {
    fun <R> ((T) -> R).bind(): R = this(cxt)

    fun <R> Function1<T, R>.bind(): R = this(cxt)
}

fun <VD, T, R> route(jump: Jumper<Tuple2<VD, T>, Tuple2<VD, R>>): YRoute<VD, Jumper<T, R>> = StateT<ReaderTPartialOf<ForIO, RouteCxt>, VD, Jumper<T, R>>(ReaderT.applicative(IO.applicative()))
{ state -> action { cxt -> IO.just(state toT { param: T -> action { cxt ->
    val resultIO: IO<Result<Tuple2<VD, R>>> = jump(state toT param).run(cxt).fix()
    when(result) {
        is Success ->
    }
    IO { Result.success(builder.createIntent(cxt)) }
} }) } }


data class ActivitiesState(val list: List<ActivityItem>)

class ActivityItem(val activity: Activity, val tag: Any? = null)

fun <VD> createActivityIntent(): YRoute<VD, Jumper<ActivityBuilder<Activity>, Intent>> = StateT(ReaderT.applicative<ForIO, RouteCxt>(IO.applicative()))
{ state -> action { IO.just(state toT { builder: ActivityBuilder<Activity> -> action { cxt ->
    IO { Result.success(builder.createIntent(cxt)) }
} }) } }


class ActivityBuilder<A>(val clazz: Class<A>) {
    internal var createIntent: RouteCxt.() -> Intent = {
        Intent(app, clazz)
    }

    fun withParam(data: Bundle): ActivityBuilder<A> {
        createIntent = createIntent andThen { it.putExtras(data) }
        return this
    }

    fun withIntent(f: (Intent) -> Unit): ActivityBuilder<A> {
        createIntent = createIntent andThen { f(it); it }
        return this
    }
}

fun <T> Single<T>.toIO(): IO<T> = IO { blockingGet() }

val startActivity: YRoute<ActivitiesState, Jumper<ActivityBuilder<Activity>, Intent>> =
    createActivityIntent<ActivitiesState>().flatMap(ReaderT.monad(IO.monad())) { jump ->
        action { cxt -> IO {
            jumper<ActivityBuilder<Activity>, Intent> { builder ->
                val action = jump(builder)
                action.andThen(IO.monad()) { result ->
                    when(result) {
                        is Success -> IO { result }
                        is Fail -> IO { result }
                    }
                }
            }
        }
        }
    }



//    createActivityIntent<ActivitiesState>().transform(IO.functor()) { (state, jump) ->
//        state toT jumper<ActivityBuilder<Activity>, Intent> { builder ->
//            run1<RouteCxt, IO<Result<Intent>>> {
//                val top: Context = state.list.lastOrNull()?.activity ?: cxt.app
//
//                binding {
//                    val (intentResult) = jump(builder).bind()
//                    intentResult.map {
//                        top.startActivity(it)
//                        it
//                    }
//                }
//            }
//        }
//    }.flatMap(IO.monad()) { jump ->
//        StateT<ForIO, ActivitiesState, Jumper<ActivityBuilder<Activity>, Intent>>(IO.applicative()) { state ->
//            jumper<ActivityBuilder<Activity>, Intent> {
//                jump(it).flatMap {
//
//                }
//            }
//            run1<RouteCxt, IO<Result<Intent>>> {
//
//            }
//        }
//    }


//        .map(IO.monad()) { create ->
//            jumper<ActivityBuilder<Activity>, Intent> { builder ->
//                route { cxt ->
//                    create(builder).map { front: IO<Result<Intent>> ->
//                        front.map {
//                            it.map { intent ->
//                                cxt.app.startActivity(intent)
//                                intent
//                            }
//                        }
//                    }
//                }.flatten()
//            }
//        }

interface ForResult {

}



fun <T> startActivityForResult(): YRoute<ActivitiesState, Jumper<ActivityBuilder<T>, Intent>> where T: Activity, T: ForResult = binding {
    createActivityIntent<ActivitiesState>().transform(IO.functor()) { (state, jump) ->
        state to jumper<> {

        }
    }
}

    run1<RouteCxt> {
    val jumper = createActivityIntent<ActivitiesState>().bind()

    jumper.map(IO.monad()) { create ->
            jumper<ActivityBuilder, Intent> { builder ->
                route {
                    create(builder).map { front: IO<Result<Intent>> ->
                        front.map {
                            it.map { intent ->
                                app.sta(intent)
                                intent
                            }
                        }
                    }
                }.flatten()
            }
        }
}

**/
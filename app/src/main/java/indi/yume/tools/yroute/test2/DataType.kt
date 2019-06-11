package indi.yume.tools.yroute.test2

import android.app.Activity
import android.app.Application
import android.content.Intent
import arrow.core.ForId
import arrow.data.StateT
import arrow.effects.ForIO
import arrow.effects.IO
import arrow.effects.MVar
import arrow.effects.extensions.io.monadDefer.binding
import arrow.generic.coproduct10.Coproduct10
import indi.yume.tools.yroute.HListK

/**
class RouteCxt<S>(val app: Application) {
//    val store: Store
}

typealias Store<S> = MVar<ForIO, S>

typealias RouteAction<S, R> = Store<S>.() -> R

sealed class Result<out T>
class Success<T>(val t: T) : Result<T>()
class Fail(val message: String, val error: Throwable?) : Result<Nothing>()

fun <T, R> Result<T>.map(mapper: (T) -> R): Result<R> = flatMap { Success(mapper(it)) }

fun <T, R> Result<T>.flatMap(f: (T) -> Result<R>): Result<R> = when(this) {
    is Success -> f(t)
    is Fail -> this
}


typealias Jumper<T, R> = (T) -> IO<Result<R>>

typealias Sto<VD, T> = StateT<ForIO, VD, T>

interface YRoute<VD, T, R> : YRouteOf<VD, T, R> {
//    fun jump(target: T): RouteAction<VD, IO<Result<R>>>
}

class ForYRoute private constructor() { companion object }
typealias YRouteOf<A, B, C> = arrow.Kind<ForYRoute, Triple<A, B, C>>
@Suppress("UNCHECKED_CAST", "NOTHING_TO_INLINE")
inline fun <A, B, C> YRouteOf<A, B, C>.fix(): YRoute<A, B, C> =
    this as YRoute<A, B, C>


class ActivityYRoute<L : HListK<ForYRoute, L>>(val subs: L) : YRoute<ActivityStackState, ActivityStackStarter<L>, Unit> {
    fun jump(target: ActivityStackStarter<L>): RouteAction<ActivityStackState, IO<Result<Unit>>> =
        target.jump()
}

class ActivityStackState(val list: List<ActivityItem>)

class ActivityItem(val activity: Activity, val tag: Any?, val state: Any)

class ActivityStackStarter<L : HListK<ForYRoute, L>> private constructor(
    val getter: (L) -> Any,
    val runner: Any.() -> RouteAction<Any, IO<Result<Any>>>
) {
    fun run(ayr: ActivityYRoute<L>): RouteAction<ActivityStackState, IO<Result<Unit>>> = store@{ binding {
        val vdl = this@store.take()

        val yRoute = getter(ayr.subs)
        yRoute.runner()

        Success(Unit)
    } }

    companion object {
        fun <L : HListK<ForYRoute, L>, S : Any, T, R : Any, YR : YRoute<S, T, R>> create(
            getter: (L) -> YR,
            runner: YR.() -> RouteAction<S, IO<Result<R>>>
        ): ActivityStackStarter<L> =
            ActivityStackStarter<L>(getter) {
                val yRoute = this as YR
                {
                    val store = this as Store<S>
                    runner(yRoute).invoke(store).map { it.map { Unit } }
                }
            }
    }
}


class RouteEngine<>

{
    activityStact {
        + activityYRoute

        + FragmentStack {
            stack = Table("1" to DefaultFragment::class)
            subs = {
                + "1" to FragmentStack {
                    stack = Single1
                    subs = {
                        + ViewStack
                    }
                }
            }
        }

        +
    }
}

**/
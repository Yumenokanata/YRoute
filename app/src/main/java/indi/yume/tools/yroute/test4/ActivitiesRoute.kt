package indi.yume.tools.yroute.test4

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import arrow.core.Either
import arrow.core.Tuple2
import arrow.core.andThen
import arrow.core.toT
import arrow.data.extensions.list.foldable.find
import arrow.effects.IO
import arrow.effects.extensions.io.monadDefer.binding
import indi.yume.tools.yroute.Logger
import io.reactivex.Completable
import io.reactivex.Single
import kotlin.random.Random

data class ActivitiesState(val list: List<ActivityItem>)

interface ActivityItem {
    val activity: Activity
    val hashTag: Int

    fun changeActivity(act: Activity): ActivityItem
}

data class ActivityData(override val activity: Activity,
                        override val hashTag: Int,
                        val extra: Any? = null) : ActivityItem {
    override fun changeActivity(act: Activity): ActivityItem = copy(activity = act)
}

class RxActivityBuilder private constructor(clazz: Class<out Activity>) : ActivityBuilder<Activity>(clazz) {
    companion object {
        fun <T> create(clazz: Class<T>): RxActivityBuilder where T : Activity, T : ActivityLifecycleOwner =
            RxActivityBuilder(clazz)
    }
}

open class ActivityBuilder<out A>(val clazz: Class<out A>) {
    internal var createIntent: RouteCxt.() -> Intent = {
        Intent(app, clazz)
    }

    fun withBundle(data: Bundle): ActivityBuilder<A> {
        createIntent = createIntent andThen { it.putExtras(data) }
        return this
    }

    fun withIntent(f: (Intent) -> Unit): ActivityBuilder<A> {
        createIntent = createIntent andThen { f(it); it }
        return this
    }
}

object ActivitiesRoute {
    //<editor-fold defaultstate="collapsed" desc="Routes">
    fun <T : Activity, VD> createActivityIntent(): YRoute<VD, ActivityBuilder<T>, Intent> = routeF { vd, cxt, param ->
        IO { vd toT Result.success(param.createIntent(cxt)) }
    }

    val startActivity: YRoute<ActivitiesState, Intent, Activity> =
        routeF { vd, cxt, param ->
            Logger.d("startActivity", "start startActivity action")
            binding {
                val top: Context = vd.list.lastOrNull()?.activity ?: cxt.app

                Logger.d("startActivity", "startActivity: intent=$param")
                !IO { top.startActivity(param) }

                Logger.d("startActivity", "wait activity.")
                val (act) = cxt.bindNextActivity()
                    .firstOrError().toIO()
                Logger.d("startActivity", "get activity: $act")

                vd.copy(list = vd.list + ActivityData(act, act.hashCode())) toT
                        (if (cxt.checkComponentClass(param, act)) Result.success(act)
                        else Fail(
                            "startActivity | start activity is Success, but can not get target activity: " +
                                    "target is ${param.component?.className} but get is $act", null))
            }
        }

    val startActivityForResult: YRoute<ActivitiesState, Tuple2<Intent, Int>, Activity> =
        routeF { vd, cxt, (param, requestCode) ->
            binding {
                val top = vd.list.lastOrNull()?.activity

                if (top != null) {
                    !IO { top.startActivityForResult(param, requestCode) }

                    val (act) = cxt.bindNextActivity()
                        .firstOrError().toIO()

                    vd.copy(list = vd.list + ActivityData(act, act.hashCode())) toT
                            (if (cxt.checkComponentClass(param, act)) Result.success(act)
                            else Fail(
                                "startActivity | start activity is Success, but can not get target activity: " +
                                        "target is ${param.component?.className} but get is $act", null))
                } else vd toT Fail("startActivityForRx has failed: have no top activity.", null)
            }
        }

    val startActivityForRx: YRoute<ActivitiesState, RxActivityBuilder, Single<Tuple2<Int, Bundle?>>> =
        routeF { vd, cxt, builder ->
            binding {
                val intent = builder.createIntent(cxt)
                val top = vd.list.lastOrNull()?.activity

                if (top != null) {
                    val requestCode = Random.nextInt() and 0x0000ffff

                    !IO { top.startActivityForResult(intent, requestCode) }

                    val (activity) = cxt.bindNextActivity()
                        .firstOrError().toIO()

                    val newState = vd.copy(list = vd.list + ActivityData(activity, activity.hashCode()))

                    newState toT if (activity is ActivityLifecycleOwner && builder.clazz.isInstance(activity)) {
                        Success(activity.bindActivityLife().ofType(ActivityLifeEvent.OnActivityResult::class.java)
                            .filter { it.requestCode == requestCode }
                            .firstOrError()
                            .map { it.resultCode toT it.data?.extras })
                    } else Fail("startActivityForRx | start activity is Success, but can not get target activity: " +
                            "target is ${builder.clazz.simpleName} but get is $activity", null)
                } else vd toT Fail("startActivityForRx has failed: have no top activity.", null)
            }
        }

    val backActivity: YRoute<ActivitiesState, Unit, Unit> =
        routeF { vd, cxt, _ ->
            binding {
                val top = vd.list.lastOrNull()?.activity

                if (top != null) {
                    !IO { top.finish() }

                    val newState = vd.copy(list = vd.list.dropLast(1))

                    newState toT Success(Unit)
                } else vd toT Fail("backActivity has failed: have no top activity.")
            }
        }

    val finishTargetActivity: YRoute<ActivitiesState, ActivityItem, Unit> =
        routeF { vd, cxt, activityItem ->
            binding {
                val targetItem = vd.list.find { it.hashTag == activityItem.hashTag }.orNull()

                if (targetItem != null) {
                    !IO { targetItem.activity.finish() }

                    val newState = vd.copy(list = vd.list.filter { it.hashTag != activityItem.hashTag })

                    newState toT Success(Unit)
                } else vd toT Fail("finishTargetActivity | failed, target item not find: " +
                        "target=$activityItem but stack has ${vd.list.joinToString()}")
            }
        }

    val findTargetActivityItem: YRoute<ActivitiesState, Activity, ActivityItem?> =
        routeF { vd, cxt, activity ->
            val item = vd.list.firstOrNull { it.activity === activity }
            IO.just(vd toT Success(item))
        }
    //</editor-fold>

    val routeStartActivityByIntent: YRoute<ActivitiesState, Intent, Activity> =
        startActivity

    val routeStartActivity: YRoute<ActivitiesState, ActivityBuilder<Activity>, Activity> =
        createActivityIntent<Activity, ActivitiesState>().compose(startActivity)

    val routeStartActivityForResult: YRoute<ActivitiesState, Tuple2<ActivityBuilder<Activity>, Int>, Activity> =
        createActivityIntent<Activity, ActivitiesState>()
            .zipWithFunc { requestCode: Int -> requestCode }
            .compose(startActivityForResult)

    val routeStartActivityForRx: YRoute<ActivitiesState, RxActivityBuilder, Single<Tuple2<Int, Bundle?>>> =
            startActivityForRx

    val routeFinishTop: YRoute<ActivitiesState, Unit, Unit> = backActivity

    val routeFinish: YRoute<ActivitiesState, Activity, Unit> =
        findTargetActivityItem
            .composeWith { state, cxt, activity, item ->
                binding {
                    if (item == null) {
                        !IO { activity.finish() }
                        state toT Success(Unit)
                    } else {
                        !finishTargetActivity.runRoute(state, cxt, item)
                    }
                }
            }
}


fun CoreEngine<ActivitiesState>.bindApp(): Completable =
    routeCxt.globalActivityLife.bindActivityLife()
        .map { run(globalActivityLogic, it).unsafeRunAsync { either ->
            if (either is Either.Left) either.a.printStackTrace()
        } }
        .ignoreElements()

private const val SAVE_STORE_HASH_TAG = "manager__hash_tag"

val globalActivityLogic: YRoute<ActivitiesState, ActivityLifeEvent, Unit> =
    routeF { state, cxt, event ->
        Logger.d("globalActivityLogic", "start deal event: $event")
        when(event) {
            is ActivityLifeEvent.OnCreate -> {
                val newState = if (state.list.all { it.activity !== event.activity }) {
                    state.copy(list = state.list + ActivityData(
                        event.activity,
                        event.activity.hashCode(),
                        "this is globalActivityLogic auto generate ActivityData item."
                    ))
                } else {
                    val savedHashTag = event.savedInstanceState?.getString(SAVE_STORE_HASH_TAG)?.toIntOrNull()
                    if (savedHashTag != null) {
                        state.copy(list = state.list.map {
                            if (it.hashTag == savedHashTag)
                                it.changeActivity(event.activity)
                            else it
                        })
                    } else state
                }

                IO.just(newState)
            }
            is ActivityLifeEvent.OnSaveInstanceState -> binding {
                val item = state.list.firstOrNull { it.activity === event.activity }
                if (item != null)
                    !IO { event.outState?.apply { putString(SAVE_STORE_HASH_TAG, item.hashTag.toString()) } }

                state
            }
            is ActivityLifeEvent.OnDestroy -> {
                Logger.d("globalActivityLogic OnDestroy", "start find: ${event.activity.hashCode()}")
                val item = state.list.firstOrNull { it.activity === event.activity }
                Logger.d("globalActivityLogic OnDestroy", "find result: ${item}")

                if (item != null)
                    IO.just(state.copy(list = state.list.filter { it.activity !== event.activity }))
                else
                    IO.just(state)
            }
            else -> IO.just(state)
        }.map { it toT Success(Unit) }
    }
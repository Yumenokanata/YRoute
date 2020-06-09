package indi.yume.tools.yroute

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import arrow.core.Tuple2
import arrow.core.andThen
import arrow.core.toT
import indi.yume.tools.yroute.YRouteConfig.globalDefaultAnimData
import indi.yume.tools.yroute.datatype.*
import io.reactivex.Completable
import io.reactivex.Single
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.await
import kotlin.random.Random

data class ActivitiesState(val list: List<ActivityData>)

data class ActivityData(val activity: Activity,
                        val hashTag: Long,
                        val extra: Map<String, Any> = emptyMap(),
                        val animData: AnimData?)

class ActivityBuilder<out A> {
    val clazz: Class<out A>

    internal var createIntent: RouteCxt.() -> Intent

    var animData: AnimData? = globalDefaultAnimData

    constructor(clazz: Class<out A>) {
        this.clazz = clazz
        createIntent = { Intent(app, clazz) }
    }

    constructor(intent: Intent) {
        this.clazz = Class.forName(intent.component?.className!!) as Class<out A>
        createIntent = { Intent(app, clazz) }
    }

    fun withBundle(data: Bundle): ActivityBuilder<A> {
        createIntent = createIntent andThen { it.putExtras(data) }
        return this
    }

    fun withIntent(f: (Intent) -> Unit): ActivityBuilder<A> {
        createIntent = createIntent andThen { f(it); it }
        return this
    }

    fun withAnimData(animData: AnimData?): ActivityBuilder<A> {
        this.animData = animData
        return this
    }

    fun animDataOrDefault(animData: AnimData?): ActivityBuilder<A> {
        if (this.animData == null)
            this.animData = animData
        return this
    }
}

object ActivitiesRoute {
    //<editor-fold defaultstate="collapsed" desc="Routes">
    fun <T : Activity, VD> createActivityIntent(builder: ActivityBuilder<T>): YRoute<VD, Intent> =
        routeF { vd, cxt ->
            vd toT YResult.success(builder.createIntent(cxt))
        }

    fun startActivity(intent: Intent, animData: AnimData? = globalDefaultAnimData): YRoute<ActivitiesState, Activity> =
        routeF { vd, cxt ->
            Logger.d("startActivity", "start startActivity action")

            val top: Context = vd.list.lastOrNull()?.activity ?: cxt.app

            Logger.d("startActivity", "startActivity: intent=$intent")
            top.startActivity(intent)
            if (animData != null && top is Activity)
                top.overridePendingTransition(animData.enterAnim, animData.enterStayAnimForActivity)
            else (top as? Activity)?.overridePendingTransition(0, 0)

            Logger.d("startActivity", "wait activity.")
            val act = cxt.bindNextActivity()
                .firstOrError().await()
            Logger.d("startActivity", "get activity: $act")

            vd.copy(list = vd.list + ActivityData(act, CoreID.get(), animData = animData)) toT
                    (if (cxt.checkComponentClass(intent, act)) YResult.success(act)
                    else Fail(
                        "startActivity | start activity is Success, but can not get target activity: " +
                                "target is ${intent.component?.className} but get is $act", null
                    ))
        }

    fun startActivityForResult(intent: Intent, requestCode: Int, animData: AnimData? = globalDefaultAnimData): YRoute<ActivitiesState, Activity> =
        routeF { vd, cxt ->
            val top = vd.list.lastOrNull()?.activity

            if (top != null) {
                top.startActivityForResult(intent, requestCode)
                if (animData != null)
                    top.overridePendingTransition(animData.enterAnim, animData.enterStayAnimForActivity)
                else top.overridePendingTransition(0, 0)

                val act = cxt.bindNextActivity()
                    .firstOrError().await()

                vd.copy(list = vd.list + ActivityData(act, CoreID.get(), animData = animData)) toT
                        (if (cxt.checkComponentClass(intent, act)) YResult.success(act)
                        else Fail(
                            "startActivity | start activity is Success, but can not get target activity: " +
                                    "target is ${intent.component?.className} but get is $act", null
                        ))
            } else vd toT Fail("startActivityForRx has failed: have no top activity.", null)
        }

    fun <A> startActivityForRx(builder: ActivityBuilder<A>): YRoute<ActivitiesState, Tuple2<A, Single<Tuple2<Int, Bundle?>>>> =
        routeF { vd, cxt ->
            val intent = builder.createIntent(cxt)
            val top = vd.list.lastOrNull()?.activity

            if (top != null) {
                val requestCode = Random.nextInt() and 0x0000ffff
                val animData = builder.animData

                top.startActivityForResult(intent, requestCode)
                if (animData != null)
                    top.overridePendingTransition(animData.enterAnim, animData.enterStayAnimForActivity)
                else top.overridePendingTransition(0, 0)

                val activity = cxt.bindNextActivity()
                    .firstOrError().await()

                val newState = vd.copy(list = vd.list + ActivityData(activity, CoreID.get(), animData = animData))

                newState toT if (activity is ActivityLifecycleOwner && builder.clazz.isInstance(activity)) {
                    Success((activity as A) toT activity.bindActivityLife().ofType(ActivityLifeEvent.OnActivityResult::class.java)
                        .filter { it.requestCode == requestCode }
                        .firstOrError()
                        .map { it.resultCode toT it.data?.extras })
                } else Fail(
                    "startActivityForRx | start activity is Success, but can not get target activity: " +
                            "target is ${builder.clazz.simpleName} but get is $activity", null
                )
            } else vd toT Fail("startActivityForRx has failed: have no top activity.", null)
        }

    val backActivity: YRoute<ActivitiesState, Unit> =
        routeF { vd, cxt ->
            val top = vd.list.lastOrNull()

            if (top != null) {
                top.activity.finish()
                if (top.animData != null)
                    top.activity.overridePendingTransition(0, top.animData.exitAnim)
                else top.activity.overridePendingTransition(0, 0)

                val newState = vd.copy(list = vd.list.dropLast(1))

                newState toT Success(Unit)
            } else vd toT Fail("backActivity has failed: have no top activity.")
        }

    fun finishTargetActivity(targetData: ActivityData): YRoute<ActivitiesState, Unit> =
        routeF { vd, cxt ->
            val targetItem = vd.list.firstOrNull { it.hashTag == targetData.hashTag }

            if (targetItem != null) {
                targetItem.activity.finish()
                if (targetItem.animData != null)
                    targetItem.activity.overridePendingTransition(0, targetItem.animData.exitAnim)
                else targetItem.activity.overridePendingTransition(0, 0)

                val newState = vd.copy(list = vd.list.filter { it.hashTag != targetData.hashTag })

                newState toT Success(Unit)
            } else vd toT Fail(
                "finishTargetActivity | failed, target item not find: " +
                        "target=$targetData but stack has ${vd.list.joinToString()}"
            )
        }

    fun findTargetActivityItem(activity: Activity): YRoute<ActivitiesState, ActivityData?> =
        routeF { vd, cxt ->
            val item = vd.list.firstOrNull { it.activity === activity }
            vd toT Success(item)
        }
    //</editor-fold>

    fun routeStartActivityByIntent(intent: Intent): YRoute<ActivitiesState, Activity> =
        startActivity(intent)

    fun <A : Activity> routeStartActivity(builder: ActivityBuilder<A>): YRoute<ActivitiesState, A> =
        createActivityIntent<Activity, ActivitiesState>(builder)
                .flatMapR { startActivity(it, builder.animData).ofType(type<A>()) }

    fun <A : Activity> routeStartActivityForResult(builder: ActivityBuilder<A>, requestCode: Int): YRoute<ActivitiesState, A> =
        createActivityIntent<A, ActivitiesState>(builder)
            .flatMapR { intent -> startActivityForResult(intent, requestCode, builder.animData) }
                .mapResult {
                    @Suppress("UNCHECKED_CAST")
                    it as A
                }

    fun <A> routeStartActivityForRx(builder: ActivityBuilder<A>): YRoute<ActivitiesState, Tuple2<A, Single<Tuple2<Int, Bundle?>>>> =
            startActivityForRx(builder)

    val routeFinishTop: YRoute<ActivitiesState, Unit> = backActivity

    fun routeFinish(activity: Activity): YRoute<ActivitiesState, Unit> =
        findTargetActivityItem(activity)
            .composeWith { state, cxt, item ->
                if (item == null) {
                    activity.finish()
                    state toT Success(Unit)
                } else {
                    finishTargetActivity(item).runRoute(state, cxt)
                }
            }
}


fun CoreEngine<ActivitiesState>.bindApp(): Completable =
    routeCxt.globalActivityLife.bindActivityLife()
        .map { event ->
            GlobalScope.launch {
                run(saveActivitiesInstanceState(event))
            }.invokeOnCompletion {
                it?.printStackTrace()
            }
            event
        }
        .map {
            GlobalScope.launch {
                run(globalActivityLogic(it))
            }.invokeOnCompletion {
                it?.printStackTrace()
            }
        }
        .ignoreElements()

private const val SAVE_STORE_HASH_TAG = "manager__hash_tag"

fun globalActivityLogic(event: ActivityLifeEvent): YRoute<ActivitiesState, Unit> =
    routeF { state, cxt ->
        Logger.d("globalActivityLogic", "start deal event: $event")
        when (event) {
            is ActivityLifeEvent.OnCreate -> {
                val newState = if (state.list.all { it.activity !== event.activity }) {
                    state.copy(
                        list = state.list + ActivityData(
                            event.activity,
                            CoreID.get(),
                            mapOf("message" to "this is globalActivityLogic auto generate ActivityData item."),
                            animData = null
                        )
                    )
                } else {
                    val savedHashTag = event.savedInstanceState?.getString(SAVE_STORE_HASH_TAG)?.toLongOrNull()
                    if (savedHashTag != null) {
                        state.copy(list = state.list.map {
                            if (it.hashTag == savedHashTag)
                                it.copy(activity = event.activity)
                            else it
                        })
                    } else state
                }

                newState
            }
            is ActivityLifeEvent.OnSaveInstanceState -> {
                val item = state.list.firstOrNull { it.activity === event.activity }
                if (item != null)
                    event.outState.apply { putString(SAVE_STORE_HASH_TAG, item.hashTag.toString()) }

                state
            }
            is ActivityLifeEvent.OnDestroy -> {
                Logger.d("globalActivityLogic OnDestroy", "start find: ${event.activity.hashCode()}")
                val item = state.list.firstOrNull { it.activity === event.activity }
                Logger.d("globalActivityLogic OnDestroy", "find result: ${item}")

                if (item != null)
                    state.copy(list = state.list.filter { it.activity !== event.activity })
                else
                    state
            }
            else -> state
        }.let { it toT Success(Unit) }
    }

fun saveActivitiesInstanceState(event: ActivityLifeEvent): YRoute<ActivitiesState, Unit> =
        routeF { state, cxt ->
            when (event) {
                is ActivityLifeEvent.OnSaveInstanceState -> {
                    val bundle = event.outState

                    SaveInstanceActivityUtil.routeSave(bundle, event.activity)
                            .runRoute(state, cxt)
                }
                is ActivityLifeEvent.OnCreate -> {
                    val bundle = event.savedInstanceState
                    if (bundle != null)
                        SaveInstanceActivityUtil.routeRestore(bundle, event.activity)
                                .runRoute(state, cxt)
                    else
                        state toT Success(Unit)
                }
                else -> state toT Success(Unit)
            }
        }
package indi.yume.tools.yroute

import android.app.Activity
import android.os.Bundle
import androidx.fragment.app.Fragment
import arrow.core.toT
import indi.yume.tools.yroute.datatype.Success
import indi.yume.tools.yroute.datatype.YRoute
import indi.yume.tools.yroute.datatype.routeF
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

typealias SaveKey = String

data class SaveInstanceState(
    val savedState: Map<SaveKey, Any> = emptyMap()
)

object SaveInstanceUtil {
    const val INTENT_KEY__SAVE_KEY = "intent_key__save_key"

    val lock = this

    var saveInstanceState: SaveInstanceState = SaveInstanceState()

    fun save(state: Any, bundle: Bundle) = synchronized(lock) {
        val saveKey = state.hashCode().toString()
        bundle.putString(INTENT_KEY__SAVE_KEY, saveKey)

        val oldState = saveInstanceState
        val newState = oldState.copy(
                savedState = oldState.savedState + (saveKey to state)
        )
        saveInstanceState = newState
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> restore(bundle: Bundle, type: Class<T>): T? = synchronized(lock) {
        val saveKey = bundle.getString(INTENT_KEY__SAVE_KEY)

        if (saveKey != null) {
            val oldState = saveInstanceState
            val s = oldState.savedState[saveKey] as? T
            val newState = oldState.copy(savedState = oldState.savedState - saveKey)
            saveInstanceState = newState

            s
        } else null
    }

    inline fun <reified T> restore(bundle: Bundle): T? = restore(bundle, T::class.java)
}

data class ActivitySavedData(
    val hashTag: Long,
    val extra: Map<String, Any> = emptyMap(),
    val animData: AnimData?
) {
    constructor(activityData: ActivityData): this(
        activityData.hashTag,
        activityData.extra,
        activityData.animData
    )

    fun asActivityData(activity: Activity) =
        ActivityData(activity, hashTag, extra, animData)
}

object SaveInstanceActivityUtil {
    const val INTENT_KEY__ACTIVITY_TAG = "intent_key__activity_tag"

    val lock = this
    private var savedMap: Map<Long, ActivitySavedData> = emptyMap()

    fun save(bundle: Bundle, state: ActivitiesState, activity: Activity) {
        val target = state.list.find { it.activity == activity }
        if (target != null) {
            bundle.putLong(INTENT_KEY__ACTIVITY_TAG, target.hashTag)
            synchronized(lock) {
                savedMap = savedMap + (target.hashTag to ActivitySavedData(target))
            }
        }
    }

    fun restore(bundle: Bundle, oldState: ActivitiesState, activity: Activity): ActivitiesState {
        val tag = if (bundle.containsKey(INTENT_KEY__ACTIVITY_TAG))
            bundle.getLong(INTENT_KEY__ACTIVITY_TAG)
        else null

        return if (tag != null) {
            val targetData = synchronized(lock) {
                val target = savedMap[tag]
                savedMap = savedMap - tag
                target
            }?.asActivityData(activity = activity)

            val stackExtra = targetData?.getStackExtra()
            val newStackExtra = stackExtra?.restore(activity)

            val newTargetData = newStackExtra?.let { targetData.putStackExtra(it) }

            val newState = oldState.copy(list = when {
                newTargetData == null -> oldState.list
                oldState.list.any { it.hashTag == tag } -> oldState.list.map {
                    if (it.hashTag == tag) newTargetData ?: it
                    else it
                }
                else -> oldState.list + newTargetData
            })

            newState
        } else oldState
    }

    fun routeRestore(bundle: Bundle, activity: Activity): YRoute<ActivitiesState, Unit> =
            routeF { state, cxt ->
                val newState = restore(bundle, state, activity)
                newState toT Success(Unit)
            }
}

object SaveInstanceFragmentUtil {
    const val INTENT_KEY__FRAGMENT_TAG = "intent_key__fragment_tag"

    val lock = this
    private var savedMap: Map<Long, FragSaveData> = emptyMap()

    fun save(fragment: Fragment, bundle: Bundle) {
        if (fragment is StackFragment) {
            val hashTag = fragment.controller.hashTag
            if (hashTag != null) {
                bundle.putLong(INTENT_KEY__FRAGMENT_TAG, hashTag)
                synchronized(lock) {
                    val param = if (fragment is FragmentParam<*>)
                        kotlin.runCatching { fragment.injector.firstElement().timeout(100, TimeUnit.MILLISECONDS)
                                .blockingGet() }.getOrNull()
                    else null
                    savedMap = savedMap + (hashTag to FragSaveData(fragment.controller, param))
                }
            }
        }
    }

    suspend fun restore(bundle: Bundle, oldState: StackFragState<*, *>, fragment: Fragment): StackFragState<*, *> = coroutineScope {
        val tag = if (bundle.containsKey(INTENT_KEY__FRAGMENT_TAG))
            bundle.getLong(INTENT_KEY__FRAGMENT_TAG)
        else null

        if (tag != null) {
            if (fragment is StackFragment) {
                val savedData = synchronized(lock) {
                    val savedData = savedMap[tag]
                    if (savedData != null) {
                        fragment.controller = savedData.controller
                    }
                    savedMap = savedMap - tag
                    savedData
                }

                if (fragment is FragmentParam<*> && savedData?.param != null) {
                    launch(YRouteConfig.fragmentCreateContext) {
                        try {
                            fragment.unsafePutParam(savedData.param)
                        } catch (e: Throwable) {
                            Logger.d("restore", e.message ?: "unsafePutParam has error.")
                        }
                    }
                }

            }
            oldState.restore(fragment, tag)
        } else oldState
    }

    fun <F> routeRestore(fragment: Fragment, bundle: Bundle): YRoute<StackFragState<F, StackType<F>>, Unit> =
            routeF { state, cxt ->
                val newState = restore(bundle, state, fragment)
                (newState as StackFragState<F, StackType<F>>) toT Success(Unit)
            }

    data class FragSaveData(
            val controller: FragController,
            val param: Any?
    )
}
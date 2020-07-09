package indi.yume.tools.yroute

import android.app.Activity
import android.os.Bundle
import androidx.fragment.app.Fragment
import arrow.core.toT
import indi.yume.tools.yroute.datatype.Success
import indi.yume.tools.yroute.datatype.YRoute
import indi.yume.tools.yroute.datatype.routeF
import kotlinx.coroutines.Dispatchers
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

object SaveInstanceActivityUtil {
    const val INTENT_KEY__ACTIVITY_TAG = "intent_key__activity_tag"

    val lock = this
    private var savedMap: Map<Long, ActivityData> = emptyMap()

    suspend fun save(bundle: Bundle, state: ActivitiesState, activity: Activity) {
        val target = state.list.find { it.activity == activity }
        if (target != null) {
            synchronized(lock) {
                savedMap = savedMap + (target.hashTag to target)
            }
            withContext(Dispatchers.Main) {
                bundle.putLong(INTENT_KEY__ACTIVITY_TAG, target.hashTag)
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
            }?.copy(activity = activity)

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

    fun routeSave(bundle: Bundle, activity: Activity): YRoute<ActivitiesState, Unit> =
            routeF { state, cxt ->
                save(bundle, state, activity)
                state toT Success(Unit)
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

    suspend fun save(bundle: Bundle, state: StackFragState<*, *>, fragment: Fragment) {
        val stack = state.stack

        val target = when (stack) {
            is StackType.Single<*> -> stack.list.find {
                    it.t == fragment || (fragment is StackFragment && fragment.controller.hashTag == it.hashTag)
                }
            is StackType.Table<*> -> stack.table.values.flatMap { it }.find {
                it.t == fragment || (fragment is StackFragment && fragment.controller.hashTag == it.hashTag)
            }
        }
        if (target != null) {
            if (fragment is StackFragment) synchronized(lock) {
                val param = if (fragment is FragmentParam<*>)
                    fragment.injector.firstElement().timeout(100, TimeUnit.MILLISECONDS)
                            .blockingGet()
                else null
                savedMap = savedMap + (target.hashTag to FragSaveData(fragment.controller, param))
            }
            withContext(Dispatchers.Main) {
                bundle.putLong(INTENT_KEY__FRAGMENT_TAG, target.hashTag)
            }
        }
    }

    fun restore(bundle: Bundle, oldState: StackFragState<*, *>, fragment: Fragment): StackFragState<*, *> {
        val tag = if (bundle.containsKey(INTENT_KEY__FRAGMENT_TAG))
            bundle.getLong(INTENT_KEY__FRAGMENT_TAG)
        else null

        return if (tag != null) {
            if (fragment is StackFragment) synchronized(lock) {
                val savedData = savedMap[tag]
                if (savedData != null) {
                    fragment.controller = savedData.controller
                    if (fragment is FragmentParam<*> && savedData.param != null)
                        fragment.unsafePutParam(savedData.param)
                }
                savedMap = savedMap - tag
            }
            oldState.restore(fragment, tag)
        } else oldState
    }

    fun <F> routeSave(fragment: Fragment, bundle: Bundle): YRoute<StackFragState<F, StackType<F>>, Unit> =
            routeF { state, cxt ->
                save(bundle, state, fragment)
                state toT Success(Unit)
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
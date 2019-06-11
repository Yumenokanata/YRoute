package indi.yume.tools.yroute.test4

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.annotation.IdRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import arrow.core.*
import arrow.core.extensions.either.monad.flatMap
import arrow.data.ReaderT
import arrow.effects.IO
import arrow.effects.extensions.io.monadDefer.binding
import arrow.optics.Lens
import arrow.optics.PLens
import arrow.optics.PSetter
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.Subject
import java.lang.ClassCastException

interface FragActivityItem<out VD> {
    val state: VD
}

//data class FragActivityData<out VD>(override val activity: FragmentActivity,
//                                    override val tag: Any? = null,
//                                    override val state: VD) : ActivityItem, FragActivityItem<VD>

data class StackActivityData<T>(override val activity: FragmentActivity,
                                override val hashTag: Int,
                                val controller: StackActivity<T>,
                                override val state: StackFragActState<T>) : ActivityItem, FragActivityItem<StackFragActState<T>> {
    override fun changeActivity(act: Activity): ActivityItem = copy(activity = act as FragmentActivity)
}

data class StackFragActState<T>(val stack: StackType<T>)

typealias TableTag = String

sealed class StackType<T> {
    data class Single<T>(val list: List<FItem<T>> = emptyList()) : StackType<T>()
    data class Table<T>(val defaultMap: Map<TableTag, Class<out T>> = emptyMap(),
                        val defaultTag: TableTag = "default___tag",
                        val table: Map<TableTag, List<FItem<T>>> = emptyMap(),
                        val current: Pair<TableTag, FItem<T>?>? = null) : StackType<T>() {
        companion object {
            fun <T> create(defaultMap: Map<TableTag, Class<out T>> = emptyMap(),
                           defaultTag: TableTag = "default___tag"): Table<T> =
                Table(defaultMap = defaultMap, defaultTag = defaultTag)
        }
    }
}

data class FItem<T>(val t: T, val hashTag: Int, val tag: Any? = null)

open class FragmentBuilder<A>(val clazz: Class<out A>) {
    internal var createIntent: RouteCxt.() -> Intent = {
        Intent(app, clazz)
    }

    internal var doForFragment: RouteCxt.(A) -> Unit = { }

    var stackTag: TableTag? = null

    var fragmentTag: Any? = null

    fun withParam(data: Bundle): FragmentBuilder<A> {
        createIntent = createIntent andThen { it.putExtras(data) }
        return this
    }

    fun withIntent(f: (Intent) -> Unit): FragmentBuilder<A> {
        createIntent = createIntent andThen { f(it); it }
        return this
    }

    fun withFragment(f: RouteCxt.(A) -> Unit): FragmentBuilder<A> {
        val action = doForFragment
        doForFragment = { action(it); f(it) }
        return this
    }

    fun withFragmentTag(tag: Any?): FragmentBuilder<A> {
        this.fragmentTag = tag
        return this
    }

    fun withStackTag(tag: TableTag?): FragmentBuilder<A> {
        this.stackTag = tag
        return this
    }
}

interface FragmentParam<T> {
    val injector: Subject<T>

    companion object {
        fun <T> defaultInjecter(): Subject<T> =
            BehaviorSubject.create<T>().toSerialized()
    }
}

fun <T, P> FragmentBuilder<T>.withParam(param: P): FragmentBuilder<T> where T : FragmentParam<P> =
    withFragment { f -> f.injector.onNext(param) }

typealias Checker<T> = (T) -> Boolean

interface StackActivity<F> {
    @get:IdRes
    val fragmentId: Int

    val initStack: StackType<F>

    var controller: StackController
}

class StackController(var hashTag: Int? = null) {
    companion object {
        fun defaultController(): StackController = StackController()
    }
}

interface StackFragment {
    var controller: StackController
}

typealias InnerError<S> = Either<Fail, S>

enum class FinishResult {
    FinishOver, FinishParent
}

object StackActivityRoute {

    //<editor-fold defaultstate="collapsed" desc="Routes">
    fun <F> stackActivityGetter(stackActivity: StackActivity<F>): ReaderT<EitherPartialOf<Fail>, ActivitiesState, StackActivityData<F>> =
        ReaderT { state ->
            when (val target = state.list.firstOrNull { it.hashTag == stackActivity.controller.hashTag }) {
                null -> Fail("Can not find target activity: target is $stackActivity, but stack is ${state.list.joinToString { it.toString() }}").left()
                !is StackActivityData<*> -> Fail("Target activity is not a StackActivity: target=$target").left()
                else -> (target as StackActivityData<F>).right()
            }
        }

    val activityItemSetter: PSetter<ActivitiesState, InnerError<ActivitiesState>, ActivitiesState, ActivityItem> =
        PSetter { state, f ->
            val item = f(state)

            val target = state.list.withIndex().firstOrNull { it.value.hashTag == item.hashTag }
            if (target == null)
                Fail("Can not find target activity: target is $item, but stack is ${state.list.joinToString { it.toString() }}").left()
            else
                state.copy(list = state.list.toMutableList().apply { set(target.index, item) }).right()
        }

    fun <F> stackTypeLens(): Lens<StackActivityData<F>, StackType<F>> = PLens(
        get = { activityData -> activityData.state.stack },
        set = { activityData, stack -> activityData.copy(state = activityData.state.copy(stack = stack)) }
    )

    fun <F> singleStackFGetter(stackFragment: StackFragment?): ReaderT<EitherPartialOf<Fail>, StackType.Single<F>, Tuple2<FItem<F>?, FItem<F>>> =
        ReaderT { singleStack ->
            val target = if (stackFragment == null)
                singleStack.list.withIndex().lastOrNull()
            else
                singleStack.list.withIndex().firstOrNull { it.value.hashTag == stackFragment.controller.hashTag }

            when {
                target == null -> Fail("Can not find target fragment: target=$stackFragment, but stack is ${singleStack.list.joinToString { it.toString() }}").left()
                target.index == 0 -> (null toT target.value).right()
                else -> (singleStack.list[target.index - 1] toT target.value).right()
            }
        }

    fun <F> tableStackFGetter(stackFragment: StackFragment?): ReaderT<EitherPartialOf<Fail>, StackType.Table<F>, Tuple3<TableTag, FItem<F>?, FItem<F>>> =
        ReaderT { tableStack ->
            fun <K, V> findAtMapList(map: Map<K, List<V>>, checker: (V) -> Boolean): Tuple3<K, V?, V>? {
                for ((k, list) in map) {
                    val result = list.withIndex().firstOrNull { checker(it.value) }
                    if (result != null)
                        return Tuple3(k,
                            if (result.index != 0) list[result.index - 1] else null,
                            result.value)
                }
                return null
            }

            val target = when {
                stackFragment != null -> findAtMapList(tableStack.table) { it.hashTag == stackFragment.controller.hashTag }
                tableStack.current != null -> {
                    val tag = tableStack.current.first
                    val targetList = tableStack.table[tag]
                    val target = targetList?.lastOrNull()
                    if (targetList != null && target != null)
                        Tuple3(tag,
                            targetList.getOrNull(targetList.size - 1),
                            target
                        )
                    else null
                }
                else -> null
            }

            target?.right() ?: Fail("Can not find target fragment: target=$stackFragment, but stack is ${tableStack.table}").left()
        }


    fun <F : Fragment> stackFragActivityOrNull(): YRoute<ActivitiesState, StackActivity<F>, StackActivityData<F>?> =
        routeF { vd, cxt, stack ->
            IO {
                val item = vd.list.firstOrNull { it.hashTag == stack.controller.hashTag }

                vd toT if (item != null) {
                    val stackActivityData = item as? StackActivityData<F>

                    if (stackActivityData != null) {
                        Success(stackActivityData)
                    } else {
                        Success(null)
                    }
                } else Success(null)
            }
        }

    fun <F : Fragment> stackFragActivity(): YRoute<ActivitiesState, StackActivity<F>, StackActivityData<F>> =
        routeF { vd, cxt, stack ->
            IO {
                val item = vd.list.firstOrNull { it.hashTag == stack.controller.hashTag }

                if (item != null) {
                    val stackActivityData = item as? StackActivityData<F>

                    if (stackActivityData == null) {
                        vd.copy(list = vd.list.map { if (it.hashTag == stack.controller.hashTag && item.activity is FragmentActivity)
                            StackActivityData<F>(it.activity as FragmentActivity, it.hashTag, stack, StackFragActState(stack.initStack))
                        else it }) toT
                                Success(StackActivityData<F>(item.activity as FragmentActivity, item.hashTag, stack, StackFragActState(stack.initStack)))
                    } else {
                        vd toT Success(stackActivityData)
                    }
                } else vd toT Fail("fragmentActivity | FragmentActivity not find: " +
                        "target=$stack, but activity list=${vd.list.joinToString { it.activity.toString() }}"
                )
            }
        }

    fun <T : Fragment> startStackFragActivity(): YRoute<ActivitiesState, ActivityBuilder<FragmentActivity>, FragmentActivity> =
        ActivitiesRoute.createActivityIntent<FragmentActivity, ActivitiesState>()
            .transform { vd, cxt, intent ->
                binding {
                    val top: Context = vd.list.lastOrNull()?.activity ?: cxt.app

                    !IO { top.startActivity(intent) }

                    val (act) = cxt.bindNextActivity()
                        .firstOrError().toIO()

                    if (act !is StackActivity<*> || act !is ActivityLifecycleOwner) {
                        vd.copy(list = vd.list + ActivityData(act, act.hashCode())) toT
                                Result.fail("Stack Activity must implements `StackActivity` interface.")
                    } else if (cxt.checkComponentClass(intent, act) && act is FragmentActivity) {
                        val stack = act as StackActivity<T>
                        val defaultType = act.initStack as StackType<T>
                        val hashTag = act.hashCode()
                        !IO { act.controller.hashTag = hashTag }

                        vd.copy(list = vd.list + StackActivityData(act, hashTag, stack, StackFragActState<T>(defaultType))) toT
                                Result.success<FragmentActivity>(act)
                    } else {
                        vd.copy(list = vd.list + ActivityData(act, act.hashCode())) toT
                                Result.fail("startActivity | start activity is Success, but can not get target activity: " +
                                        "target is ${intent.component?.className} but get is $act", null)
                    }
                }
            }

    fun <F : Fragment> startFragmentAtStackActivity(): YRoute<ActivitiesState, Tuple2<StackActivity<F>, FragmentBuilder<F>>, F> =
        routeF { state, cxt, (stack, builder) ->
            binding {
                val stackActivityData = state.list.firstOrNull { it.hashTag == stack.controller.hashTag } as? StackActivityData<F>

                if (stackActivityData != null) {
                    val (newState, result) = !startFragment<F>().runRoute(stackActivityData, cxt, builder)

                    state.copy(list = state.list.map {
                        if(it.hashTag == stack.controller.hashTag) newState else it
                    }) toT result
                } else state  toT Fail("Can not find target stack activity: " +
                        "target hashTag is ${stack.controller.hashTag}, but list is ${state.list.joinToString { it.activity.toString() }}")
            }
        }

    fun <F : Fragment> switchFragmentAtStackActivity(): YRoute<ActivitiesState, Tuple2<StackActivity<F>, TableTag>, F?> =
        routeF { state, cxt, (stack, targetTag) ->
            binding {
                val stackActivityData = state.list.firstOrNull { it.hashTag == stack.controller.hashTag } as? StackActivityData<F>

                if (stackActivityData == null) {
                    state toT Fail("Can not find target stack activity: " +
                            "target hashTag is ${stack.controller.hashTag}, but list is ${state.list.joinToString { it.activity.toString() }}")
                } else if (stackActivityData.state.stack !is StackType.Table) {
                    state toT Fail("Can switch target tag, target stack is not a Table.")
                } else {
                    val (newState, result) = !switchStackAtTable<F>()
                        .subRoute(type = type<StackInnerState<StackActivityData<F>>>(),
                            mapper = { vd -> vd.map { it.state.stack as StackType.Table<F> } },
                            demapper = { svd, oldVD -> oldVD.copy(state = oldVD.state.copy(state = oldVD.state.state.copy(stack = svd.state))) })
                        .stackTran()
                        .runRoute(stackActivityData, cxt, targetTag)

                    state.copy(list = state.list.map {
                        if(it.hashTag == stack.controller.hashTag) newState else it
                    }) toT result
                }
            }
        }


    fun <A: ActivityItem, T, R> YRoute<A, T, R>.mergeActivity(): YRoute<ActivitiesState, Tuple2<Checker<Activity>, T>, R> =
        routeF { vd, cxt, (checker, param) ->
            binding {
                val svd = vd.list.firstOrNull { checker(it.activity) }

                if (svd != null) {
                    val faData = svd as? A
                    if (faData != null) {
                        val (newFaData, result) = !this@mergeActivity.runRoute(faData, cxt, param)

                        vd.copy(list = vd.list.map { if (checker(it.activity)) newFaData else it }) toT result
                    } else vd toT Fail("mergeActivity | target item data has error: " +
                            "need data.extra is FragActivityItem<Fragment>, but target data is $svd")
                } else vd toT Fail("mergeActivity | can not find target activity.")
            }
        }

    internal fun <S, F> createFragment(): YRoute<S, FragmentBuilder<F>, F> where F : Fragment =
        routeF { vd, cxt, builder ->
            val intent = builder.createIntent(cxt)

            val clazzNameE = intent.component?.className?.right()
                ?: Result.fail<F>("Can not get fragment class name, from intent: $intent").left()

            val fragmentE = clazzNameE.flatMap { clazzName ->
                try {
                    val fragClazz = Class.forName(clazzName)
                    val fragmentInstance = fragClazz.newInstance() as F
                    fragmentInstance.arguments = intent.extras
                    builder.doForFragment(cxt, fragmentInstance)
                    fragmentInstance.right()
                } catch (e: ClassNotFoundException) {
                    Fail("Can not find Fragment class: $clazzName", e).left()
                } catch (e: InstantiationException) {
                    Fail("Can not create Fragment instance.", e).left()
                } catch (e: IllegalAccessException) {
                    Fail("Can not create Fragment instance.", e).left()
                } catch (e: ClassCastException) {
                    Fail("Target Fragment type error.", e).left()
                }
            }

            when (fragmentE) {
                is Either.Right -> IO.just(vd toT Result.success(fragmentE.b))
                is Either.Left -> IO.just(vd toT fragmentE.a)
            }
        }

    internal data class StackInnerState<S>(
        val state: S,
        val activity: FragmentActivity,
        val fragmentId: Int,
        val fm: FragmentManager,
        val ft: FragmentTransaction
    ) {
        fun <T> map(f: (S) -> T): StackInnerState<T> = StackInnerState(
            state = f(state),
            activity = activity, fragmentId = fragmentId, fm = fm, ft = ft
        )
    }

    internal fun <F, T, R> YRoute<StackInnerState<StackActivityData<F>>, T, R>.stackTran(): YRoute<StackActivityData<F>, T, R> =
        routeF { vd, cxt, param ->
            binding {
                val fm = vd.activity.supportFragmentManager
                val ft = !IO { fm.beginTransaction() }
                val innerState = StackInnerState(vd, vd.activity, vd.controller.fragmentId, fm, ft)

                val (newInnerState, result) = !this@stackTran.runRoute(innerState, cxt, param)

                !IO { newInnerState.ft.commitAllowingStateLoss() }

                newInnerState.state toT result
            }
        }

    internal fun <S, Sub, T, R> YRoute<StackInnerState<Sub>, T, R>.mapInner(
        type: TypeCheck<S> = type(),
        mapper: (S) -> Sub, demapper: (Sub, S) -> S): YRoute<StackInnerState<S>, T, R> =
        routeF { vd, cxt, param ->
            binding {
                val (newSVD, result) = !this@mapInner.runRoute(vd.map(mapper), cxt, param)

                val newVD = demapper(newSVD.state, vd.state)

                vd.copy(state = newVD) toT result
            }
        }

    internal fun <F> startFragAtSingle(): YRoute<StackInnerState<StackType.Single<F>>, Tuple2<FragmentBuilder<F>, F>, F> where F : Fragment =
        routeF { vd, cxt, (builder, fragment) ->
            val newItem = FItem<F>(fragment, fragment.hashCode(), builder.fragmentTag)
            val stackState = vd.state
            val newStack = stackState.copy(list = stackState.list + newItem)

            IO {
                for (i in stackState.list.size - 1 downTo 0) {
                    val f = stackState.list[i]
                    if (f.t.isVisible) vd.ft.hide(f.t)
                }
                vd.ft.add(vd.fragmentId, fragment)

                vd.copy(state = newStack) toT
                        Result.success(fragment)
            }
        }

    internal fun <F> startFragAtTable(): YRoute<StackInnerState<StackType.Table<F>>, Tuple2<FragmentBuilder<F>, F>, F> where F : Fragment =
        routeF { vd, cxt, (builder, fragment) ->
            val newItem = FItem<F>(fragment, fragment.hashCode(), builder.fragmentTag)

            val oldStackState = vd.state
            val stackTag = builder.stackTag

            val targetTag = stackTag ?: oldStackState.current?.first ?: oldStackState.defaultTag

            binding {
                val innerState =
                    if (oldStackState.current == null || oldStackState.current.first != stackTag) {
                        val (state, beforeFrag) = !switchStackAtTable<F>(true).runRoute(vd, cxt, targetTag)
                        state
                    } else vd

                val innerStackState = innerState.state
                val targetStack = innerStackState.table[targetTag] ?: emptyList()
                val newTable = innerState.state.table - targetTag + (targetTag to targetStack + newItem)

                !IO { vd.ft.add(vd.fragmentId, fragment) }

                innerState.copy(
                    state = innerState.state.copy(
                        table = newTable,
                        current = targetTag to newItem)) toT Success(fragment)
            }
        }

    internal fun <F> switchStackAtTable(silentSwitch: Boolean = false): YRoute<StackInnerState<StackType.Table<F>>, TableTag, F?> where F : Fragment =
        routeF { vd, cxt, targetTag ->
            val stackState = vd.state
            val currentTag = stackState.current?.first

            if (targetTag == currentTag) {
                IO.just(vd toT Success(stackState.table[currentTag]?.lastOrNull()?.t))
            } else binding {
                if (currentTag != null) {
                    val currentStack = stackState.table[currentTag] ?: emptyList()

                    !IO {
                        for (i in currentStack.size - 1 downTo 0) {
                            val f = currentStack[i]
                            if (f.t.isVisible) vd.ft.hide(f.t)
                        }
                    }
                }

                val targetStack = stackState.table[targetTag] ?: emptyList()
                if (targetStack.isEmpty()) {
                    // 如果准备切换到的目标堆栈为空的话，并且配置有默认Fragment的话, 切换到目标Stack并启动默认Fragment
                    val defaultFragClazz: Class<out F>? = stackState.defaultMap[targetTag]

                    if (defaultFragClazz != null) {
                        val (_, result) = !createFragment<Unit, F>().runRoute(Unit, cxt, FragmentBuilder(defaultFragClazz).withStackTag(targetTag))

                        when (result) {
                            is Success -> !IO {
                                vd.ft.add(vd.fragmentId, result.t)
                                if (silentSwitch) vd.ft.hide(result.t)

                                val newItem = FItem<F>(result.t, result.t.hashCode(), targetTag)
                                vd.copy(state = vd.state.copy(table = stackState.table + (targetTag to targetStack + newItem),
                                    current = targetTag to newItem)) toT Success(result.t)
                            }
                            is Fail -> vd toT result
                        }
                    } else vd.copy(state = vd.state.copy(current = targetTag to null)) toT Result.success(null)
                } else !IO {
                    val item = targetStack.last()
                    if (!silentSwitch) vd.ft.show(item.t)

                    vd.copy(state = vd.state.copy(current = targetTag to item)) toT Success(item.t)
                }
            }
        }

    fun <F> startFragment(): YRoute<StackActivityData<F>, FragmentBuilder<F>, F> where F : Fragment =
        createFragment<StackActivityData<F>, F>().packageParam()
            .compose(foldStack(startFragAtSingle<F>(), startFragAtTable<F>()).intoStackType().stackTran())

    internal fun <F : Fragment, T ,R> YRoute<StackInnerState<StackType<F>>, T, R>.intoStackType()
            : YRoute<StackInnerState<StackActivityData<F>>, T, R> = mapInner(
        mapper = { s -> s.state.stack }, demapper = { sub, s -> s.copy(state = s.state.copy(stack = sub)) }
    )

    internal fun <F : Fragment, T ,R> foldStack(
        single: YRoute<StackInnerState<StackType.Single<F>>, T, R>,
        table: YRoute<StackInnerState<StackType.Table<F>>, T, R>
    ): YRoute<StackInnerState<StackType<F>>, T, R> = routeF { state, cxt, param ->
        val stack = state.state
        when (stack) {
            is StackType.Single<F> -> single
                .mapInner(type = type<StackType<F>>(),
                    mapper = { vd -> vd as StackType.Single<F> },
                    demapper = { svd, oldVD -> svd })
            is StackType.Table<F> -> table
                .mapInner(type = type<StackType<F>>(),
                    mapper = { vd -> vd as StackType.Table<F> },
                    demapper = { svd, oldVD -> svd })
        }.runRoute(state, cxt, param)
    }

    fun <F> finishFragment(): YRoute<ActivitiesState, Tuple2<StackActivity<F>, StackFragment?>, Unit> where F : Fragment =
        routeF { activitiesState, cxt, (activityStack, fragmentStack) ->
            val either = stackActivityGetter(activityStack).run(activitiesState).fix()
            val targetActivity = when (either) {
                is Either.Left -> return@routeF IO.just(activitiesState toT either.a)
                is Either.Right -> either.b
            }

            binding {
                val (newStackState, result) = !finishFragmentAtStack<F>().runRoute(targetActivity, cxt, fragmentStack)
                val newStateEither = activityItemSetter.set(activitiesState, newStackState)

                when(result) {
                    is Fail -> when(newStateEither) {
                        is Either.Left -> activitiesState toT newStateEither.a
                        is Either.Right -> newStateEither.b toT result
                    }
                    is Success -> when(newStateEither) {
                        is Either.Left -> activitiesState toT newStateEither.a
                        is Either.Right -> when(result.t) {
                            FinishResult.FinishOver -> newStateEither.b toT Success(Unit)
                            FinishResult.FinishParent -> !ActivitiesRoute.finishTargetActivity.runRoute(newStateEither.b, cxt, targetActivity)
                        }
                    }
                }
            }
        }

    fun <F> finishFragmentAtStack(): YRoute<StackActivityData<F>, StackFragment?, FinishResult> where F : Fragment =
        foldStack(finishFragmentAtSingle<F>(), finishFragmentAtTable<F>()).intoStackType().stackTran()

    internal fun <F : Fragment> finishFragmentAtSingle(): YRoute<StackInnerState<StackType.Single<F>>, StackFragment?, FinishResult> =
        routeF { state, cxt, paramF ->
            val stack = state.state

            val result = singleStackFGetter<F>(paramF).run(stack).fix()

            val (backF, targetF) = when (result) {
                is Either.Left -> return@routeF IO.just(state toT result.a)
                is Either.Right -> result.b
            }

            binding {
                val newState = state.copy(state = stack.copy(list = stack.list.filter { it.hashTag != targetF.hashTag }))

                !IO {
                    state.ft.show(targetF.t)
                    if (backF != null) state.ft.hide(backF.t)
                }

                newState toT Success(when {
                    newState.state.list.isEmpty() -> FinishResult.FinishParent
                    else -> FinishResult.FinishOver
                })
            }
        }

    internal fun <F : Fragment> finishFragmentAtTable(): YRoute<StackInnerState<StackType.Table<F>>, StackFragment?, FinishResult> =
        routeF { state, cxt, paramF ->
            val stack = state.state

            val result = tableStackFGetter<F>(paramF).run(stack).fix()

            val (targetTag, backF, targetF) = when (result) {
                is Either.Left -> return@routeF IO.just(state toT result.a)
                is Either.Right -> result.b
            }

            binding {
                val newState = state.copy(state = stack.copy(
                    table = stack.table + (targetTag to (stack.table[targetTag]?.filter { it.hashTag != targetF.hashTag } ?: emptyList())),
                    current = if (stack.current?.second?.hashTag == targetF.hashTag) backF?.let { targetTag to it } else stack.current))

                !IO {
                    state.ft.show(targetF.t)
                    if (backF != null) state.ft.hide(backF.t)
                }

                newState toT Success(
                    if (stack.table[targetTag].isNullOrEmpty()) FinishResult.FinishParent
                    else FinishResult.FinishOver
                )
            }
        }
    //</editor-fold>
}
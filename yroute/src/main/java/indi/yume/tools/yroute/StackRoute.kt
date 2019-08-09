package indi.yume.tools.yroute

import android.annotation.TargetApi
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.transition.ChangeBounds
import android.transition.ChangeTransform
import android.transition.TransitionSet
import android.view.View
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
import indi.yume.tools.yroute.datatype.*
import indi.yume.tools.yroute.datatype.Success
import io.reactivex.Single
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.Subject
import java.lang.ClassCastException
import kotlin.random.Random
import androidx.annotation.AnimRes
import androidx.core.view.ViewCompat
import arrow.effects.extensions.io.monoid.combineAll
import indi.yume.tools.yroute.YRouteConfig.globalDefaultAnimData


//data class FragActivityData<out VD>(override val activity: FragmentActivity,
//                                    override val tag: Any? = null,
//                                    override val state: VD) : ActivityItem, FragActivityItem<VD>

const val EXTRA_KEY__STACK_ACTIVITY_DATA = "extra_key__stack_activity_data"

fun ActivityData.getStackExtra(): StackActivityExtraState<*, *>? =
    extra[EXTRA_KEY__STACK_ACTIVITY_DATA] as? StackActivityExtraState<*, *>

fun ActivityData.putStackExtra(extraState: StackActivityExtraState<*, *>): ActivityData =
    copy(extra = extra + (EXTRA_KEY__STACK_ACTIVITY_DATA to extraState))

fun <F, T> ActivityData.getStackExtraOrDefault(host: StackHost<F, T>? = null): StackActivityExtraState<F, T>?
        where T : StackType<F> {
    val extra = getStackExtra()

    return if (extra != null) extra as StackActivityExtraState<F, T>
    else getDefaultStackExtra(host)
}

fun <F, T> ActivityData.getDefaultStackExtra(host: StackHost<F, T>? = null): StackActivityExtraState<F, T>?
        where T : StackType<F> {
    return if (activity is FragmentActivity && host != null)
        StackActivityExtraState(
                activity = activity,
                state = StackFragState(
                        host = host,
                        stack = host.initStack,
                        fm = activity.supportFragmentManager
                ))
    else if (activity is FragmentActivity && activity is StackHost<*, *>) {
        val actHost = activity as StackHost<F, T>
        StackActivityExtraState(
                activity = activity,
                state = StackFragState(
                        host = actHost,
                        stack = actHost.initStack,
                        fm = activity.supportFragmentManager
                ))
    } else null
}

fun ActivitiesState.putStackExtraToActState(host: StackHost<*, *>, extra: StackActivityExtraState<*, *>): ActivitiesState =
        copy(list = list.map {
            if (it.hashTag == host.controller.hashTag)
                it.putStackExtra(extra)
            else it
        })

data class StackActivityExtraState<F, Type : StackType<F>>(
    val activity: FragmentActivity,
    val state: StackFragState<F, Type>)

data class StackFragState<F, out Type : StackType<F>>(
    val host: StackHost<F, Type>,
    val stack: Type,
    val fm: FragmentManager)

data class AnimData(
        @AnimRes val enterAnim: Int = R.anim.fragment_left_enter,
        @AnimRes val exitAnim: Int = R.anim.fragment_left_exit,
        @AnimRes val enterStayAnimForActivity: Int = R.anim.stay_anim
)

interface StackHost<F, out T : StackType<F>> {
    @get:IdRes
    val fragmentId: Int

    val initStack: T

    var controller: StackController

    var defaultAnim: AnimData?
        get() = controller.defaultAnim
        set(value) {
            controller.defaultAnim = value
        }

    fun onBackPressed(currentStackSize: Int): Boolean = false
}

interface StackController {
    var hashTag: Long?

    var defaultAnim: AnimData?

    companion object {
        fun defaultController(): StackController = StackControllerImpl()
    }
}

class StackControllerImpl(
        override var hashTag: Long? = null,
        override var defaultAnim: AnimData? = globalDefaultAnimData) : StackController

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

data class FItem<T>(val t: T, val hashTag: Long, val tag: Any? = null, val anim: AnimData?)

open class FragmentBuilder<out F> {
    internal var createIntent: RouteCxt.() -> Intent

    constructor(clazz: Class<out F>) {
        createIntent = {
            Intent(app, clazz)
        }
    }

    constructor(intent: Intent) {
        createIntent = { intent }
    }

    internal var doForFragment: RouteCxt.(Any) -> Unit = { }

    var stackTag: TableTag? = null

    var fragmentTag: Any? = null

    var animData: AnimData? = globalDefaultAnimData

    fun withBundle(data: Bundle): FragmentBuilder<F> {
        createIntent = createIntent andThen { it.putExtras(data) }
        return this
    }

    fun withIntent(f: (Intent) -> Unit): FragmentBuilder<F> {
        createIntent = createIntent andThen { f(it); it }
        return this
    }

    @Suppress("UNCHECKED_CAST")
    fun withFragment(f: RouteCxt.(F) -> Unit): FragmentBuilder<F> {
        val action = doForFragment
        doForFragment = { action(it); f(it as F) }
        return this
    }

    fun withFragmentTag(tag: Any?): FragmentBuilder<F> {
        this.fragmentTag = tag
        return this
    }

    fun withStackTag(tag: TableTag?): FragmentBuilder<F> {
        this.stackTag = tag
        return this
    }

    fun withAnimData(anim: AnimData?): FragmentBuilder<F> {
        this.animData = anim
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


interface StackFragment : FragmentLifecycleOwner {
    var controller: FragController

    fun getIntent(): Intent? = controller.fromIntent

    // 此方法会在当前Fragment结束之前, 如果requestCode != -1, 就会回调. 用于通知之后其他界面可以响应返回值了
    fun preSendFragmentResult(requestCode: Int, resultCode: Int, data: Bundle?) {
        if (this is Fragment)
            makeState(FragmentLifeEvent.PreSendFragmentResult(this, requestCode, resultCode, data))
     }

    /**
     * 此方法不保证一定会像FragmentManager库中一样被调用,
     * 比如在StackFragment中启动一个新的StackActivity并在通过startFragmentForResult启动一个Fragment,
     * 这个Fragment在finish()之后是不会回调本Fragment的此方法的。
     *
     *                      ---------startForResult------------->
     *                     |                                     |
     * StackActivity1{ Fragment1 }           StackActivity2{ Fragment2 }
     *                     X                                     |
     *                      ------not call this func <--------finish()
     *
     * 此方法只能保证同一个StackActivity下的前后Fragment会回调
     */
    fun onFragmentResult(requestCode: Int, resultCode: Int, data: Bundle?) {
        if (this is Fragment)
            makeState(FragmentLifeEvent.OnFragmentResult(this, requestCode, resultCode, data))
    }

    fun onShow(mode: OnShowMode) {
        if (this is Fragment) makeState(FragmentLifeEvent.OnShow(this, mode))
    }

    fun onHide(mode: OnHideMode) {
        if (this is Fragment) makeState(FragmentLifeEvent.OnHide(this, mode))
    }

    val requestCode: Int
        get() = controller.requestCode
    val resultCode: Int
        get() = controller.resultCode
    val resultData: Bundle?
        get() = controller.resultData

    fun setResult(resultCode: Int, data: Bundle?) {
        controller.resultCode = resultCode
        controller.resultData = data
    }

    fun onBackPressed(): Boolean = false
}

interface FragController {
    var hashTag: Long?
    var fromIntent: Intent?

    var requestCode: Int
    var resultCode: Int
    var resultData: Bundle?

    companion object {
        fun defaultController(): FragController = FragControllerImpl()
    }
}

class FragControllerImpl(
    override var hashTag: Long? = null,
    override var requestCode: Int = -1,
    override var resultCode: Int = -1,
    override var resultData: Bundle? = null,

    override var fromIntent: Intent? = null
) : FragController

typealias InnerError<S> = Either<Fail, S>

enum class FinishResult {
    FinishOver, FinishParent
}

data class SingleTarget<F>(val backF: FItem<F>?, val target: FItem<F>)

data class TableTarget<F>(val tag: TableTag, val backF: FItem<F>?, val target: FItem<F>)

object StackRoute {

    //<editor-fold defaultstate="collapsed" desc="Routes">
    fun <F, T : StackType<F>> stackActivitySelecter(host: StackHost<F, T>)
            : YRoute<ActivitiesState, Lens<ActivitiesState, StackActivityExtraState<F, T>?>> =
        routeF { state, routeCxt ->
            val item = state.list.firstOrNull { it.hashTag == host.controller.hashTag }
            val extra = item?.getStackExtraOrDefault(host)

            val result: Tuple2<ActivitiesState, YResult<Lens<ActivitiesState, StackActivityExtraState<F, T>?>>> =
                if (item == null || extra == null) {
                    state toT Fail("Can not find target StackFragState: target=$host, but stack is ${state.list.joinToString()}")
                } else {
                    state.putStackExtraToActState(host, extra) toT Success(stackActivityLens(host))
                }

            IO.just(result)
        }

    fun <F, T : StackType<F>> stackActivityLens(stackHost: StackHost<F, T>): Lens<ActivitiesState, StackActivityExtraState<F, T>?> = Lens(
        get = { state ->
            val item = state.list.firstOrNull { it.activity === stackHost || it.hashTag == stackHost.controller.hashTag }
            if (stackHost.controller.hashTag == null && item != null)
                stackHost.controller.hashTag = item.hashTag

            val extra = item?.getStackExtra()
            if (item != null && extra is StackActivityExtraState<*, *>)
                extra as StackActivityExtraState<F, T>
            else null
        },
        set = { state, extra ->
            if (extra == null)
                state
            else
                state.copy(list = state.list.map { item ->
                    if (item.activity == extra.activity || item.hashTag == extra.state.host.controller.hashTag)
                        item.putStackExtra(extra)
                    else item
                })
        }
    )

    fun <F, T : StackType<F>> stackActivityGetter(stackActivity: StackHost<F, T>): ReaderT<EitherPartialOf<Fail>, ActivitiesState, StackActivityExtraState<F, T>> =
        ReaderT { state ->
            val extra = stackActivityLens(stackActivity).get(state)

            when {
                extra == null -> Fail("Can not find target activity or target activity is not a StackActivity: " +
                        "target is $stackActivity, but stack is ${state.list.joinToString { it.toString() }}").left()
                else -> (extra as? StackActivityExtraState<F, T>)?.right() ?: Fail("Target activity is not a StackActivity: target=$stackActivity").left()
            }
        }

    val activityItemSetter: PSetter<ActivitiesState, InnerError<ActivitiesState>, ActivitiesState, ActivityData> =
        PSetter { state, f ->
            val item = f(state)

            val target = state.list.withIndex().firstOrNull { it.value.hashTag == item.hashTag }
            if (target == null)
                Fail("Can not find target activity: target is $item, but stack is ${state.list.joinToString { it.toString() }}").left()
            else
                state.copy(list = state.list.toMutableList().apply { set(target.index, item) }).right()
        }

    fun <F, T : StackType<F>> stackStateForActivityLens(): Lens<StackActivityExtraState<F, T>, StackFragState<F, T>> = Lens(
        get = { extra -> extra.state },
        set = { extra, newStackState -> extra.copy(state = newStackState) }
    )

    fun <F, T : StackType<F>> stackTypeLens(): Lens<StackFragState<F, T>, T> = PLens(
        get = { stackState -> stackState.stack },
        set = { stackState, stack -> stackState.copy(stack = stack) }
    )

    fun <F> singleStackFGetter(stackFragment: StackFragment?): ReaderT<EitherPartialOf<Fail>, StackType.Single<F>, SingleTarget<F>> =
        ReaderT { singleStack ->
            val target = if (stackFragment == null)
                singleStack.list.withIndex().lastOrNull()
            else
                singleStack.list.withIndex().firstOrNull { it.value.hashTag == stackFragment.controller.hashTag }

            when {
                target == null -> Fail("Can not find target fragment: target=$stackFragment, but stack is ${singleStack.list.joinToString { it.toString() }}").left()
                target.index == 0 -> SingleTarget(null, target.value).right()
                else -> SingleTarget(singleStack.list[target.index - 1], target.value).right()
            }
        }

    fun <F> tableStackFGetter(stackFragment: StackFragment?): ReaderT<EitherPartialOf<Fail>, StackType.Table<F>, TableTarget<F>> =
        ReaderT { tableStack ->
            fun <K, V> findAtMapList(map: Map<K, List<V>>, checker: (V) -> Boolean): Tuple3<K, V?, V>? {
                for ((k, list) in map) {
                    val result = list.withIndex().firstOrNull { checker(it.value) }
                    if (result != null)
                        return Tuple3(k,
                            if (result.index != 0) list.getOrNull(result.index - 1) else null,
                            result.value)
                }
                return null
            }

            val target = when {
                stackFragment != null -> findAtMapList(tableStack.table) { it.hashTag == stackFragment.controller.hashTag }
                        ?.let { TableTarget(it.a, it.b, it.c) }
                tableStack.current != null -> {
                    val tag = tableStack.current.first
                    val targetList = tableStack.table[tag]
                    val target = targetList?.lastOrNull()
                    if (targetList != null && target != null)
                        TableTarget<F>(tag,
                            if (targetList.size > 1) targetList.getOrNull(targetList.size - 2) else null,
                            target
                        )
                    else null
                }
                else -> null
            }

            target?.right() ?: Fail("Can not find target fragment: target=$stackFragment, but stack is ${tableStack.table}").left()
        }


    fun <F : Fragment, T : StackType<F>> stackFragActivityOrNull(host: StackHost<F, T>): YRoute<ActivitiesState, StackActivityExtraState<F, T>?> =
        routeF { vd, cxt ->
            IO {
                val item = vd.list.firstOrNull { it.hashTag == host.controller.hashTag }
                val extra = item?.getStackExtraOrDefault(host)

                if (extra != null) {
                    val stackActivityData = item as? StackActivityExtraState<F, T>

                    vd.putStackExtraToActState(host, extra) toT Success(stackActivityData)
                } else vd toT Success(null)
            }
        }

    fun <F : Fragment, T : StackType<F>> stackFragActivity(host: StackHost<F, T>): YRoute<ActivitiesState, StackActivityExtraState<F, T>> =
        routeF { vd, cxt ->
            IO {
                val item = vd.list.firstOrNull { it.hashTag == host.controller.hashTag }
                val extra = item?.getStackExtra()

                if (item != null) {
                    if (extra == null && item.activity is FragmentActivity) {
                        val initExtraState = StackActivityExtraState<F, T>(
                            activity = item.activity as FragmentActivity,
                            state = StackFragState(
                                host = host,
                                stack = host.initStack,
                                fm = item.activity.supportFragmentManager
                            )
                        )

                        vd.copy(list = vd.list.map {
                            if (it.hashTag == host.controller.hashTag)
                                it.putStackExtra(initExtraState)
                            else it
                        }) toT Success(initExtraState)
                    } else if (extra != null) {
                        vd toT Success(extra as StackActivityExtraState<F, T>)
                    } else {
                        vd toT Fail("Find item but not target activity is not a FragmentActivity.")
                    }
                } else vd toT Fail(
                    "fragmentActivity | FragmentActivity not find: " +
                            "target=$host, but activity list=${vd.list.joinToString { it.activity.toString() }}"
                )
            }
        }

    fun <F, T : StackType<F>, A> startStackFragActivity(builder: ActivityBuilder<A>): YRoute<ActivitiesState, A>
            where F : Fragment, A : FragmentActivity, A : StackHost<F, T> =
        ActivitiesRoute.createActivityIntent<A, ActivitiesState>(builder)
            .transform { vd, cxt, intent ->
                binding {
                    val top: Context = vd.list.lastOrNull()?.activity ?: cxt.app
                    val anim = builder.animData
                    !IO {
                        top.startActivity(intent)
                        if (anim != null && top is Activity)
                            top.overridePendingTransition(anim.enterAnim, anim.enterStayAnimForActivity)
                        else (top as? Activity)?.overridePendingTransition(0, 0)
                    }

                    val (act) = cxt.bindNextActivity()
                        .firstOrError().toIO()

                    if (act !is StackHost<*, *> || act !is ActivityLifecycleOwner) {
                        vd.copy(list = vd.list + ActivityData(act, CoreID.get(), animData = anim)) toT
                                YResult.fail("Stack Activity must implements `StackHost` and `ActivityLifecycleOwner` interface.")
                    } else if (cxt.checkComponentClass(intent, act)) {
                        val host = act as StackHost<F, T>
                        val hashTag = CoreID.get()
                        !IO { act.controller.hashTag = hashTag }

                        vd.copy(list = vd.list + ActivityData(
                            activity = act, hashTag = hashTag,
                            extra = emptyMap(), animData = builder.animData
                        ).putStackExtra(StackActivityExtraState(
                            activity = act as A,
                            state = StackFragState(
                                host = host,
                                stack = host.initStack,
                                fm = act.supportFragmentManager
                            )))) toT YResult.success<A>(act)
                    } else {
                        vd.copy(list = vd.list + ActivityData(act, CoreID.get(), animData = anim)) toT
                                YResult.fail("startActivity | start activity is Success, but can not get target activity: " +
                                        "target is ${intent.component?.className} but get is $act", null)
                    }
                }
            }

    fun <F> startFragment(builder: FragmentBuilder<F>): YRoute<StackFragState<F, StackType<F>>, F>
            where F : Fragment, F : StackFragment =
            createFragment<StackFragState<F, StackType<F>>, F>(builder).flatMapR { f ->
                foldStack<F, IO<YResult<F>>>(putFragAtSingle<F>(builder, f), putFragAtTable<F>(builder, f))
                        .mapInner(lens = stackTypeLens<F, StackType<F>>())
                        .stackTranIO()
            }

    fun <F> startFragmentForSingle(builder: FragmentBuilder<F>): YRoute<StackFragState<F, StackType.Single<F>>, F>
            where F : Fragment, F : StackFragment =
        createFragment<StackInnerState<StackType.Single<F>>, F>(builder)
            .flatMapR { putFragAtSingle<F>(builder, it) }
            .mapInner(lens = stackTypeLens<F, StackType.Single<F>>())
            .stackTranIO()

    fun <F> startFragmentForTable(builder: FragmentBuilder<F>): YRoute<StackFragState<F, StackType.Table<F>>, F>
            where F : Fragment, F : StackFragment =
        createFragment<StackInnerState<StackType.Table<F>>, F>(builder)
            .flatMapR { putFragAtTable<F>(builder, it) }
            .mapInner(lens = stackTypeLens<F, StackType.Table<F>>())
            .stackTranIO()

    fun <S, F> YRoute<S, F>.dealFragForResult(requestCode: Int): YRoute<S, F>
            where F : StackFragment =
            mapResult {
                it.controller.requestCode = requestCode
                it
            }

    fun <S, F> YRoute<S, F>.mapToRx(): YRoute<S, Single<Tuple2<Int, Bundle?>>>
            where F : StackFragment, F : FragmentLifecycleOwner {
        val requestCode: Int = Random.nextInt()

        return dealFragForResult(requestCode).mapResult { f ->
            f.bindFragmentLife().ofType(FragmentLifeEvent.PreSendFragmentResult::class.java)
                .filter { it.requestCode == requestCode }
                .firstOrError()
                .map { it.resultCode toT it.data }
        }
    }

    fun <F : Fragment> switchFragmentAtStackActivity(host: StackHost<F, StackType.Table<F>>, tag: TableTag): YRoute<ActivitiesState, F?> =
        switchStackAtTable<F>(tag)
                .mapInner(lens = stackTypeLens<F, StackType.Table<F>>())
                .stackTranIO<F, StackType.Table<F>, F?>() // YRoute<StackFragState<F, StackType.Table<F>>, F?>
                .runAtA(host)

    fun <S, F : Fragment> createFragment(builder: FragmentBuilder<F>): YRoute<S, F> =
        routeF { vd, cxt ->
            val intent = builder.createIntent(cxt)

            val clazzNameE: Either<YResult<F>, String> = intent.component?.className?.right()
                ?: YResult.fail<F>("Can not get fragment class name, from intent: $intent").left()

            val fragmentE = clazzNameE.flatMap { clazzName ->
                try {
                    val fragClazz = Class.forName(clazzName)
                    val fragmentInstance = fragClazz.newInstance() as F
                    fragmentInstance.arguments = intent.extras
                    builder.doForFragment(cxt, fragmentInstance)
                    if (fragmentInstance is StackFragment)
                        fragmentInstance.controller.fromIntent = intent
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
                is Either.Right -> IO.just(vd toT YResult.success(fragmentE.b))
                is Either.Left -> IO.just(vd toT fragmentE.a)
            }
        }

    data class StackInnerState<out S>(
        val state: S,
        val fragmentId: Int,
        val fm: FragmentManager,
        val ft: FragmentTransaction
    ) {
        fun <T> map(f: (S) -> T): StackInnerState<T> = StackInnerState(
            state = f(state), fragmentId = fragmentId, fm = fm, ft = ft
        )
    }

    fun <F, T : StackType<F>, R> YRoute<StackInnerState<StackFragState<F, T>>, R>.stackTran(): YRoute<StackFragState<F, T>, R> =
            mapResult { IO.just(YResult.success(it)) }.stackTranIO()

    fun <F, T : StackType<F>, R> YRoute<StackInnerState<StackFragState<F, T>>, IO<YResult<R>>>.stackTranIO(): YRoute<StackFragState<F, T>, R> =
        routeF { vd, cxt ->
            binding {
                val fm = vd.fm
                val ft = !IO { fm.beginTransaction() }
                val innerState = StackInnerState(vd, vd.host.fragmentId, fm, ft)

                val (newInnerState, resultIO) = !this@stackTranIO.runRoute(innerState, cxt)

                !IO { newInnerState.ft.routeExecFT() }

                val result = when (resultIO) {
                    is Fail -> resultIO
                    is Success -> !resultIO.t
                }

                newInnerState.state toT result
            }
        }

    fun <T> stackTranResult(result: YResult<T>): YResult<IO<YResult<T>>> =
            YResult.success(IO.just(result))

    fun <T> stackTranResult(IOs: Collection<IO<Unit>>, result: YResult<T>): YResult<IO<YResult<T>>> =
            YResult.success(IOs.combineAll(Unit.monoid()).map { result })

    fun <S, Sub, R> YRoute<StackInnerState<Sub>, R>.mapInner(
        type: TypeCheck<S> = type(), lens: Lens<S, Sub>): YRoute<StackInnerState<S>, R> =
        routeF { vd, cxt ->
            binding {
                val (newSVD, result) = !this@mapInner.runRoute(vd.map(lens::get), cxt)

                val newVD = lens.set(vd.state, newSVD.state)

                vd.copy(state = newVD) toT result
            }
        }

    //<editor-fold desc="Add Fragment Core func">
    fun <F> putFragAtSingle(builder: FragmentBuilder<F>, fragment: F): YRoute<StackInnerState<StackType.Single<F>>, IO<YResult<F>>>
            where F : Fragment, F : StackFragment {
        val animData = builder.animData
        return if (animData != null)
            putFragAtSingleWithAnim(animData, builder, fragment)
        else
            putFragAtSingleNoAnim(builder, fragment)
    }

    fun <F> putFragAtTable(builder: FragmentBuilder<F>, fragment: F): YRoute<StackInnerState<StackType.Table<F>>, IO<YResult<F>>>
            where F : Fragment, F : StackFragment {
        val animData = builder.animData
        return if (animData != null)
            putFragAtTableWithAnim(animData, builder, fragment)
        else
            putFragAtTableNoAnim(builder, fragment)
    }

    fun <F> putFragAtSingleNoAnim(builder: FragmentBuilder<F>, fragment: F): YRoute<StackInnerState<StackType.Single<F>>, IO<YResult<F>>>
            where F : Fragment, F : StackFragment =
            routeF { vd, cxt ->
                val newItem = FItem<F>(fragment, CoreID.get(), builder.fragmentTag, null)
                fragment.controller.hashTag = newItem.hashTag

                val stackState = vd.state
                val newStack = stackState.copy(list = stackState.list + newItem)

                IO {
                    val hideIOs = stackState.list.reversed()
                            .filter { it.t.isVisible }
                            .onEach { vd.ft.hide(it.t) }
                            .map { IO { it.t.onHide(OnHideMode.OnStartNew) } }
                    vd.ft.add(vd.fragmentId, fragment)
                    val cbIOs = hideIOs + IO { fragment.onShow(OnShowMode.OnCreate) }

                    vd.copy(state = newStack) toT
                            stackTranResult(cbIOs, Success(fragment))
                }
            }

    fun <F> putFragAtTableNoAnim(builder: FragmentBuilder<F>, fragment: F): YRoute<StackInnerState<StackType.Table<F>>, IO<YResult<F>>>
            where F : Fragment, F : StackFragment =
            routeF { vd, cxt ->
                val newItem = FItem<F>(fragment, CoreID.get(), builder.fragmentTag, null)
                fragment.controller.hashTag = newItem.hashTag

                val oldStackState = vd.state
                val stackTag = builder.stackTag

                val targetTag = stackTag ?: oldStackState.current?.first ?: oldStackState.defaultTag

                binding {
                    val innerState =
                            if (oldStackState.current == null || oldStackState.current.first != stackTag) {
                                val (state, beforeFrag) = !switchStackAtTable<F>(targetTag, true).runRoute(vd, cxt)
                                state
                            } else vd

                    val innerStackState = innerState.state
                    val targetStack = innerStackState.table[targetTag] ?: emptyList()
                    val newTable = innerState.state.table - targetTag + (targetTag to targetStack + newItem)
                    val backF = innerState.state.current?.second?.t

                    val cbIOs = !IO {
                        sequence {
                            vd.ft.add(vd.fragmentId, fragment)
                            yield(IO { fragment.onShow(OnShowMode.OnCreate) })
                            if (backF != null && backF.isVisible) {
                                vd.ft.hide(backF)
                                yield(IO { backF.onHide(OnHideMode.OnStartNew) })
                            }
                        }.toList()
                    }

                    innerState.copy(
                            state = innerState.state.copy(
                                    table = newTable,
                                    current = targetTag to newItem
                            )
                    ) toT stackTranResult(cbIOs, Success(fragment))
                }
            }

    fun <F> putFragAtSingleWithAnim(animData: AnimData, builder: FragmentBuilder<F>, fragment: F): YRoute<StackInnerState<StackType.Single<F>>, IO<YResult<F>>>
            where F : Fragment, F : StackFragment =
            routeF { vd, cxt ->
                val stackState = vd.state
                if (stackState.list.isEmpty())
                    return@routeF putFragAtSingleNoAnim(builder, fragment).runRoute(vd, cxt)

                val newItem = FItem<F>(fragment, CoreID.get(), builder.fragmentTag, animData)
                fragment.controller.hashTag = newItem.hashTag

                val newStack = stackState.copy(list = stackState.list + newItem)

                val backF = stackState.list.last()

                binding {
                    !vd.fm.putFragWithAnim(animData, vd.fragmentId, backF.t, fragment)

                    vd.copy(state = newStack) toT
                            Success(IO.just(YResult.success(fragment)))
                }
            }

    fun <F> putFragAtTableWithAnim(animData: AnimData, builder: FragmentBuilder<F>, fragment: F): YRoute<StackInnerState<StackType.Table<F>>, IO<YResult<F>>>
            where F : Fragment, F : StackFragment =
            routeF { vd, cxt ->
                val newItem = FItem<F>(fragment, CoreID.get(), builder.fragmentTag, animData)
                fragment.controller.hashTag = newItem.hashTag

                val oldStackState = vd.state
                val stackTag = builder.stackTag

                val targetTag = stackTag ?: oldStackState.current?.first ?: oldStackState.defaultTag

                binding {
                    val innerState =
                            if (oldStackState.current == null || oldStackState.current.first != stackTag) {
                                val (state, beforeFrag) = !switchStackAtTable<F>(targetTag, true).runRoute(vd, cxt)
                                state
                            } else vd

                    val innerStackState = innerState.state
                    val targetStack = innerStackState.table[targetTag] ?: emptyList()
                    val newTable = innerState.state.table - targetTag + (targetTag to targetStack + newItem)
                    val backF = innerState.state.current?.second?.t

                    val result = if (backF != null) {
                        !vd.fm.putFragWithAnim(animData, vd.fragmentId, backF, fragment)
                        stackTranResult(Success(fragment))
                    } else {
                        !IO { vd.ft.add(vd.fragmentId, fragment) }
                        stackTranResult(listOf(IO { fragment.onShow(OnShowMode.OnCreate) }),
                                Success(fragment))
                    }

                    innerState.copy(
                            state = innerState.state.copy(
                                    table = newTable,
                                    current = targetTag to newItem
                            )
                    ) toT result
                }
            }

    fun <F> FragmentManager.putFragWithAnim(animData: AnimData, fragmentId: Int,
                                            backF: F, targetF: F): IO<Unit> where F : Fragment, F : StackFragment = binding {
        !trans {
            show(backF)
            add(fragmentId, targetF)
        }

        val viewEvent = !targetF.bindFragmentLife()
                .ofType(FragmentLifeEvent.OnViewCreated::class.java)
                .firstOrError().toIO()

        backF.onHide(OnHideMode.OnStartNew)
        targetF.onShow(OnShowMode.OnCreate)
        !startAnim(animData.enterAnim, viewEvent.view).toIO()

        !trans { hide(backF) }
        backF.onHide(OnHideMode.OnStartNewAfterAnim)
        targetF.onShow(OnShowMode.OnCreateAfterAnim)
    }
    //</editor-fold>

    fun <F> switchStackAtTable(targetTag: TableTag, silentSwitch: Boolean = false): YRoute<StackInnerState<StackType.Table<F>>, IO<YResult<F?>>> where F : Fragment =
        routeF { vd, cxt ->
            val stackState = vd.state
            val currentTag = stackState.current?.first

            if (targetTag == currentTag) {
                IO.just(vd toT stackTranResult(Success(stackState.table[currentTag]?.lastOrNull()?.t)))
            } else binding {
                val hideIOs = if (currentTag != null) {
                    val currentStack = stackState.table[currentTag] ?: emptyList()

                    !IO { sequence {
                        for (i in currentStack.size - 1 downTo 0) {
                            val f = currentStack[i]
                            if (f.t.isVisible) {
                                vd.ft.hide(f.t)
                                if (f.t is StackFragment) yield(IO { f.t.onHide(OnHideMode.OnSwitch) })
                            }
                        }
                    }.toList() }
                } else emptyList()

                val targetStack = stackState.table[targetTag] ?: emptyList()
                if (targetStack.isEmpty()) {
                    // 如果准备切换到的目标堆栈为空的话，并且配置有默认Fragment的话, 切换到目标Stack并启动默认Fragment
                    val defaultFragClazz: Class<out F>? = stackState.defaultMap[targetTag]

                    if (defaultFragClazz != null) {
                        val (_, result) = !createFragment<Unit, F>(FragmentBuilder(defaultFragClazz).withStackTag(targetTag))
                            .runRoute(Unit, cxt)

                        when (result) {
                            is Success -> !IO {
                                val cbIOs = hideIOs + sequence {
                                    vd.ft.add(vd.fragmentId, result.t)
                                    if (result.t is StackFragment) yield(IO { result.t.onShow(OnShowMode.OnCreate) })
                                    if (silentSwitch) vd.ft.hide(result.t)
                                }

                                val newItem = FItem<F>(result.t, CoreID.get(), targetTag, null)
                                vd.copy(
                                    state = vd.state.copy(
                                        table = stackState.table + (targetTag to targetStack + newItem),
                                        current = targetTag to newItem
                                    )
                                ) toT stackTranResult(cbIOs, Success(result.t))
                            }
                            is Fail -> vd toT stackTranResult(hideIOs, result)
                        }
                    } else vd.copy(state = vd.state.copy(current = targetTag to null)) toT stackTranResult(hideIOs, Success(null))
                } else !IO {
                    val item = targetStack.last()

                    val cbIOs = hideIOs + sequence {
                        if (!silentSwitch) {
                            vd.ft.show(item.t)
                            if (item.t is StackFragment) yield(IO { item.t.onShow(OnShowMode.OnSwitch) })
                        }
                    }

                    vd.copy(state = vd.state.copy(current = targetTag to item)) toT stackTranResult(cbIOs, Success(item.t))
                }
            }
        }

    fun <F : Fragment, R> foldStack(
        single: YRoute<StackInnerState<StackType.Single<F>>, R>,
        table: YRoute<StackInnerState<StackType.Table<F>>, R>
    ): YRoute<StackInnerState<StackType<F>>, R> = routeF { state, cxt ->
        val stack = state.state
        @Suppress("UNCHECKED_CAST")
        when(stack) {
            is StackType.Single<F> -> single.runRoute(state as StackInnerState<StackType.Single<F>>, cxt)
            is StackType.Table<F> -> table.runRoute(state as StackInnerState<StackType.Table<F>>, cxt)
        }
    }

    fun <F : Fragment, R> foldForFragState(
            single: YRoute<StackFragState<F, StackType.Single<F>>, R>,
            table: YRoute<StackFragState<F, StackType.Table<F>>, R>
    ): YRoute<StackFragState<F, StackType<F>>, R> = routeF { state, cxt ->
        val stack = state.stack
        @Suppress("UNCHECKED_CAST")
        when(stack) {
            is StackType.Single<F> -> single.runRoute(state as StackFragState<F, StackType.Single<F>>, cxt)
            is StackType.Table<F> -> table.runRoute(state as StackFragState<F, StackType.Table<F>>, cxt)
        }
    }

    fun <F, T> dealFinishResultForActivity(activity: Activity, finishResult: FinishResult): YRoute<ActivitiesState, Unit>
            where F : Fragment, T : StackType<F> =
        when (finishResult) {
            FinishResult.FinishOver -> routeId()
            FinishResult.FinishParent -> ActivitiesRoute.findTargetActivityItem(activity)
                .resultNonNull("findTargetActivityItem at dealFinishResultForActivity()")
                .flatMapR { data -> ActivitiesRoute.finishTargetActivity(data) }
        }

    fun <F> dealFinishForResult(finishF: F, backF: F?): IO<Unit> where F : StackFragment = IO {
        finishF.controller.apply {
            if (requestCode != -1) {
                finishF.preSendFragmentResult(requestCode, resultCode, resultData)
                backF?.onFragmentResult(requestCode, resultCode, resultData)
            }
        }
        Unit
    }

    fun <F> finishFragmentForSingle(target: StackFragment?): YRoute<StackFragState<F, StackType.Single<F>>, Tuple2<SingleTarget<F>?, FinishResult>>
            where F : Fragment, F : StackFragment =
        finishFragmentAtSingle<F>(target).mapInner(lens = stackTypeLens<F, StackType.Single<F>>()).stackTranIO()

    fun <F> finishFragmentForTable(target: StackFragment?): YRoute<StackFragState<F, StackType.Table<F>>, Tuple2<TableTarget<F>?, FinishResult>>
            where F : Fragment, F : StackFragment =
        finishFragmentAtTable<F>(target).mapInner(lens = stackTypeLens<F, StackType.Table<F>>()).stackTranIO()

    //<editor-fold defaultstate="collapsed" desc="Normal useless">
    fun <F: Fragment> finishFragmentAtSingleNoAnim(targetF: StackFragment?)
            : YRoute<StackInnerState<StackType.Single<F>>, IO<YResult<Tuple2<SingleTarget<F>?, FinishResult>>>> =
        routeF { state, cxt ->
            val stack = state.state

            val result = singleStackFGetter<F>(targetF).run(stack).fix()

            val target = when (result) {
                is Either.Left -> return@routeF when {
                    targetF == null -> IO.just(state toT stackTranResult(Success(null toT FinishResult.FinishParent)))
                    else -> IO.just(state toT stackTranResult(result.a))
                }
                is Either.Right -> result.b
            }
            val (backItem, targetItem) = target

            binding {
                val newState =
                    state.copy(state = stack.copy(list = stack.list.filter { it.hashTag != targetItem.hashTag }))

                val cbIOs = !IO { sequence {
                    state.ft.remove(targetItem.t)
                    if (backItem != null) {
                        state.ft.show(backItem.t)
                        if (backItem.t is StackFragment) yield(IO { backItem.t.onShow(OnShowMode.OnBack) })
                    }
                }.toList() }

                if (targetItem.t is StackFragment && backItem != null && backItem.t is StackFragment)
                    !dealFinishForResult(targetItem.t, backItem.t)

                newState toT stackTranResult(cbIOs, Success(
                        target toT when {
                            newState.state.list.isEmpty() -> FinishResult.FinishParent
                            else -> FinishResult.FinishOver
                        }))
            }
        }

    fun <F : Fragment> finishFragmentAtTableNoAnim(target: StackFragment?)
            : YRoute<StackInnerState<StackType.Table<F>>, IO<YResult<Tuple2<TableTarget<F>?, FinishResult>>>> =
            routeF { state, cxt ->
                val stack = state.state

                val result = tableStackFGetter<F>(target).run(stack).fix()
                println("finishFragmentAtSingle>>> result: ${result}")

                val targetItem = when (result) {
                    is Either.Left -> return@routeF when {
                        target == null -> IO.just(state toT stackTranResult(Success(null toT FinishResult.FinishParent)))
                        else -> IO.just(state toT stackTranResult(result.a))
                    }
                    is Either.Right -> result.b
                }
                val (targetTag, backF, targetF) = targetItem

                binding {
                    val newState = state.copy(state = stack.copy(
                            table = stack.table + (targetTag to (stack.table[targetTag]?.filter { it.hashTag != targetF.hashTag }
                                    ?: emptyList())),
                            current = if (stack.current?.second?.hashTag == targetF.hashTag) backF?.let { targetTag to it } else stack.current))

                    val cbIOs = !IO { sequence {
                        state.ft.remove(targetF.t)
                        if (backF != null) {
                            state.ft.show(backF.t)
                            if (backF.t is StackFragment) yield(IO { backF.t.onShow(OnShowMode.OnBack) })
                        }
                    }.toList() }

                    if (targetF.t is StackFragment && backF != null && backF.t is StackFragment)
                        !dealFinishForResult(targetF.t, backF.t)

                    newState toT stackTranResult(cbIOs, Success(
                            targetItem toT if (newState.state.table[targetTag].isNullOrEmpty()) FinishResult.FinishParent
                            else FinishResult.FinishOver
                    ))
                }
            }
    //</editor-fold>

    fun <F> finishFragmentAtSingle(targetF: StackFragment?): YRoute<StackInnerState<StackType.Single<F>>, IO<YResult<Tuple2<SingleTarget<F>?, FinishResult>>>>
            where F : Fragment, F : StackFragment =
            routeF { state, cxt ->
                val stack = state.state

                val result = singleStackFGetter<F>(targetF).run(stack).fix()

                val target = when (result) {
                    is Either.Left -> return@routeF when {
                        targetF == null -> IO.just(state toT stackTranResult(Success(null toT FinishResult.FinishParent)))
                        else -> IO.just(state toT stackTranResult(result.a))
                    }
                    is Either.Right -> result.b
                }
                val (backItem, targetItem) = target
                Logger.d("finishFragmentAtSingle", "target=$target")

                if (backItem == null) {
                    // Do not finish top fragment
                    return@routeF IO.just(state toT stackTranResult(Success(target toT FinishResult.FinishParent)))
                }

                binding {
                    val newState =
                            state.copy(state = stack.copy(list = stack.list.filter { it.hashTag != targetItem.hashTag }))

                    !dealFinishForResult(targetItem.t, backItem.t)

                    val animData = targetItem.anim
                    val cbIOs = if (animData == null) {
                        // NoAnim
                        !IO {
                            state.ft.remove(targetItem.t)
                            state.ft.show(backItem.t)
                        }
                        listOf(IO { backItem.t.onShow(OnShowMode.OnBack) })
                    } else {
                        !state.fm.popFragWithAnim(animData, backItem.t, targetItem.t)
                        emptyList()
                    }

                    newState toT stackTranResult(cbIOs, Success(
                            target toT when {
                                newState.state.list.isEmpty() -> FinishResult.FinishParent
                                else -> FinishResult.FinishOver
                            }
                    ))
                }
            }

    fun <F> finishFragmentAtTable(target: StackFragment?): YRoute<StackInnerState<StackType.Table<F>>, IO<YResult<Tuple2<TableTarget<F>?, FinishResult>>>>
            where F : Fragment, F : StackFragment =
            routeF { state, cxt ->
                val stack = state.state

                val result = tableStackFGetter<F>(target).run(stack).fix()
                println("finishFragmentAtSingle>>> result: ${result}")

                val targetItem = when (result) {
                    is Either.Left -> return@routeF when {
                        target == null -> IO.just(state toT stackTranResult(Success(null toT FinishResult.FinishParent)))
                        else -> IO.just(state toT stackTranResult(result.a))
                    }
                    is Either.Right -> result.b
                }
                val (targetTag, backF, targetF) = targetItem

                if (backF == null) {
                    // Do not finish top fragment
                    return@routeF IO.just(state toT stackTranResult(Success(null toT FinishResult.FinishParent)))
                }

                binding {
                    val newState = state.copy(state = stack.copy(
                            table = stack.table + (targetTag to (stack.table[targetTag]?.filter { it.hashTag != targetF.hashTag }
                                    ?: emptyList())),
                            current = if (stack.current?.second?.hashTag == targetF.hashTag) backF?.let { targetTag to it } else stack.current))

                    !dealFinishForResult(targetF.t, backF.t)

                    val animData = targetF.anim
                    val cbIOs = if (animData == null) {
                        // NoAnim
                        !IO {
                            state.ft.remove(targetF.t)
                            state.ft.show(backF.t)
                        }
                        listOf(IO { backF.t.onShow(OnShowMode.OnBack) })
                    } else {
                        !state.fm.popFragWithAnim(animData, backF.t, targetF.t)
                        emptyList()
                    }

                    newState toT stackTranResult(cbIOs, Success(
                            targetItem toT if (newState.state.table[targetTag].isNullOrEmpty()) FinishResult.FinishParent
                            else FinishResult.FinishOver
                    ))
                }
            }

    fun <F> FragmentManager.popFragWithAnim(animData: AnimData,
                                            backF: F, targetF: F): IO<Unit> where F : Fragment, F : StackFragment = binding {
        !trans { show(backF) }

        !startAnim(animData.exitAnim, targetF.view).toIO()

        !trans { remove(targetF) }
        backF.onShow(OnShowMode.OnBack)
    }

    fun <F> clearCurrentStackForSingle(): YRoute<StackInnerState<StackType.Single<F>>, IO<YResult<Boolean>>>
            where F : Fragment =
            routeF { state, cxt ->
                val stack = state.state
                if (stack.list.isEmpty()) return@routeF IO.just(state toT stackTranResult(Success(false)))

                val newState = state.copy(state = stack.copy(list = emptyList()))

                binding {
                    !IO {
                        for (item in stack.list.reversed())
                            state.ft.remove(item.t)
                    }

                    newState toT stackTranResult(Success(true))
                }
            }

    fun <F> clearCurrentStackForTable(resetStack: Boolean = false): YRoute<StackInnerState<StackType.Table<F>>, IO<YResult<Boolean>>>
            where F : Fragment =
            routeF { state, cxt ->
                val stack = state.state
                val currentTag = stack.current?.first ?: return@routeF IO.just(state toT stackTranResult(Success(false)))

                binding {
                    !IO {
                        for (item in stack.table[currentTag]?.reversed() ?: emptyList())
                            state.ft.remove(item.t)
                    }

                    val clearStackState = state.copy(state = stack.copy(
                            table = stack.table + (currentTag to emptyList()),
                            current = currentTag to null))

                    if (resetStack) {
                        val defaultFragClazz: Class<out F>? = stack.defaultMap[currentTag]

                        if (defaultFragClazz != null) {
                            val (_, result) = !createFragment<Unit, F>(FragmentBuilder(defaultFragClazz).withStackTag(currentTag))
                                    .runRoute(Unit, cxt)

                            when (result) {
                                is Success -> !IO {
                                    val cbIOs = sequence {
                                        state.ft.add(state.fragmentId, result.t)
                                        if (result.t is StackFragment) yield(IO { result.t.onShow(OnShowMode.OnCreate) })
                                    }.toList()

                                    val newItem = FItem<F>(result.t, CoreID.get(), currentTag, null)
                                    state.copy(state = stack.copy(
                                            table = stack.table + (currentTag to listOf(newItem)),
                                            current = currentTag to newItem)) toT stackTranResult(cbIOs, Success(true))
                                }
                                is Fail -> clearStackState toT stackTranResult(result)
                            }
                        } else clearStackState toT stackTranResult(Fail("Can not reset stack: " +
                                "do not have default fragment class for tag=$currentTag at map=${stack.defaultMap}"))
                    } else clearStackState toT stackTranResult(Success(true))
                }
            }

    fun <F> backToTopForSingle(): YRoute<StackInnerState<StackType.Single<F>>, IO<YResult<Boolean>>>
            where F : Fragment =
            routeF { state, cxt ->
                val stack = state.state
                if (stack.list.size <= 1) return@routeF IO.just(state toT stackTranResult(Success(false)))

                binding {
                    val topF = stack.list.first()
                    val cbIOs = !IO { sequence {
                        for (item in stack.list.drop(1).reversed())
                            state.ft.remove(item.t)
                        state.ft.show(topF.t)
                        if (topF.t is StackFragment) yield(IO { topF.t.onShow(OnShowMode.OnBack) })
                    }.toList() }

                    val topBackF = stack.list.getOrNull(1)?.t
                    if (topBackF != null && topBackF is StackFragment && topF.t is StackFragment)
                        !dealFinishForResult(topBackF, topF.t)

                    state.copy(state = stack.copy(listOf(topF))) toT stackTranResult(cbIOs, Success(true))
                }
            }

    fun <F> backToTopForTable(): YRoute<StackInnerState<StackType.Table<F>>, IO<YResult<Boolean>>>
            where F : Fragment =
            routeF { state, cxt ->
                val stack = state.state
                val currentTag = stack.current?.first
                val targetList = stack.table[currentTag]

                if (currentTag == null || targetList.isNullOrEmpty())
                    return@routeF IO.just(state toT stackTranResult(Success(false)))

                binding {
                    val topF = targetList.first()
                    val cbIOs = !IO { sequence {
                        for (item in targetList.drop(1).reversed())
                            state.ft.remove(item.t)
                        state.ft.show(topF.t)
                        if (topF.t is StackFragment) yield(IO { topF.t.onShow(OnShowMode.OnBack) })
                    }.toList() }

                    val topBackF = targetList.getOrNull(1)?.t
                    if (topBackF != null && topBackF is StackFragment && topF.t is StackFragment)
                        !dealFinishForResult(topBackF, topF.t)

                    state.copy(state = stack.copy(
                            table = stack.table + (currentTag to listOf(topF)),
                            current = currentTag to topF)) toT stackTranResult(cbIOs, Success(true))
                }
            }

    fun <F, A, T> YRoute<ActivitiesState, A>.startFragAtNewActivity(fragBuilder: FragmentBuilder<F>)
            : YRoute<ActivitiesState, Tuple2<A, F>>
            where F : Fragment, F : StackFragment, T : StackType<F>, A : FragmentActivity, A : StackHost<F, T> =
            flatMapR { activity: A ->
                val stack = activity.initStack
                when (stack) {
                    is StackType.Single<*> -> startFragmentForSingle(fragBuilder)
                            .runAtA(activity as StackHost<F, StackType.Single<F>>)
                            .mapResult { f -> (activity toT f) as Tuple2<A, F> }
                    is StackType.Table<*> -> startFragmentForTable(fragBuilder)
                            .runAtA(activity as StackHost<F, StackType.Table<F>>)
                            .mapResult { f -> (activity toT f) as Tuple2<A, F> }
                    else -> routeFail("Unreachable")
                }
            }

    fun <F> getTopOfStackForSingle(): YRoute<StackFragState<F, StackType.Single<F>>, F?> = routeF { s, routeCxt ->
        IO.just(s toT Success(s.stack.list.lastOrNull()?.t))
    }

    fun <F> getTopOfStackForTable(): YRoute<StackFragState<F, StackType.Table<F>>, F?> = routeF { s, routeCxt ->
        IO.just(s toT Success(s.stack.table[s.stack.current?.first]?.lastOrNull()?.t))
    }
    //</editor-fold>

    fun <F, T, A> routeStartStackActivity(builder: ActivityBuilder<A>)
            : YRoute<ActivitiesState, A>
            where F : Fragment, T : StackType<F>, A : FragmentActivity, A : StackHost<F, T> =
            startStackFragActivity(builder)

    fun <F, T> routeGetStackFromActivity(host: StackHost<F, T>)
            : YRoute<ActivitiesState, StackFragState<F, T>>
            where F : Fragment, T : StackType<F> =
            routeF { actState, routeCxt ->
                binding {
                    val (newState, lens) = !routeGetStackLensFromActivity(host).runRoute(actState, routeCxt)

                    when (lens) {
                        is Fail -> newState toT lens
                        is Success -> {
                            val target = lens.t.get(actState)

                            newState toT
                                    if (target != null) Success(target) else Fail("Can not found stack at target activity: $host")
                        }
                    }
                }
            }

    fun <F, T> routeGetStackLensFromActivity(host: StackHost<F, T>)
            : YRoute<ActivitiesState, Lens<ActivitiesState, StackFragState<F, T>?>>
            where F : Fragment, T : StackType<F> =
            routeF { actState, routeCxt ->
                val extraLens = stackActivityLens(host)
                val fragStateLens = stackStateForActivityLens<F, T>()
                val lens = extraLens.composeNonNull(fragStateLens)

                val extra = extraLens.get(actState)

                if (extra == null) {
                    val item = actState.list.firstOrNull { it.activity === host || it.hashTag == host.controller.hashTag }
                    val defaultExtra = item?.getDefaultStackExtra(host)
                    if (defaultExtra != null)
                        return@routeF IO.just(actState.putStackExtraToActState(host, defaultExtra) toT Success(lens))
                }

                IO.just(actState toT Success(lens))
            }

    fun <F, T> routeGetStackLensFromFrag(frag: Fragment)
            : YRoute<ActivitiesState, Lens<ActivitiesState, StackFragState<F, T>?>>
            where F : Fragment, T : StackType<F> =
            routeF { actState, routeCxt ->
                val activity = frag.activity
                if (activity == null) {
                    IO.just(actState toT Fail("Can not find parent stack activity: frag=$frag, parent=$activity"))
                } else if (activity !is StackHost<*, *>) {
                    IO.just(actState toT Fail("Parent activity must be implements `StackHost` interface: frag=$frag, parent=$activity"))
                } else {
                    @Suppress("UNCHECKED_CAST")
                    val host = activity as StackHost<F, T>
                    routeGetStackLensFromActivity(host).runRoute(actState, routeCxt)
                }
            }

    fun <F, T> routeGetStackFromFrag(frag: Fragment)
            : YRoute<ActivitiesState, StackHost<F, T>?>
            where F : Fragment, T : StackType<F> =
            routeGetStackLensFromFrag<F, T>(frag).flatMapR { lens ->
                routeFromState<ActivitiesState, StackHost<F, T>?> { s ->
                    lens.get(s)?.host
                }
            }

    fun <F, R> routeRunAtFrag(frag: Fragment, route: YRoute<StackFragState<F, StackType<F>>, R>): YRoute<ActivitiesState, R>
            where F : Fragment =
            routeGetStackLensFromFrag<F, StackType<F>>(frag).composeState(route.stateNullable("routeRunAtFrag"))

    infix fun <F, R> YRoute<StackFragState<F, StackType<F>>, R>.runAtF(frag: Fragment): YRoute<ActivitiesState, R>
            where F : Fragment =
            routeRunAtFrag(frag, this)

    fun <F, T, R> routeRunAtAct(host: StackHost<F, T>, route: YRoute<StackFragState<F, T>, R>): YRoute<ActivitiesState, R>
            where F : Fragment, T : StackType<F> =
            routeGetStackLensFromActivity(host).composeState(route.stateNullable("routeRunAtFrag"))

    infix fun <F, T, R> YRoute<StackFragState<F, T>, R>.runAtA(host: StackHost<F, T>): YRoute<ActivitiesState, R>
            where F : Fragment, T : StackType<F> =
            routeRunAtAct(host, this)

    infix fun <F, A, T> YRoute<StackFragState<F, T>, Tuple2<F?, FinishResult>>.runAtADealFinish(act: A): YRoute<ActivitiesState, FinishResult>
            where F : Fragment, F : StackFragment, T : StackType<F>, A : FragmentActivity, A : StackHost<F, T> =
            runAtADealFinish(act, act)

    fun <F, T> YRoute<StackFragState<F, T>, Tuple2<F?, FinishResult>>.runAtADealFinish(
            act: FragmentActivity, host: StackHost<F, T>): YRoute<ActivitiesState, FinishResult>
            where F : Fragment, F : StackFragment, T : StackType<F> =
            routeRunAtAct(host, this).flatMapR { (lastF, result) ->
                when (result) {
                    FinishResult.FinishOver -> routeId<ActivitiesState>()
                    FinishResult.FinishParent ->
                        (foldForFragState<F, Boolean>(
                                single = routeFromState { s ->
                                    s.stack.list.foldRight(false) { i, b -> b || i.t.onBackPressed() }
                                            || host.onBackPressed(s.stack.list.size)
                                },
                                table = routeFromState { s ->
                                    val currentTag = s.stack.current?.first
                                    val currentStack = s.stack.table[currentTag]

                                    (if (currentTag != null && !currentStack.isNullOrEmpty())
                                        currentStack.foldRight(false) { i, b -> b || i.t.onBackPressed() }
                                    else false) || host.onBackPressed(currentStack?.size ?: 0)
                                }
                        ) runAtA host).flatMapR { isNotExecDefault ->
                            if (!isNotExecDefault) {
                                (if (lastF != null && lastF.requestCode != -1) {
                                    act.setResult(lastF.resultCode, lastF.resultData?.let { Intent().putExtras(it) })
                                    routeFromIO<ActivitiesState, Unit>(dealFinishForResult(lastF, null))
                                } else routeId()).andThen(ActivitiesRoute.routeFinish(act))
                            } else routeId<ActivitiesState>()
                        }
                }.mapResult { result }
            }

    fun <F> routeStartFragmentAtSingle(builder: FragmentBuilder<F>): YRoute<StackFragState<F, StackType.Single<F>>, F>
            where F : Fragment, F : StackFragment =
            startFragmentForSingle(builder)

    fun <F> routeStartFragmentAtTable(builder: FragmentBuilder<F>): YRoute<StackFragState<F, StackType.Table<F>>, F>
            where F : Fragment, F : StackFragment =
            startFragmentForTable(builder)

    fun <F> routeStartFragment(builder: FragmentBuilder<F>): YRoute<StackFragState<F, StackType<F>>, F>
            where F : Fragment, F : StackFragment =
            startFragment(builder)

    fun <F> routeStartFragmentForResultAtSingle(builder: FragmentBuilder<F>, requestCode: Int): YRoute<StackFragState<F, StackType.Single<F>>, F>
            where F : Fragment, F : StackFragment =
            startFragmentForSingle(builder).dealFragForResult(requestCode)

    fun <F> routeStartFragmentForResultAtTable(builder: FragmentBuilder<F>, requestCode: Int): YRoute<StackFragState<F, StackType.Table<F>>, F>
            where F : Fragment, F : StackFragment =
            startFragmentForTable(builder).dealFragForResult(requestCode)

    fun <F> routeStartFragmentForResult(builder: FragmentBuilder<F>, requestCode: Int): YRoute<StackFragState<F, StackType<F>>, F>
            where F : Fragment, F : StackFragment =
            foldForFragState(
                    routeStartFragmentForResultAtSingle(builder, requestCode),
                    routeStartFragmentForResultAtTable(builder, requestCode))

    fun <F> routeStartFragmentForRxAtSingle(builder: FragmentBuilder<F>): YRoute<StackFragState<F, StackType.Single<F>>, Single<Tuple2<Int, Bundle?>>>
            where F : Fragment, F : StackFragment, F : FragmentLifecycleOwner =
            startFragmentForSingle(builder).mapToRx()

    fun <F> routeStartFragmentForRxAtTable(builder: FragmentBuilder<F>): YRoute<StackFragState<F, StackType.Table<F>>, Single<Tuple2<Int, Bundle?>>>
            where F : Fragment, F : StackFragment, F : FragmentLifecycleOwner =
            startFragmentForTable(builder).mapToRx()

    fun <F> routeStartFragmentForRx(builder: FragmentBuilder<F>): YRoute<StackFragState<F, StackType<F>>, Single<Tuple2<Int, Bundle?>>>
            where F : Fragment, F : StackFragment, F : FragmentLifecycleOwner =
            foldForFragState(
                    routeStartFragmentForRxAtSingle(builder),
                    routeStartFragmentForRxAtTable(builder))

    fun <F> routeSwitchTag(tag: TableTag): YRoute<StackFragState<F, StackType.Table<F>>, F?> where F : Fragment =
            switchStackAtTable<F>(tag).mapInner(lens = stackTypeLens<F, StackType.Table<F>>()).stackTranIO()

    fun <F> routeClearCurrentStackForSingle(): YRoute<StackFragState<F, StackType.Single<F>>, Boolean>
            where F : Fragment =
            clearCurrentStackForSingle<F>().mapInner(lens = stackTypeLens<F, StackType.Single<F>>()).stackTranIO()

    fun <F> routeClearCurrentStackForTable(resetStack: Boolean = false): YRoute<StackFragState<F, StackType.Table<F>>, Boolean>
            where F : Fragment =
            clearCurrentStackForTable<F>(resetStack).mapInner(lens = stackTypeLens<F, StackType.Table<F>>()).stackTranIO()

    fun <F> routeBackToTopForSingle(): YRoute<StackFragState<F, StackType.Single<F>>, Boolean>
            where F : Fragment =
            backToTopForSingle<F>().mapInner(lens = stackTypeLens<F, StackType.Single<F>>()).stackTranIO()

    fun <F> routeBackToTopForTable(): YRoute<StackFragState<F, StackType.Table<F>>, Boolean>
            where F : Fragment =
            backToTopForTable<F>().mapInner(lens = stackTypeLens<F, StackType.Table<F>>()).stackTranIO()

    //<editor-fold defaultstate="collapsed" desc="Normal useless">
    fun <F : Fragment> routeFinishFragmentAtSingleNoAnim(target: StackFragment?): YRoute<StackFragState<F, StackType.Single<F>>, Tuple2<SingleTarget<F>?, FinishResult>> =
            finishFragmentAtSingleNoAnim<F>(target).mapInner(lens = stackTypeLens<F, StackType.Single<F>>()).stackTranIO()

    fun <F : Fragment> routeFinishFragmentAtTableNoAnim(target: StackFragment?): YRoute<StackFragState<F, StackType.Table<F>>, Tuple2<TableTarget<F>?, FinishResult>> =
            finishFragmentAtTableNoAnim<F>(target).mapInner(lens = stackTypeLens<F, StackType.Table<F>>()).stackTranIO()

    fun <F : Fragment> routeFinishFragmentNoAnim(target: StackFragment?): YRoute<StackFragState<F, StackType<F>>, Tuple2<Either<SingleTarget<F>, TableTarget<F>>, FinishResult>> =
            foldForFragState(
                    routeFinishFragmentAtSingleNoAnim<F>(target)
                            .mapResult { (it.a.left() as Either<SingleTarget<F>, TableTarget<F>>) toT it.b },
                    routeFinishFragmentAtTableNoAnim<F>(target)
                            .mapResult { (it.a.right() as Either<SingleTarget<F>, TableTarget<F>>) toT it.b })
    //</editor-fold>

    fun <F> routeFinishFragmentAtSingle(target: StackFragment?): YRoute<StackFragState<F, StackType.Single<F>>, Tuple2<SingleTarget<F>?, FinishResult>>
            where F : Fragment, F : StackFragment =
            finishFragmentAtSingle<F>(target).mapInner(lens = stackTypeLens<F, StackType.Single<F>>()).stackTranIO()

    fun <F> routeFinishFragmentAtTable(target: StackFragment?): YRoute<StackFragState<F, StackType.Table<F>>, Tuple2<TableTarget<F>?, FinishResult>>
            where F : Fragment, F : StackFragment =
            finishFragmentAtTable<F>(target).mapInner(lens = stackTypeLens<F, StackType.Table<F>>()).stackTranIO()

    fun <F> routeFinishFragment(target: StackFragment?): YRoute<StackFragState<F, StackType<F>>, Tuple2<Either<SingleTarget<F>?, TableTarget<F>?>, FinishResult>>
            where F : Fragment, F : StackFragment =
            foldForFragState(
                    routeFinishFragmentAtSingle<F>(target)
                            .mapResult { (it.a.left() as Either<SingleTarget<F>?, TableTarget<F>?>) toT it.b },
                    routeFinishFragmentAtTable<F>(target)
                            .mapResult { (it.a.right() as Either<SingleTarget<F>?, TableTarget<F>?>) toT it.b })

    fun <S, F> YRoute<S, Tuple2<SingleTarget<F>?, FinishResult>>.mapSingleFinishResult(): YRoute<S, Tuple2<F?, FinishResult>> =
            mapResult { it.a?.target?.t toT it.b }

    fun <S, F> YRoute<S, Tuple2<TableTarget<F>?, FinishResult>>.mapTableFinishResult(): YRoute<S, Tuple2<F?, FinishResult>> =
            mapResult { it.a?.target?.t toT it.b }

    fun <S, F> YRoute<S, Tuple2<Either<SingleTarget<F>?, TableTarget<F>?>, FinishResult>>.mapFinishResult(): YRoute<S, Tuple2<F?, FinishResult>> =
            mapResult { (target, result) ->
                when (target) {
                    is Either.Left -> target.a?.target?.t
                    is Either.Right -> target.b?.target?.t
                } toT result
            }

    fun <F, A, T> routeStartFragAtNewActivity(activityBuilder: ActivityBuilder<A>,
                                              fragBuilder: FragmentBuilder<F>)
            : YRoute<ActivitiesState, Tuple2<A, F>>
            where F : Fragment, F : StackFragment, T : StackType<F>, A : FragmentActivity, A : StackHost<F, T> =
            routeStartStackActivity<F, T, A>(activityBuilder.animDataOrDefault(fragBuilder.animData))
                    .flatMapR { activity: A ->
                        val stack = activity.initStack
                        when (stack) {
                            is StackType.Single<*> -> startFragmentForSingle(fragBuilder)
                                    .runAtA(activity as StackHost<F, StackType.Single<F>>)
                                    .mapResult { f -> (activity toT f) as Tuple2<A, F> }
                            is StackType.Table<*> -> startFragmentForTable(fragBuilder)
                                    .runAtA(activity as StackHost<F, StackType.Table<F>>)
                                    .mapResult { f -> (activity toT f) as Tuple2<A, F> }
                            else -> routeFail("Unreachable")
                        }
                    }

    fun <F, A> routeStartFragAtNewSingleActivity(activityBuilder: ActivityBuilder<A>,
                                                 fragBuilder: FragmentBuilder<F>)
            : YRoute<ActivitiesState, Tuple2<A, F>>
            where F : Fragment, F : StackFragment, A : FragmentActivity, A : StackHost<F, StackType.Single<F>> =
            routeStartStackActivity(activityBuilder.animDataOrDefault(fragBuilder.animData))
                    .flatMapR { activity ->
                        startFragmentForSingle(fragBuilder)
                                .runAtA(activity)
                                .mapResult { f -> activity toT f }
                    }

    fun <F, A> routeStartFragAtNewTableActivity(activityBuilder: ActivityBuilder<A>,
                                                fragBuilder: FragmentBuilder<F>)
            : YRoute<ActivitiesState, Tuple2<A, F>>
            where F : Fragment, F : StackFragment, A : FragmentActivity, A : StackHost<F, StackType.Table<F>> =
            routeStartStackActivity(activityBuilder.animDataOrDefault(fragBuilder.animData))
                    .flatMapR { activity ->
                        startFragmentForTable(fragBuilder)
                                .runAtA(activity)
                                .mapResult { f -> activity toT f }
                    }

    fun <F, T, A> routeOnBackPress(activity: A): YRoute<ActivitiesState, FinishResult>
            where F : Fragment, F : StackFragment, T : StackType<F>, A : FragmentActivity, A : StackHost<F, T> =
            routeFinishFragment<F>(null).mapFinishResult() runAtADealFinish activity

    fun <F> startWithShared(builder: FragmentBuilder<F>, view: View): YRoute<StackFragState<F, StackType<F>>, F>
            where F : Fragment, F : StackFragment =
            createFragment<StackFragState<F, StackType<F>>, F>(builder).flatMapR { f ->
                sharedItem<F>(f, view).flatMapR {
                    foldStack<F, IO<YResult<F>>>(putFragAtSingle<F>(builder, f), putFragAtTable<F>(builder, f))
                }
                        .mapInner(lens = stackTypeLens<F, StackType<F>>())
                        .stackTranIO()
            }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    fun <F : Fragment> sharedItem(f: Fragment, view: View): YRoute<StackRoute.StackInnerState<StackType<F>>, Unit> =
            routeF { s, routeCxt ->
                binding {
                    !IO {
                        f.sharedElementEnterTransition = TransitionSet().apply {
                            setOrdering(TransitionSet.ORDERING_TOGETHER)
                            addTransition(ChangeBounds())
                            addTransition(ChangeTransform())
                        }
//                        f.enterTransition = Fade()
//                        f.exitTransition = Fade()
                        f.sharedElementReturnTransition = TransitionSet().apply {
                            setOrdering(TransitionSet.ORDERING_TOGETHER)
                            addTransition(ChangeBounds())
                            addTransition(ChangeTransform())
                        }
                    }

                    !IO {
                        s.ft.setReorderingAllowed(true)
                        s.ft.addSharedElement(view, ViewCompat.getTransitionName(view)!!)
                    }

                    s toT Success(Unit)
                }
            }
}
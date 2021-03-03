package indi.yume.tools.yroute.fragmentmanager

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.CallSuper
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import arrow.core.*
import indi.yume.tools.yroute.*
import indi.yume.tools.yroute.datatype.*
import io.reactivex.rxjava3.subjects.Subject
import kotlinx.coroutines.rx3.await

abstract class BaseManagerFragment<F> : Fragment(), StackFragment where F : Fragment, F : StackFragment {
    abstract val core: CoreEngine<ActivitiesState>

    override val lifeSubject: Subject<FragmentLifeEvent> = FragmentLifecycleOwner.defaultLifeSubject()

    override var controller: FragController = FragController.defaultController()
    init {
        commonFragmentLifeLogicDefault { core }
                .catchSubscribe()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        makeState(FragmentLifeEvent.OnCreate(this, savedInstanceState))
    }

    @CallSuper
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        makeState(FragmentLifeEvent.OnCreateView(this, inflater, container, savedInstanceState))
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        makeState(FragmentLifeEvent.OnViewCreated(this, view, savedInstanceState))
    }

    override fun onStart() {
        super.onStart()
        makeState(FragmentLifeEvent.OnStart(this))
    }

    override fun preSendFragmentResult(requestCode: Int, resultCode: Int, data: Bundle?) {
        makeState(FragmentLifeEvent.PreSendFragmentResult(this, requestCode, resultCode, data))
    }

    override fun onFragmentResult(requestCode: Int, resultCode: Int, data: Bundle?) {
        makeState(FragmentLifeEvent.OnFragmentResult(this, requestCode, resultCode, data))
    }

    override fun onResume() {
        super.onResume()
        makeState(FragmentLifeEvent.OnResume(this))
    }

    override fun onLowMemory() {
        super.onLowMemory()
        makeState(FragmentLifeEvent.OnLowMemory(this))
    }

    override fun onDestroy() {
        super.onDestroy()
        makeState(FragmentLifeEvent.OnDestroy(this))
        destroyLifecycle()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        makeState(FragmentLifeEvent.OnSaveInstanceState(this, outState))
    }

    suspend fun getStackActivity(): StackHost<F, StackType<F>>? =
            StackRoute.run {
                routeGetStackFromFrag<F, StackType<F>>(this@BaseManagerFragment)
            }.startLazy(core).flattenForYRoute()

    suspend fun <A : Activity> start(builder: ActivityBuilder<A>): A =
            ActivitiesRoute.routeStartActivity(builder).startLazy(core).flattenForYRoute()

    suspend fun <A : Activity> startActivityForRx(builder: ActivityBuilder<A>): Tuple2<Int, Bundle?>? =
            ActivitiesRoute.routeStartActivityForRx(builder).startLazy(core).flattenForYRoute()
                    .b.await()

    suspend fun startFragmentForRx(builder: FragmentBuilder<F>): Tuple2<Int, Bundle?>? =
            StackRoute.run {
                routeStartFragmentForRx(builder) runAtF this@BaseManagerFragment
            }.startLazy(core).flattenForYRoute().await()

    suspend fun start(builder: FragmentBuilder<F>): F =
            StackRoute.run {
                routeStartFragment(builder) runAtF this@BaseManagerFragment
            }.startLazy(core).flattenForYRoute()

    suspend fun <A, T> startFragmentOnNewActivity(fragIntent: Intent, activityClazz: Class<A>,
                                          anim: AnimData? = YRouteConfig.globalDefaultAnimData): Tuple2<A, F>
            where T : StackType<F>, A : FragmentActivity, A : StackHost<F, T> =
            startFragmentOnNewActivity(
                    ActivityBuilder(activityClazz).withAnimData(anim),
                    FragmentBuilder(fragIntent))

    suspend fun <A, T> startFragmentOnNewActivity(activityBuilder: ActivityBuilder<A>,
                                                  builder: FragmentBuilder<F>): Tuple2<A, F>
            where T : StackType<F>, A : FragmentActivity, A : StackHost<F, T> =
            StackRoute.routeStartFragAtNewActivity(
                    activityBuilder,
                    builder)
                    .startLazy(core).flattenForYRoute()

    suspend fun <A> startFragmentOnNewActivityForResult(fragIntent: Intent,
                                                activityClazz: Class<A>,
                                                requestCode: Int)
            where A : FragmentActivity, A : StackHost<F, StackType.Single<F>> =
            startFragmentOnNewActivityForResult(
                    ActivityBuilder(activityClazz),
                    FragmentBuilder(fragIntent),
                    requestCode)

    suspend fun <A> startFragmentOnNewActivityForResult(activityBuilder: ActivityBuilder<A>,
                                                builder: FragmentBuilder<F>,
                                                requestCode: Int)
            where A : FragmentActivity, A : StackHost<F, StackType.Single<F>> = StackRoute.run {
        ActivitiesRoute.routeStartActivityForResult(activityBuilder.animDataOrDefault(builder.animData), requestCode)
                .flatMapR {
                    routeStartFragmentForResult(builder, requestCode) runAtA it
                }
    }.startLazy(core).flattenForYRoute()

    suspend fun startFragment(intent: Intent): F =
        startFragment(FragmentBuilder<F>(intent))

    suspend fun startFragment(builder: FragmentBuilder<F>): F =
        StackRoute.run {
            routeStartFragment(builder) runAtF this@BaseManagerFragment
        }.startLazy(core).flattenForYRoute()

    suspend fun startFragmentForResult(intent: Intent, requestCode: Int): F =
        startFragmentForResult(FragmentBuilder(intent), requestCode)

    suspend fun startFragmentForResult(builder: FragmentBuilder<F>, requestCode: Int): F =
            StackRoute.run {
                routeStartFragmentForResult(builder, requestCode) runAtF this@BaseManagerFragment
            }.startLazy(core).flattenForYRoute()

    suspend fun finish(): Unit = StackRoute.run {
        routeGetStackFromFrag<F, StackType<F>>(this@BaseManagerFragment)
                .resultNonNull("Can not find parent StackHost.")
                .flatMapR { host ->
                    routeFinishFragment<F>(this@BaseManagerFragment)
                            .mapFinishResult()
                            .runAtADealFinish(requireActivity(), host)
                            .mapResult { Unit }
                }
    }.startLazy(core).flattenForYRoute()

    suspend fun finishNoAnim(): Unit = StackRoute.run {
        routeGetStackFromFrag<F, StackType<F>>(this@BaseManagerFragment)
                .resultNonNull("Can not find parent StackHost.")
                .flatMapR { host ->
                    foldForFragState(
                            routeFinishFragmentAtSingleNoAnim<F>(this@BaseManagerFragment)
                                    .mapResult { (it.a.left() as Either<SingleTarget<F>?, TableTarget<F>?>) toT it.b },
                            routeFinishFragmentAtTableNoAnim<F>(this@BaseManagerFragment)
                                    .mapResult { (it.a.right() as Either<SingleTarget<F>?, TableTarget<F>?>) toT it.b })
                            .mapFinishResult()
                            .runAtADealFinish(requireActivity(), host)
                            .mapResult { Unit }
                }
    }.startLazy(core).flattenForYRoute()

    suspend fun isTopOfStack(): Boolean = StackRoute.run {
        foldForFragState(
                getTopOfStackForSingle<Fragment>(),
                getTopOfStackForTable<Fragment>()
        ).mapResult { it == this@BaseManagerFragment } runAtF this@BaseManagerFragment
    }.startLazy(core).flattenForYRoute()
}
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
import arrow.fx.IO
import arrow.fx.extensions.io.monad.flatten
import indi.yume.tools.yroute.*
import indi.yume.tools.yroute.datatype.*
import io.reactivex.subjects.Subject

abstract class BaseManagerFragment<F> : Fragment(), StackFragment where F : Fragment, F : StackFragment {
    abstract val core: CoreEngine<ActivitiesState>

    override val lifeSubject: Subject<FragmentLifeEvent> = FragmentLifecycleOwner.defaultLifeSubject()

    override var controller: FragController = FragController.defaultController()

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

    fun getStackActivity(): IO<StackHost<F, StackType<F>>?> =
            StackRoute.run {
                routeGetStackFromFrag<F, StackType<F>>(this@BaseManagerFragment)
            }.start(core).flattenForYRoute()

    fun <A : Activity> start(builder: ActivityBuilder<A>): IO<A> =
            ActivitiesRoute.routeStartActivity(builder).start(core).flattenForYRoute()

    fun <A : Activity> startActivityForRx(builder: ActivityBuilder<A>): IO<Tuple2<Int, Bundle?>> =
            ActivitiesRoute.routeStartActivityForRx(builder).start(core).flattenForYRoute()
                    .map { it.b.toIO() }.flatten()

    fun startFragmentForRx(builder: FragmentBuilder<F>): IO<Tuple2<Int, Bundle?>> =
            StackRoute.run {
                routeStartFragmentForRx(builder) runAtF this@BaseManagerFragment
            }.start(core).flattenForYRoute()
                    .map { it.toIO() }.flatten()

    fun start(builder: FragmentBuilder<F>): IO<F> =
            StackRoute.run {
                routeStartFragment(builder) runAtF this@BaseManagerFragment
            }.start(core).flattenForYRoute()

    fun <A, T> startFragmentOnNewActivity(fragIntent: Intent, activityClazz: Class<A>,
                                          anim: AnimData? = YRouteConfig.globalDefaultAnimData): IO<Tuple2<A, F>>
            where T : StackType<F>, A : FragmentActivity, A : StackHost<F, T> =
            startFragmentOnNewActivity(
                    ActivityBuilder(activityClazz).withAnimData(anim),
                    FragmentBuilder(fragIntent))

    fun <A, T> startFragmentOnNewActivity(activityBuilder: ActivityBuilder<A>,
                                          builder: FragmentBuilder<F>): IO<Tuple2<A, F>>
            where T : StackType<F>, A : FragmentActivity, A : StackHost<F, T> =
            StackRoute.routeStartFragAtNewActivity(
                    activityBuilder,
                    builder)
                    .start(core).flattenForYRoute()

    fun <A> startFragmentOnNewActivityForResult(fragIntent: Intent,
                                                activityClazz: Class<A>,
                                                requestCode: Int)
            where A : FragmentActivity, A : StackHost<F, StackType.Single<F>> =
            startFragmentOnNewActivityForResult(
                    ActivityBuilder(activityClazz),
                    FragmentBuilder(fragIntent),
                    requestCode)

    fun <A> startFragmentOnNewActivityForResult(activityBuilder: ActivityBuilder<A>,
                                                builder: FragmentBuilder<F>,
                                                requestCode: Int)
            where A : FragmentActivity, A : StackHost<F, StackType.Single<F>> = StackRoute.run {
        ActivitiesRoute.routeStartActivityForResult(activityBuilder.animDataOrDefault(builder.animData), requestCode)
                .flatMapR {
                    routeStartFragmentForResult(builder, requestCode) runAtA it
                }
    }.start(core).flattenForYRoute()

    fun startFragment(intent: Intent): IO<F> =
        startFragment(FragmentBuilder<F>(intent))

    fun startFragment(builder: FragmentBuilder<F>): IO<F> =
        StackRoute.run {
            routeStartFragment(builder) runAtF this@BaseManagerFragment
        }.start(core).flattenForYRoute()

    fun startFragmentForResult(intent: Intent, requestCode: Int): IO<F> =
        startFragmentForResult(FragmentBuilder(intent), requestCode)

    fun startFragmentForResult(builder: FragmentBuilder<F>, requestCode: Int): IO<F> =
            StackRoute.run {
                routeStartFragmentForResult(builder, requestCode) runAtF this@BaseManagerFragment
            }.start(core).flattenForYRoute()

    fun finish(): IO<Unit> = StackRoute.run {
        routeGetStackFromFrag<F, StackType<F>>(this@BaseManagerFragment)
                .resultNonNull("Can not find parent StackHost.")
                .flatMapR { host ->
                    routeFinishFragment<F>(this@BaseManagerFragment)
                            .mapFinishResult()
                            .runAtADealFinish(requireActivity(), host)
                            .mapResult { Unit }
                }
    }.start(core).flattenForYRoute()

    fun finishNoAnim(): IO<Unit> = StackRoute.run {
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
    }.start(core).flattenForYRoute()

    fun isTopOfStack(): IO<Boolean> = StackRoute.run {
        foldForFragState(
                getTopOfStackForSingle<Fragment>(),
                getTopOfStackForTable<Fragment>()
        ).mapResult { it == this@BaseManagerFragment } runAtF this@BaseManagerFragment
    }.start(core).flattenForYRoute()
}
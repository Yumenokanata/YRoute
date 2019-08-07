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
import arrow.core.Tuple2
import arrow.effects.IO
import arrow.effects.extensions.io.monad.flatten
import indi.yume.tools.yroute.*
import indi.yume.tools.yroute.datatype.*
import io.reactivex.subjects.Subject

abstract class BaseManagerFragment : Fragment(), StackFragment {
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

    fun getStackActivity(): IO<StackHost<BaseManagerFragment, StackType<BaseManagerFragment>>?> =
            StackRoute.run {
                routeGetStackFromFrag<BaseManagerFragment, StackType<BaseManagerFragment>>(this@BaseManagerFragment)
            }.start(core).flattenForYRoute()

    fun <A : Activity> start(builder: ActivityBuilder<A>): IO<A> =
            ActivitiesRoute.routeStartActivity(builder).start(core).flattenForYRoute()

    fun <A : Activity> startActivityForRx(builder: RxActivityBuilder<A>): IO<Tuple2<Int, Bundle?>> =
            ActivitiesRoute.routeStartActivityForRx(builder).start(core).flattenForYRoute()
                    .map { it.toIO() }.flatten()

    fun <F> startFragmentForRx(builder: FragmentBuilder<F>): IO<Tuple2<Int, Bundle?>> where F : Fragment, F : StackFragment =
            StackRoute.run {
                routeStartFragmentForRx(builder) runAtF this@BaseManagerFragment
            }.start(core).flattenForYRoute()
                    .map { it.toIO() }.flatten()

    fun <F> start(builder: FragmentBuilder<F>): IO<F> where F : Fragment, F : StackFragment =
            StackRoute.run {
                routeStartFragment(builder) runAtF this@BaseManagerFragment
            }.start(core).flattenForYRoute()

    fun <F, A, T> startFragmentOnNewActivity(fragIntent: Intent, activityClazz: Class<A>,
                                             anim: AnimData? = YRouteConfig.globalDefaultAnimData): IO<Tuple2<A, F>>
            where F : Fragment, F : StackFragment, T : StackType<F>, A : FragmentActivity, A : StackHost<F, T> =
            startFragmentOnNewActivity(
                    ActivityBuilder(activityClazz).withAnimData(anim),
                    FragmentBuilder(fragIntent))

    fun <F, A, T> startFragmentOnNewActivity(activityBuilder: ActivityBuilder<A>,
                                             builder: FragmentBuilder<F>): IO<Tuple2<A, F>>
            where F : Fragment, F : StackFragment, T : StackType<F>, A : FragmentActivity, A : StackHost<F, T> =
            StackRoute.routeStartFragAtNewActivity(
                    activityBuilder,
                    builder)
                    .start(core).flattenForYRoute()

    fun <F, A> startFragmentOnNewActivityForResult(fragIntent: Intent,
                                                   activityClazz: Class<A>,
                                                   requestCode: Int)
            where F : Fragment, F : StackFragment, A : FragmentActivity, A : StackHost<F, StackType.Single<F>> =
            startFragmentOnNewActivityForResult(
                    ActivityBuilder(activityClazz),
                    FragmentBuilder(fragIntent),
                    requestCode)

    fun <F, A> startFragmentOnNewActivityForResult(activityBuilder: ActivityBuilder<A>,
                                                   builder: FragmentBuilder<F>,
                                                   requestCode: Int)
            where F : Fragment, F : StackFragment, A : FragmentActivity, A : StackHost<F, StackType.Single<F>> = StackRoute.run {
        ActivitiesRoute.routeStartActivityForResult(activityBuilder.animDataOrDefault(builder.animData), requestCode)
                .flatMapR {
                    routeStartFragmentForResult(builder, requestCode) runAtA it
                }
    }.start(core).flattenForYRoute()

    fun <F> startFragment(intent: Intent): IO<F> where F : Fragment, F : StackFragment =
            startFragment(FragmentBuilder<F>(intent))

    fun <F> startFragment(builder: FragmentBuilder<F>): IO<F> where F : Fragment, F : StackFragment =
            StackRoute.run {
                routeStartFragment(builder) runAtF this@BaseManagerFragment
            }.start(core).flattenForYRoute()

    fun <F> startFragmentForResult(intent: Intent, requestCode: Int): IO<F> where F : Fragment, F : StackFragment =
            startFragmentForResult(FragmentBuilder(intent), requestCode)

    fun <F> startFragmentForResult(builder: FragmentBuilder<F>, requestCode: Int): IO<F> where F : Fragment, F : StackFragment =
            StackRoute.run {
                routeStartFragmentForResult(builder, requestCode) runAtF this@BaseManagerFragment
            }.start(core).flattenForYRoute()

    fun finish(): IO<Unit> = StackRoute.run {
        routeGetStackFromFrag<BaseManagerFragment, StackType<BaseManagerFragment>>(this@BaseManagerFragment)
                .resultNonNull("Can not find parent StackHost.")
                .flatMapR { host ->
                    routeFinishFragment<BaseManagerFragment>(this@BaseManagerFragment)
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
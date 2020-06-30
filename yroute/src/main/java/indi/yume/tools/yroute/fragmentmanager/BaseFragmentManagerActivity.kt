package indi.yume.tools.yroute.fragmentmanager

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import arrow.core.Tuple2
import indi.yume.tools.yroute.*
import indi.yume.tools.yroute.YRouteConfig.globalDefaultAnimData
import indi.yume.tools.yroute.datatype.CoreEngine
import indi.yume.tools.yroute.datatype.flatMapR
import indi.yume.tools.yroute.datatype.start
import indi.yume.tools.yroute.datatype.startLazy
import io.reactivex.subjects.Subject
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.await

abstract class BaseFragmentManagerActivity<F, T : StackType<F>> : AppCompatActivity(), ActivityLifecycleOwner, StackHost<F, T>
        where F : Fragment, F : StackFragment {
    override val lifeSubject: Subject<ActivityLifeEvent> = ActivityLifecycleOwner.defaultLifeSubject()

    override var controller: StackController = StackController.defaultController()

    abstract val core: CoreEngine<ActivitiesState>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        makeState(ActivityLifeEvent.OnCreate(this, savedInstanceState))
    }

    override fun onStart() {
        super.onStart()
        makeState(ActivityLifeEvent.OnStart(this))
    }

    override fun onResume() {
        super.onResume()
        makeState(ActivityLifeEvent.OnResume(this))
    }

    override fun onPause() {
        super.onPause()
        makeState(ActivityLifeEvent.OnPause(this))
    }

    override fun onStop() {
        super.onStop()
        makeState(ActivityLifeEvent.OnStop(this))
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        makeState(ActivityLifeEvent.OnNewIntent(this, intent))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        makeState(ActivityLifeEvent.OnSaveInstanceState(this, outState))
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        makeState(ActivityLifeEvent.OnConfigurationChanged(this, newConfig))
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        makeState(ActivityLifeEvent.OnActivityResult(this, requestCode, resultCode, data))
    }

    override fun onDestroy() {
        super.onDestroy()
        makeState(ActivityLifeEvent.OnDestroy(this))
        destroyLifecycle()
    }

    override fun onBackPressed() {
        GlobalScope.launch {
            val result = StackRoute.routeOnBackPress(this@BaseFragmentManagerActivity).start(core)
            Logger.d("onBackPressed", "result=$result")
        }
    }

    suspend fun <A : Activity> start(builder: ActivityBuilder<A>): A =
            ActivitiesRoute.routeStartActivity(builder).startLazy(core).flattenForYRoute()

    suspend fun <A : Activity> startActivityForRx(builder: ActivityBuilder<A>): Tuple2<Int, Bundle?>? =
            ActivitiesRoute.routeStartActivityForRx(builder).startLazy(core).flattenForYRoute()
                    .b.await()

    suspend fun start(builder: FragmentBuilder<F>): F =
            StackRoute.run {
                routeStartFragment(builder) runAtA this@BaseFragmentManagerActivity
            }.startLazy(core).flattenForYRoute()

    suspend fun <F, A, T> startFragmentOnNewActivity(fragIntent: Intent, activityClazz: Class<A>,
                                                     anim: AnimData? = globalDefaultAnimData): Tuple2<A, F>
            where F : Fragment, F : StackFragment, T : StackType<F>, A : FragmentActivity, A : StackHost<F, T> =
            startFragmentOnNewActivity(
                    ActivityBuilder(activityClazz).withAnimData(anim),
                    FragmentBuilder(fragIntent))

    suspend fun <F, A, T> startFragmentOnNewActivity(activityBuilder: ActivityBuilder<A>,
                                                     builder: FragmentBuilder<F>): Tuple2<A, F>
            where F : Fragment, F : StackFragment, T : StackType<F>, A : FragmentActivity, A : StackHost<F, T> =
            StackRoute.routeStartFragAtNewActivity(
                    activityBuilder,
                    builder)
                    .startLazy(core).flattenForYRoute()

    suspend fun <F, A> startFragmentOnNewActivityForResult(fragIntent: Intent,
                                                           activityClazz: Class<A>,
                                                           requestCode: Int)
            where F : Fragment, F : StackFragment, A : FragmentActivity, A : StackHost<F, StackType.Single<F>> =
            startFragmentOnNewActivityForResult(
                    ActivityBuilder(activityClazz),
                    FragmentBuilder(fragIntent),
                    requestCode)

    suspend fun <F, A> startFragmentOnNewActivityForResult(activityBuilder: ActivityBuilder<A>,
                                                           builder: FragmentBuilder<F>,
                                                           requestCode: Int)
            where F : Fragment, F : StackFragment, A : FragmentActivity, A : StackHost<F, StackType.Single<F>> = StackRoute.run {
        ActivitiesRoute.routeStartActivityForResult(activityBuilder.animDataOrDefault(builder.animData), requestCode)
                .flatMapR {
                    routeStartFragmentForResult(builder, requestCode) runAtA it
                }
    }.startLazy(core).flattenForYRoute()

    suspend fun startFragment(intent: Intent): F = startFragment(FragmentBuilder<F>(intent))

    suspend fun startFragment(builder: FragmentBuilder<F>): F = StackRoute.run {
        routeStartFragment(builder) runAtA this@BaseFragmentManagerActivity
    }.startLazy(core).flattenForYRoute()

    suspend fun startFragmentForResult(intent: Intent, requestCode: Int): F =
            startFragmentForResult(FragmentBuilder(intent), requestCode)

    suspend fun startFragmentForResult(builder: FragmentBuilder<F>, requestCode: Int): F = StackRoute.run {
        routeStartFragmentForResult(builder, requestCode) runAtA this@BaseFragmentManagerActivity
    }.startLazy(core).flattenForYRoute()
}
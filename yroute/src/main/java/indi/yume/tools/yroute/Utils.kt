package indi.yume.tools.yroute

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.annotation.AnimRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import arrow.core.*
import arrow.fx.IO
import arrow.fx.OnCancel
import arrow.fx.typeclasses.Disposable
import arrow.optics.Lens
import arrow.typeclasses.Monoid
import indi.yume.tools.yroute.datatype.YResult
import indi.yume.tools.yroute.datatype.Fail
import indi.yume.tools.yroute.datatype.RouteCxt
import indi.yume.tools.yroute.datatype.Success
import io.reactivex.*
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import io.reactivex.disposables.Disposable as RxDisposable
import java.lang.Exception
import java.util.concurrent.atomic.AtomicLong

const val NO_ANIMATION_RES = 0

interface ActivityLifecycleOwner {
    val lifeSubject: Subject<ActivityLifeEvent>

    fun makeState(event: ActivityLifeEvent) = lifeSubject.onNext(event)

    fun destroyLifecycle() = lifeSubject.onComplete()

    fun bindActivityLife(): Observable<ActivityLifeEvent> = lifeSubject

    companion object {
        fun defaultLifeSubject(): Subject<ActivityLifeEvent> =
            PublishSubject.create<ActivityLifeEvent>().toSerialized()
    }
}

interface FragmentLifecycleOwner {
    val lifeSubject: Subject<FragmentLifeEvent>

    fun makeState(event: FragmentLifeEvent) = lifeSubject.onNext(event)

    fun destroyLifecycle() = lifeSubject.onComplete()

    fun bindFragmentLife(): Observable<FragmentLifeEvent> = lifeSubject

    companion object {
        fun defaultLifeSubject(): Subject<FragmentLifeEvent> =
            PublishSubject.create<FragmentLifeEvent>().toSerialized()
    }
}

//<editor-fold defaultstate="collapsed" desc="Activity Life">
sealed class ActivityLifeEvent {
    data class OnCreate(val activity: Activity, val savedInstanceState: Bundle?) : ActivityLifeEvent()
    data class OnStart(val activity: Activity) : ActivityLifeEvent()
    data class OnResume(val activity: Activity) : ActivityLifeEvent()
    data class OnPause(val activity: Activity) : ActivityLifeEvent()
    data class OnStop(val activity: Activity) : ActivityLifeEvent()
    data class OnDestroy(val activity: Activity) : ActivityLifeEvent()
    data class OnSaveInstanceState(val activity: Activity, val outState: Bundle) : ActivityLifeEvent()

    // Global activity stream not have this events:
    data class OnNewIntent(val activity: Activity, val intent: Intent?) : ActivityLifeEvent()

    data class OnConfigurationChanged(val activity: Activity, val newConfig: Configuration) : ActivityLifeEvent()
    data class OnActivityResult(
        val activity: Activity, val requestCode: Int,
        val resultCode: Int, val data: Intent?
    ) : ActivityLifeEvent()
}
//</editor-fold>

//<editor-fold defaultstate="collapsed" desc="Fragment Life">
sealed class FragmentLifeEvent {
    data class OnCreate(val fragment: Fragment, val savedInstanceState: Bundle?) : FragmentLifeEvent()

    data class OnCreateView(
        val fragment: Fragment,
        val inflater: LayoutInflater,
        val container: ViewGroup?,
        val savedInstanceState: Bundle?
    ) : FragmentLifeEvent()

    data class OnViewCreated(val fragment: Fragment, val view: View, val savedInstanceState: Bundle?) :
        FragmentLifeEvent()

    data class OnStart(val fragment: Fragment) : FragmentLifeEvent()
    data class OnResume(val fragment: Fragment) : FragmentLifeEvent()
    data class OnDestroy(val fragment: Fragment) : FragmentLifeEvent()
    data class OnLowMemory(val fragment: Fragment): FragmentLifeEvent()

    // Just for StackFragment, see [StackFragment#onFragmentResult()]
    data class OnFragmentResult(val fragment: Fragment, val requestCode: Int, val resultCode: Int, val data: Bundle?)
        : FragmentLifeEvent()
    // Just for StackFragment, see [StackFragment#preSendFragmentResult()]
    data class PreSendFragmentResult(val fragment: Fragment, val requestCode: Int, val resultCode: Int, val data: Bundle?)
        : FragmentLifeEvent()
    // Just for StackFragment, see [StackFragment#onShow()]
    data class OnShow(val fragment: Fragment, val showMode: OnShowMode): FragmentLifeEvent()
    // Just for StackFragment, see [StackFragment#onHide()]
    data class OnHide(val fragment: Fragment, val hideMode: OnHideMode): FragmentLifeEvent()
}

sealed class OnHideMode {
    object OnStartNew : OnHideMode()
    object OnSwitch : OnHideMode()
    object OnStartNewAfterAnim : OnHideMode()
}

sealed class OnShowMode {
    object OnBack : OnShowMode()
    object OnSwitch : OnShowMode()
    object OnCreate : OnShowMode()
    object OnCreateAfterAnim : OnShowMode()
}
//</editor-fold>

class YRouteException(val fail: Fail) : Exception(fail.message, fail.error ?: Throwable("YRoute inner error."))

fun <R> IO<YResult<R>>.flattenForYRoute(): IO<R> =
        this.flatMap {
            when (it) {
                is Success -> IO.just(it.t)
                is Fail -> IO.raiseError(YRouteException(it))
            }
        }

fun <T> IO<T>.unsafeAsyncRunDefault(
        onSuccess: (T) -> Unit = {},
        onError: (Throwable) -> Unit = { it.printStackTrace() }): Unit =
        unsafeRunAsync {
            when (it) {
                is Either.Left -> onError(it.a)
                is Either.Right -> onSuccess(it.b)
            }
        }

fun <T> IO<T>.unsafeAsyncRunDefaultCancellable(
        onCancel: OnCancel = OnCancel.Silent,
        onSuccess: (T) -> Unit = {},
        onError: (Throwable) -> Unit = { it.printStackTrace() }): Disposable =
        unsafeRunAsyncCancellable(onCancel) {
            when (it) {
                is Either.Left -> onError(it.a)
                is Either.Right -> onSuccess(it.b)
            }
        }

object CoreID {
    private val uuid = AtomicLong(0)

    fun get(): Long = uuid.incrementAndGet()
}

class TypeCheck<T>

private val typeFake = TypeCheck<Nothing>()

@Suppress("UNCHECKED_CAST")
fun <T> type(): TypeCheck<T> = typeFake as TypeCheck<T>

fun <T> Maybe<T>.toIO(): IO<Option<T>> = map { it.some() }.toSingle(none()).toIO()

fun <T> Single<T>.toIO(): IO<T> = IO.cancelable { cb ->
    val disposable = subscribe({ cb(it.right()) }, { cb(it.left()) })
    IO { disposable.dispose() }
}

fun Completable.toIO(): IO<Unit> = toSingleDefault(Unit).toIO()

fun <T> IO<T>.toSingle(): Single<T> {
    var ioDisposable: Disposable? = null

    return Single.create<T> { emitter ->
        ioDisposable = this@toSingle.runAsyncCancellable { either ->
            when (either) {
                is Either.Left -> emitter.onError(either.a)
                is Either.Right -> emitter.onSuccess(either.b)
            }
            IO.unit
        }.unsafeRunSync()
    }.doOnDispose { ioDisposable?.invoke() }
}

fun <T> IO<T>.toCompletable(callback: (Either<Throwable, T>) -> Unit): Completable {
    var ioDisposable: Disposable? = null

    return Completable.create { emitter ->
        ioDisposable = this@toCompletable.runAsyncCancellable { either ->
            callback(either)
            emitter.onComplete()
            IO.unit
        }.unsafeRunSync()
    }.doOnDispose { ioDisposable?.invoke() }
}

fun Completable.catchSubscribe(): io.reactivex.disposables.Disposable = subscribe(
        { }, { if (YRouteConfig.showLog) it.printStackTrace() }
)

fun <T> Single<T>.catchSubscribe(): io.reactivex.disposables.Disposable = subscribe(
        { }, { if (YRouteConfig.showLog) it.printStackTrace() }
)

fun <T> Maybe<T>.catchSubscribe(): io.reactivex.disposables.Disposable = subscribe(
        { }, { if (YRouteConfig.showLog) it.printStackTrace() }
)

fun <T> Observable<T>.catchSubscribe(): io.reactivex.disposables.Disposable = subscribe(
        { }, { if (YRouteConfig.showLog) it.printStackTrace() }
)

fun <T> Flowable<T>.catchSubscribe(): io.reactivex.disposables.Disposable = subscribe(
        { }, { if (YRouteConfig.showLog) it.printStackTrace() }
)

fun <T> IO<T>.catchSubscribe(): Disposable = unsafeRunAsyncCancellable {
    when(it) {
        is Either.Left -> {
            if (YRouteConfig.showLog) it.a.printStackTrace()
        }
        is Either.Right -> {}
    }
}

fun <T> IO<T>.subscribe(onError: (Throwable) -> Unit = {},
                        onSuccess: (T) -> Unit = {}): Disposable = unsafeRunAsyncCancellable {
    when (it) {
        is Either.Left -> onError(it.a)
        is Either.Right -> onSuccess(it.b)
    }
}

fun Unit.monoid(): Monoid<Unit> = UnitMonoid

val UnitMonoid = object : Monoid<Unit> {
    override fun empty() = Unit

    override fun Unit.combine(b: Unit) = Unit
}

fun RouteCxt.checkComponentClass(intent: Intent, obj: Any): Boolean =
    intent.component?.run {
        packageName == app.packageName && className == obj.javaClass.name
    } ?: false

fun <S1, S2 : Any, S3 : Any> Lens<S1, S2?>.composeNonNull(lens: Lens<S2, S3>): Lens<S1, S3?> = Lens(
    get = { s1 ->
        val s2 = this@composeNonNull.get(s1)
        if (s2 == null) null else lens.get(s2)
    },
    set = { s1, s3 ->
        val oldS2 = this@composeNonNull.get(s1)
        if (s3 == null || oldS2 == null) this@composeNonNull.set(s1, null)
        else this@composeNonNull.set(s1, lens.set(oldS2, s3))
    }
)

fun FragmentManager.trans(f: FragmentTransaction.() -> Unit): IO<Unit> = IO {
    val ft = beginTransaction()
    ft.f()
    ft.routeExecFT()
}

fun startAnim(@AnimRes animRes: Int, target: View?): Completable = if (target == null) Completable.complete() else Completable.create { emitter ->
    if (target.background == null)
        target.setBackgroundColor(Color.WHITE)

    val animation = AnimationUtils.loadAnimation(target.context, animRes)
    animation.setAnimationListener(object : Animation.AnimationListener {
        override fun onAnimationRepeat(animation: Animation?) {}

        override fun onAnimationEnd(animation: Animation?) {
            emitter.onComplete()
        }

        override fun onAnimationStart(animation: Animation?) {}

    })
    target.startAnimation(animation)
}

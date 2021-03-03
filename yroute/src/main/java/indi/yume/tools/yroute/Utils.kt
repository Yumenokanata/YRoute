package indi.yume.tools.yroute

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.os.Looper
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
import indi.yume.tools.yroute.datatype.*
import indi.yume.tools.yroute.datatype.Success
import io.reactivex.rxjava3.core.*
import io.reactivex.rxjava3.subjects.BehaviorSubject
import io.reactivex.rxjava3.subjects.PublishSubject
import io.reactivex.rxjava3.subjects.Subject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.rx3.rxSingle
import kotlinx.coroutines.withContext
import io.reactivex.rxjava3.disposables.Disposable as RxDisposable
import java.lang.Exception
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

const val NO_ANIMATION_RES = 0

interface ActivityLifecycleOwner {
    val lifeSubject: Subject<ActivityLifeEvent>

    fun makeState(event: ActivityLifeEvent) = lifeSubject.onNext(event)

    fun destroyLifecycle() = lifeSubject.onComplete()

    fun bindActivityLife(): Observable<ActivityLifeEvent> = lifeSubject

    companion object {
        fun defaultLifeSubject(): Subject<ActivityLifeEvent> =
                BehaviorSubject.create<ActivityLifeEvent>().toSerialized()
    }
}

interface FragmentLifecycleOwner {
    val lifeSubject: Subject<FragmentLifeEvent>

    fun makeState(event: FragmentLifeEvent) = lifeSubject.onNext(event)

    fun destroyLifecycle() = lifeSubject.onComplete()

    fun bindFragmentLife(): Observable<FragmentLifeEvent> = lifeSubject

    companion object {
        fun defaultLifeSubject(): Subject<FragmentLifeEvent> =
                BehaviorSubject.create<FragmentLifeEvent>().toSerialized()
    }
}

//<editor-fold defaultstate="collapsed" desc="Activity Life">
sealed class ActivityLifeEvent(val order: Int): Comparable<ActivityLifeEvent> {
    override fun compareTo(other: ActivityLifeEvent): Int =
            order.compareTo(other.order)

    data class OnCreate(val activity: Activity, val savedInstanceState: Bundle?) : ActivityLifeEvent(OrderOnCreate)
    data class OnStart(val activity: Activity) : ActivityLifeEvent(OrderOnStart)
    data class OnResume(val activity: Activity) : ActivityLifeEvent(OrderOnResume)
    data class OnPause(val activity: Activity) : ActivityLifeEvent(OrderOnPause)
    data class OnStop(val activity: Activity) : ActivityLifeEvent(OrderOnStop)

    data class OnSaveInstanceState(val activity: Activity, val outState: Bundle) : ActivityLifeEvent(OrderOnSaveInstanceState)

    // Global activity stream not have this events:
    data class OnNewIntent(val activity: Activity, val intent: Intent?) : ActivityLifeEvent(OrderOnNewIntent)

    data class OnConfigurationChanged(val activity: Activity, val newConfig: Configuration) : ActivityLifeEvent(OrderOnConfigurationChanged)
    data class OnActivityResult(
        val activity: Activity, val requestCode: Int,
        val resultCode: Int, val data: Intent?
    ) : ActivityLifeEvent(OrderOnActivityResult)

    data class OnDestroy(val activity: Activity) : ActivityLifeEvent(OrderOnDestroy)

    companion object {
        const val OrderOnCreate = 0
        const val OrderOnStart = 1
        const val OrderOnResume = 2
        const val OrderOnPause = 3
        const val OrderOnStop = 4
        const val OrderOnSaveInstanceState = 5
        const val OrderOnNewIntent = 6
        const val OrderOnConfigurationChanged = 7
        const val OrderOnActivityResult = 8
        const val OrderOnDestroy = Int.MAX_VALUE
    }
}
//</editor-fold>

//<editor-fold defaultstate="collapsed" desc="Fragment Life">
sealed class FragmentLifeEvent(val order: Int): Comparable<FragmentLifeEvent> {
    abstract val fragment: Fragment

    override fun compareTo(other: FragmentLifeEvent): Int =
            order.compareTo(other.order)

    data class OnCreate(override val fragment: Fragment, val savedInstanceState: Bundle?) : FragmentLifeEvent(OrderOnCreate)

    data class OnCreateView(
        override val fragment: Fragment,
        val inflater: LayoutInflater,
        val container: ViewGroup?,
        val savedInstanceState: Bundle?
    ) : FragmentLifeEvent(OrderOnCreateView)

    data class OnViewCreated(override val fragment: Fragment, val view: View, val savedInstanceState: Bundle?) :
        FragmentLifeEvent(OrderOnViewCreated)

    data class OnStart(override val fragment: Fragment) : FragmentLifeEvent(OrderOnStart)
    data class OnResume(override val fragment: Fragment) : FragmentLifeEvent(OrderOnResume)

    // Just for StackFragment, see [StackFragment#onShow()]
    data class OnShow(override val fragment: Fragment, val showMode: OnShowMode): FragmentLifeEvent(OrderOnShow)
    // Just for StackFragment, see [StackFragment#onHide()]
    data class OnHide(override val fragment: Fragment, val hideMode: OnHideMode): FragmentLifeEvent(OrderOnHide)
    // Just for StackFragment, see [StackFragment#onFragmentResult()]
    data class OnFragmentResult(override val fragment: Fragment, val requestCode: Int, val resultCode: Int, val data: Bundle?)
        : FragmentLifeEvent(OrderOnFragmentResult)
    // Just for StackFragment, see [StackFragment#preSendFragmentResult()]
    data class PreSendFragmentResult(override val fragment: Fragment, val requestCode: Int, val resultCode: Int, val data: Bundle?)
        : FragmentLifeEvent(OrderPreSendFragmentResult)

    data class OnSaveInstanceState(override val fragment: Fragment, val outState: Bundle) : FragmentLifeEvent(OrderOnSaveInstanceState)
    data class OnLowMemory(override val fragment: Fragment): FragmentLifeEvent(OrderOnLowMemory)

    data class OnDestroy(override val fragment: Fragment) : FragmentLifeEvent(OrderOnDestroy)

    companion object {
        const val OrderOnCreate = 0
        const val OrderOnCreateView = 1
        const val OrderOnViewCreated = 2
        const val OrderOnStart = 3
        const val OrderOnResume = 4
        const val OrderOnShow = 5
        const val OrderOnHide = 6
        const val OrderOnFragmentResult = 7
        const val OrderPreSendFragmentResult = 8
        const val OrderOnSaveInstanceState = 9
        const val OrderOnLowMemory = 10
        const val OrderOnDestroy = Int.MAX_VALUE
    }
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
    data class OnRestore(val savedInstanceState: Bundle) : OnShowMode()
}
//</editor-fold>

internal fun isMainThread(): Boolean = Looper.myLooper() == Looper.getMainLooper()

class YRouteException(val fail: Fail) : Exception(fail.message, fail.error ?: Throwable("YRoute inner error."))

suspend fun <R> SuspendP<YResult<R>>.flattenForYRoute(): R {
    val result = this@flattenForYRoute()
    return when (result) {
        is Success -> result.t
        is Fail -> throw YRouteException(result)
    }
}

fun <R> SuspendP<YResult<R>>.flattenForYRouteLazy(): SuspendP<R> =
        {
            val result = this@flattenForYRouteLazy()
            when (result) {
                is Success -> result.t
                is Fail -> throw YRouteException(result)
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

fun <T> Maybe<T>.toIO(): IO<Option<T>> = map { it.some() }.defaultIfEmpty(none()).toIO()

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

fun <R : Any> SuspendP<R>.asSingle(): Single<R> = rxSingle {
    this@asSingle()
}

suspend fun <T> (suspend () -> T).byAttempt(): Either<Throwable, T> =
        try {
            this().right()
        } catch (e: Throwable) {
            e.left()
        }

inline fun <T> attempt(run: () -> T): Either<Throwable, T> =
        try {
            run().right()
        } catch (e: Throwable) {
            e.left()
        }

internal fun <R> constantsF0(c: R): () -> R = { c }

internal fun <T, R> constantsF1(c: R): (T) -> R = { c }

internal fun <T1, T2, R> constantsF2(c: R): (T1, T2) -> R = { t1, t2 -> c }

internal fun <T1, T2, T3, R> constantsF3(c: R): (T1, T2, T3) -> R = { t1, t2, t3 -> c }

internal fun <T1, T2, T3, T4, R> constantsF4(c: R): (T1, T2, T3, T4) -> R = { t1, t2, t3, t4 -> c }

internal fun <R> constantsF0S(c: R): suspend () -> R = { c }

internal fun <T, R> constantsF1S(c: R): suspend (T) -> R = { c }

internal fun <T1, T2, R> constantsF2S(c: R): suspend (T1, T2) -> R = { t1, t2 -> c }


fun Completable.catchSubscribe(): RxDisposable = subscribe(
        { }, { if (YRouteConfig.showLog) it.printStackTrace() }
)

fun <T> Single<T>.catchSubscribe(): RxDisposable = subscribe(
        { }, { if (YRouteConfig.showLog) it.printStackTrace() }
)

fun <T> Maybe<T>.catchSubscribe(): RxDisposable = subscribe(
        { }, { if (YRouteConfig.showLog) it.printStackTrace() }
)

fun <T> Observable<T>.catchSubscribe(): RxDisposable = subscribe(
        { }, { if (YRouteConfig.showLog) it.printStackTrace() }
)

fun <T> Flowable<T>.catchSubscribe(): RxDisposable = subscribe(
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

suspend fun FragmentManager.trans(f: FragmentTransaction.() -> Unit): Unit {
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

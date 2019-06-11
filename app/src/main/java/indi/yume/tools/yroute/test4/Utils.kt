package indi.yume.tools.yroute.test4

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import arrow.core.Either
import arrow.core.left
import arrow.core.right
import arrow.effects.IO
import arrow.effects.typeclasses.Disposable
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject

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
    data class OnCreate(val activity: Activity, val savedInstanceState: Bundle?): ActivityLifeEvent()
    data class OnStart(val activity: Activity): ActivityLifeEvent()
    data class OnResume(val activity: Activity): ActivityLifeEvent()
    data class OnPause(val activity: Activity): ActivityLifeEvent()
    data class OnStop(val activity: Activity): ActivityLifeEvent()
    data class OnDestroy(val activity: Activity): ActivityLifeEvent()
    data class OnSaveInstanceState(val activity: Activity, val outState: Bundle?): ActivityLifeEvent()

    // Global activity stream not have this events:
    data class OnNewIntent(val activity: Activity, val intent: Intent?): ActivityLifeEvent()
    data class OnConfigurationChanged(val activity: Activity, val newConfig: Configuration?): ActivityLifeEvent()
    data class OnActivityResult(val activity: Activity, val requestCode: Int,
                                val resultCode: Int, val data: Intent?): ActivityLifeEvent()
}
//</editor-fold>

//<editor-fold defaultstate="collapsed" desc="Fragment Life">
sealed class FragmentLifeEvent {
    data class OnCreate(val fragment: Fragment, val savedInstanceState: Bundle?): FragmentLifeEvent()

    data class OnCreateView(val fragment: Fragment,
                            val inflater: LayoutInflater,
                            val container: ViewGroup?,
                            val savedInstanceState: Bundle?): FragmentLifeEvent()

    data class OnViewCreated(val fragment: Fragment, val view: View?, val savedInstanceState: Bundle?): FragmentLifeEvent()
    data class OnStart(val fragment: Fragment) : FragmentLifeEvent()
    data class OnResume(val fragment: Fragment) : FragmentLifeEvent()
    data class OnDestroy(val fragment: Fragment): FragmentLifeEvent()
}
//</editor-fold>


class TypeCheck<T>

private val typeFake = TypeCheck<Nothing>()

@Suppress("UNCHECKED_CAST")
fun <T> type(): TypeCheck<T> = typeFake as TypeCheck<T>



fun <T> Single<T>.toIO(): IO<T> = IO.async { connection, cb ->
    val disposable = subscribe({ cb(it.right()) },{ cb(it.left()) })
    connection.push(IO { disposable.dispose() })
}

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


fun RouteCxt.checkComponentClass(intent: Intent, obj: Any): Boolean =
    intent.component?.run {
        packageName == app.packageName && className == obj.javaClass.name
    } ?: false
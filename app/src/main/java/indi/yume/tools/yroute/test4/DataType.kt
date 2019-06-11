package indi.yume.tools.yroute.test4

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.CheckResult
import androidx.annotation.IdRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import arrow.core.*
import arrow.core.extensions.either.fx.fx
import arrow.core.extensions.either.monad.flatMap
import arrow.data.*
import arrow.data.extensions.kleisli.monad.monad
import arrow.data.extensions.list.foldable.find
import arrow.effects.ForIO
import arrow.effects.IO
import arrow.effects.MVar
import arrow.effects.extensions.io.applicativeError.handleError
import arrow.effects.extensions.io.async.async
import arrow.effects.extensions.io.fx.fx
import arrow.effects.extensions.io.monad.monad
import arrow.effects.extensions.io.monadDefer.binding
import arrow.effects.fix
import arrow.effects.typeclasses.Disposable
import arrow.generic.coproduct2.*
import arrow.generic.coproduct3.Coproduct3
import arrow.generic.coproduct3.fold
import arrow.optics.Lens
import arrow.optics.PLens
import arrow.optics.PSetter
import arrow.optics.Setter
import indi.yume.tools.yroute.Logger
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import java.lang.ClassCastException
import kotlin.random.Random

var application: Application? = null

fun test() {
    val yroute: YRoute<ActivitiesState, ActivityBuilder<Activity>, Intent> = routeF { vd, _, _ -> IO { vd toT Success(Intent()) } }
    val cxt: RouteCxt = RouteCxt.create(application!!).unsafeRunSync()
    val param: ActivityBuilder<Activity> = ActivityBuilder(Activity::class.java)

    val sumState: MVar<ForIO, ActivitiesState> = MVar.uncancelableOf(ActivitiesState(emptyList()), IO.async()).fix().unsafeRunSync()


    val result: ReaderT<ForIO, Tuple2<RouteCxt, ActivityBuilder<Activity>>, Tuple2<ActivitiesState, Result<Intent>>> =
        yroute.run(ReaderT.monad(IO.monad()), ActivitiesState(emptyList())).fix()

    val r: IO<Tuple2<ActivitiesState, Result<Intent>>> = result.run(cxt toT param).fix()

    binding {
        val (state) = sumState.take()

        val (tuple2) = yroute.run(ReaderT.monad(IO.monad()), state).run(cxt toT param)

        val (newState, result1) = tuple2

        !sumState.put(newState)

        result1
    }
}

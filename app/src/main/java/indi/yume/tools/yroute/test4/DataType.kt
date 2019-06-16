package indi.yume.tools.yroute.test4

import android.app.Activity
import android.app.Application
import android.content.Intent
import arrow.core.*
import arrow.data.*
import arrow.data.extensions.kleisli.monad.monad
import arrow.effects.ForIO
import arrow.effects.IO
import arrow.effects.MVar
import arrow.effects.extensions.io.async.async
import arrow.effects.extensions.io.monad.monad
import arrow.effects.extensions.io.monadDefer.binding
import arrow.effects.fix

var application: Application? = null

fun test() {
    val yroute: YRoute<ActivitiesState, Intent> = routeF { vd, _ -> IO { vd toT Success(Intent()) } }
    val cxt: RouteCxt = RouteCxt.create(application!!).unsafeRunSync()
    val param: ActivityBuilder<Activity> = ActivityBuilder(Activity::class.java)

    val sumState: MVar<ForIO, ActivitiesState> = MVar.uncancelableOf(ActivitiesState(emptyList()), IO.async()).fix().unsafeRunSync()


    val result: ReaderT<ForIO, RouteCxt, Tuple2<ActivitiesState, Result<Intent>>> =
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

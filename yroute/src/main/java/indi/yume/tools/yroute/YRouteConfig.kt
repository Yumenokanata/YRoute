package indi.yume.tools.yroute

import androidx.fragment.app.FragmentTransaction
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext

object YRouteConfig {
    var FragmentExecFunc: (FragmentTransaction) -> Unit = { it.commit() }

    var globalDefaultAnimData: AnimData? = null

    var showLog: Boolean = BuildConfig.DEBUG

    var taskRunnerTimeout: Long? = 5000

    var fragmentCreateContext: CoroutineContext = Dispatchers.IO
}

fun FragmentTransaction.routeExecFT(): Unit = YRouteConfig.FragmentExecFunc(this)
package indi.yume.tools.yroute

import androidx.fragment.app.FragmentTransaction

object YRouteConfig {
    var FragmentExecFunc: (FragmentTransaction) -> Unit = { it.commit() }

    var globalDefaultAnimData: AnimData? = null

    var showLog: Boolean = BuildConfig.DEBUG

    var taskRunnerTimeout: Long? = 5000
}

fun FragmentTransaction.routeExecFT(): Unit = YRouteConfig.FragmentExecFunc(this)
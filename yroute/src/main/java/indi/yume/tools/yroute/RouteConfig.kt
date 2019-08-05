package indi.yume.tools.yroute

import androidx.fragment.app.FragmentTransaction

object RouteConfig {
    var FragmentExecFunc: (FragmentTransaction) -> Unit = { it.commit() }

    var globalDefaultAnimData: AnimData? = null
}

fun FragmentTransaction.routeExecFT(): Unit = RouteConfig.FragmentExecFunc(this)
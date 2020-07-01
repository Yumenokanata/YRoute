package indi.yume.tools.yroute

object Logger {
    fun d(tag: String, msg: String) {
        if (YRouteConfig.showLog)
            println("${Thread.currentThread().name} => $tag | $msg")
    }

    fun d(tag: String, msgLazy: () -> String) {
        if (YRouteConfig.showLog)
            println("${Thread.currentThread().name} => $tag | ${msgLazy()}")
    }
}

//fun <T> logIO
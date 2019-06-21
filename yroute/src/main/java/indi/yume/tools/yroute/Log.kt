package indi.yume.tools.yroute

object Logger {
    fun d(tag: String, msg: String) {
        println("${Thread.currentThread().name} => $tag | $msg")
    }
}

//fun <T> logIO
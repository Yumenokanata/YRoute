package indi.yume.tools.yroute

import indi.yume.tools.yroute.datatype.Fail
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import org.junit.Test
import java.lang.RuntimeException
import java.net.URI
import java.util.concurrent.TimeUnit

class StringRouteTest {
    @Test
    fun stringConvertTest() {
//        val uri = URI("""route://www.other.com:80/yy/same?t1=12&t2=hjh""")
        val sh = Schedulers.single()

        val sb = PublishSubject.create<Int>()

        Observable.create<Int> { emitter ->
            emitter.onNext(1)
            emitter.onNext(2)
            emitter.onNext(3)
            emitter.onComplete()
        }.subscribeOn(Schedulers.computation())
                .concatMapSingle { i ->
                    Single.fromCallable {
                        Thread.sleep(10)
                        println("${Thread.currentThread().id} sss1=$i")
                        sh.scheduleDirect {
                            Thread.sleep(20)
                            println("${Thread.currentThread().id} sss2=$i")
                        }
//                        sb.onNext(22)
                        i
                    }.subscribeOn(sh)
//                            .observeOn(Schedulers.computation())
//                            .flatMap { Single.timer(2, TimeUnit.MILLISECONDS) }
                }
                .subscribe()

//        sb
//                .observeOn(sh)
//                .doOnNext {
//                    Thread.sleep(20)
//                    println("sss2=$it")
//                }.subscribe()


        Thread.sleep(3000)
    }

    @Test
    fun testExp() {
        try {
            val exp = RuntimeException("run time exp.")
            val fail = Fail("fail model.", exp)
            throw YRouteException(fail)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }
}
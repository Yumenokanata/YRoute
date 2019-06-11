package indi.yume.tools.yroute

import io.reactivex.Observable
import org.junit.Test

/**
 * To work on unit tests, switch the Test Artifact in the Build Variants view.
 */
typealias Worker<T> = (T) -> Unit

class RxTest {
    fun doWorker(time: Long) {
        Thread.sleep(time)
    }

    fun source(time: Long): Observable<Int> =
        Observable.create<Int> { emitter ->
            for (i in 0..100) {
                Thread.sleep(time)
                emitter.onNext(i)
            }
            emitter.onComplete()
        }

    data class Sample(val sendTime: (Int) -> Long,
                      val spendTime: (Int) -> Long,
                      val result: List<Int>)

    @Test
    fun rxTest() {
        val testSamples: List<Sample> = listOf(
            Sample({ 100 }, { 160 }, listOf(1, 2, 4)),
            Sample({ 160 }, { 100 }, listOf(1, 2, 3, 4)),
            Sample({ Math.round(200 * Math.pow(3.0 / 4, it.toDouble())) }, { 160 }, listOf(1, 2, 3, 5))
        )

        fun testRunner(sample: Sample) {
            val source = Observable.create<Int> { emitter ->
                for (i in 0..100) {
                    Thread.sleep(sample.sendTime(i))
                    emitter.onNext(i)
                }
                emitter.onComplete()
            }

            val worker: Worker<Int> = { Thread.sleep(sample.spendTime(it)) }

            val result = testFun(source, worker)
                .take(sample.result.size.toLong())
                .toList()
                .blockingGet()

            assert(result == sample.result) { "Error, test fail: result is ${result.joinToString()}, right is ${sample.result.joinToString()}" }
        }


        for (s in testSamples)
            testRunner(s)
    }

    val testFun: (Observable<Int>, Worker<Int>) -> Observable<Int> = { source, worker ->
        source
    }
}
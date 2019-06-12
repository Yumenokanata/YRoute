package indi.yume.tools.yroute

import arrow.core.Either
import arrow.core.None
import arrow.core.Some
import arrow.effects.ForIO
import arrow.effects.IO
import arrow.effects.extensions.io.async.async
import indi.yume.tools.yroute.test4.*
import org.junit.Test
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import arrow.effects.extensions.io.fx.fx
import arrow.effects.extensions.io.monad.monad
import io.kotlintest.properties.Gen
import io.kotlintest.properties.forAll
import java.io.BufferedReader
import java.util.*

class StreamTest {
    /*
     * An example use of the combinators we have so far: incrementally
     * convert the lines of a file from fahrenheit to celsius.
     */

    fun fahrenheitToCelsius(f: Double): Double = (f - 32) * 5.0 / 9.0

    val converter: Process<ForIO, Unit> =
        lines("fahrenheits.txt")
            .filter { line -> !line.startsWith("#") && !line.trim().isEmpty() }
            .map { line -> fahrenheitToCelsius(line.toDouble()).toString() }
            .pipe(Process.intersperse("\n"))
            .to(fileW("celsius.txt"))
            .drain()

    /*
     * Create a `Process<IO, O>` from the lines of a file, using
     * the `resource` combinator above to ensure the file is closed
     * when processing the stream of lines is finished.
     */
    fun lines(filename: String): Process<ForIO, String> =
        Process.resource(IO { FileReader(filename) },
            { src ->
                val iter by lazy { src.readLines().listIterator() } // a stateful iterator
                fun step() = if (iter.hasNext()) Some(iter.next()) else None

                fun lines(): Process<ForIO, String> =
                    Process.eval(IO { step() }).flatMap {
                        when (it) {
                            is None -> Halt<ForIO, String>(End)
                            is Some -> Emit(it.t, lines())
                        }
                    }
                lines()
            },
            { src -> Process.eval_(IO { src.close() }) })

    /* A `Sink` which writes input strings to the given file. */
    fun fileW(file: String, append: Boolean = false): Sink<ForIO, String> =
        Process.resource<FileWriter, (String) -> Process<ForIO, Unit>>(
            IO { FileWriter(file, append) },
            { w -> Process.constant { s: String -> Process.eval<ForIO, Unit>(IO { w.write(s) }) } },
            { w -> Process.eval_(IO { w.close() }) })

    val printlnW: Sink<ForIO, String> =
        Process.resource<Unit, (String) -> Process<ForIO, Unit>>(
            IO { Unit },
            { w -> Process.constant { s: String -> Process.eval<ForIO, Unit>(IO { println(s) }) } },
            { w -> Process.eval_(IO { }) })

    /*
     * We can write a version of collect that works for any `Monad`.
     * See the definition in the body of `Process`.
     */

    val p: Process<ForIO, String> =
        await(IO { BufferedReader(FileReader("lines.txt")) }) { eBuffer ->
            when (eBuffer) {
                is Either.Right -> {
                    fun next(): Process<ForIO, String> = await(IO { eBuffer.b.readLine() }) { eLine ->
                        when (eLine) {
                            is Either.Left -> await(IO { eBuffer.b.close() }) { Halt<ForIO, String>(eLine.a) }
                            is Either.Right -> Emit(eLine.b, next())
                        }
                    }
                    next()
                }
                is Either.Left -> Halt(eBuffer.a)
            }
        }

    /*
     * We can allocate resources dynamically when defining a `Process`.
     * As an example, this program reads a list of filenames to process
     * _from another file_, opening each file, processing it and closing
     * it promptly.
     */

    val convertAll: Process<ForIO, Unit> =
        fileW("celsius.txt").once().flatMap { out ->
            lines("fahrenheits.txt").flatMap { file ->
                lines(file).map { line -> fahrenheitToCelsius(line.toDouble()) }.flatMap { celsius ->
                    out(celsius.toString())
                }
            }
        }.drain<Unit>()


    /*
     * Just by switching the order of the `flatMap` calls, we can output
     * to multiple files.
     */
    val convertMultisink: Process<ForIO, Unit> =
        lines("fahrenheits.txt").flatMap { file ->
            lines(file).map { line -> fahrenheitToCelsius(line.toDouble()) }
                .map { it.toString() }.to(fileW("$file.celsius"))
        }.drain<Unit>()

    /*
     * We can attach filters or other transformations at any point in the
     * program, for example:
     */
    val convertMultisink2: Process<ForIO, Unit> =
        lines("fahrenheits.txt").flatMap { file ->
            lines(file).filter { !it.startsWith("#") }
                .map { line -> fahrenheitToCelsius(line.toDouble()) }
                .filter { it > 0 }
                .map { it.toString() }
                .to(fileW("$file.celsius"))
        }.drain<Unit>()

    @Test
    fun streamTest() {
        println(File("").absolutePath)
        val p = Process.eval(IO { println("woot"); 1 }).repeat()
        val p2 = Process.eval(IO { println("cleanup"); 2 }).onHalt {
            when (it) {
                is Kill -> {
                    println { "cleanup was killed, instead of bring run" }
                    Halt(Kill)
                }
                else -> Halt(it)
            }
        }

        println(Process.runLog(p2.onComplete { p2 }.onComplete { p2 }.take(1).take(1)).unsafeRunSync().joinToString())
        println(Process.runLog(converter).attempt().unsafeRunSync().toString())
        //     println { Process.collect(Process.convertAll) }
    }

    @Test
    fun outputTest() {
//        val randomRes = Process.resource<Random, (String) -> Process<ForIO, Unit>>(
//            IO { FileWriter(file, append) },
//            { w -> Process.constant { s: String -> Process.eval<ForIO, Unit>(IO { w.write(s) }) } },
//            { w -> Process.eval_(IO { w.close() }) })
//
//        lines("fahrenheits.txt")
//            .filter { line -> !line.startsWith("#") && !line.trim().isEmpty() }
//            .map { line -> fahrenheitToCelsius(line.toDouble()).toString() }
//            .pipe(Process.intersperse("\n"))
//            .to(fileW("celsius.txt"))
//            .drain()

        val out = Process.fromList(IO.monad(), (0..999).map { "item$it" })
            .pipe(Process.intersperse("\n"))
            .to(fileW("celsius.txt"))
            .drain<Unit>()

        println(Process.runLog(out).attempt().unsafeRunSync().toString())
    }
}
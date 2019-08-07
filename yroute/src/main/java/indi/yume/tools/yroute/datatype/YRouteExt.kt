package indi.yume.tools.yroute.datatype

import arrow.Kind
import arrow.core.*
import arrow.data.*

import arrow.extension
import arrow.effects.extensions.io.monadDefer.binding
import arrow.data.extensions.statet.applicative.applicative
import arrow.data.extensions.statet.functor.functor
import arrow.data.extensions.statet.monad.monad
import arrow.effects.IO
import arrow.effects.extensions.io.monad.monad
import arrow.effects.fix
import arrow.effects.handleErrorWith
import arrow.typeclasses.*
import arrow.typeclasses.suspended.monad.commutative.safe.Fx
import indi.yume.tools.yroute.YRouteException

//@extension
interface YRouteFunctor<S> : Functor<YRoutePartialOf<S>> {

    override fun <A, B> YRouteOf<S, A>.map(f: (A) -> B): YRoute<S, B> =
            fix().mapResult(f)
}

//@extension
interface YRouteApplicative<S> : Applicative<YRoutePartialOf<S>>, YRouteFunctor<S> {

    override fun <A, B> YRouteOf<S, A>.map(f: (A) -> B): YRoute<S, B> =
            fix().mapResult(f)

    override fun <A> just(a: A): YRoute<S, A> =
            routeF { s, cxt -> IO.just(s toT Success(a)) }

    override fun <A, B> YRouteOf<S, A>.ap(ff: YRouteOf<S, (A) -> B>): YRoute<S, B> =
            routeF { s, routeCxt ->
                binding {
                    val (innerState, aResult) = !fix().runRoute(s, routeCxt)
                    when(aResult) {
                        is Success -> {
                            val (newState, fResult) = !ff.fix().runRoute(innerState, routeCxt)
                            newState toT fResult.map { it(aResult.t) }
                        }
                        is Fail -> innerState toT aResult
                    }
                }
            }

    override fun <A, B> YRouteOf<S, A>.product(fb: YRouteOf<S, B>): YRoute<S, Tuple2<A, B>> =
            ap(fb.map { b -> { a: A -> a toT b } })

}

//@extension
interface YRouteMonad<S> : Monad<YRoutePartialOf<S>>, YRouteApplicative<S> {

    override fun <A, B> YRouteOf<S, A>.map(f: (A) -> B): YRoute<S, B> =
            fix().mapResult(f)

    override fun <A, B> YRouteOf<S, A>.flatMap(f: (A) -> YRouteOf<S, B>): YRoute<S, B> =
            fix().flatMapR { f(it).fix() }

    override fun <A, B> tailRecM(a: A, f: (A) -> YRouteOf<S, Either<A, B>>): YRoute<S, B> =
            routeF { s, routeCxt ->
                var newState = s

                IO.tailRecM(a) { aInner ->
                    f(aInner).fix().runRoute(newState, routeCxt).map { (innerState, result) ->
                        newState = innerState
                        when (result) {
                            is Fail -> (innerState toT result).right()
                            is Success -> when(result.t) {
                                is Either.Left -> result.t.a.left()
                                is Either.Right -> (innerState toT Success(result.t.b)).right()
                            }
                        }
                    }
                }
            }

    override fun <A, B> YRouteOf<S, A>.ap(ff: YRouteOf<S, (A) -> B>): YRoute<S, B> =
            routeF { s, routeCxt ->
                IO.monad().binding {
                    val (innerState, aResult) = !fix().runRoute(s, routeCxt)
                    when(aResult) {
                        is Success -> {
                            val (newState, fResult) = !ff.fix().runRoute(innerState, routeCxt)
                            newState toT fResult.map { it(aResult.t) }
                        }
                        is Fail -> innerState toT aResult
                    }
                }.fix()
            }

}

//@extension
interface YRouteApplicativeError<S> : ApplicativeError<YRoutePartialOf<S>, Throwable>, YRouteApplicative<S> {

    override fun <A> raiseError(e: Throwable): YRouteOf<S, A> =
            routeF { s, routeCxt -> IO.just(s toT Fail("raiseError", e)) }

    override fun <A> YRouteOf<S, A>.handleErrorWith(f: (Throwable) -> YRouteOf<S, A>): YRoute<S, A> =
            routeF { s, routeCxt ->
                IO.monad().binding {
                    val (newState, result) = !this@handleErrorWith.fix().runRoute(s, routeCxt)
                            .handleErrorWith { t ->
                                f(t).fix().runRoute(s, routeCxt)
                            }

                    when (result) {
                        is Fail -> !f(result.error ?: YRouteException(result)).fix().runRoute(newState, routeCxt)
                        is Success -> newState toT result
                    }
                }.fix()
            }
}

//@extension
interface YRouteMonadError<S> : MonadError<YRoutePartialOf<S>, Throwable>, YRouteApplicativeError<S>, YRouteMonad<S> {

}

//@extension
interface YRouteMonadThrow<S> : MonadThrow<YRoutePartialOf<S>>, YRouteMonadError<S> {
}

//@extension
interface YRouteFx<S> : Fx<YRoutePartialOf<S>> {

    override fun monad(): Monad<YRoutePartialOf<S>> =
            YRoute.monad()

}

fun <S> YRoute.Companion.functor(): YRouteFunctor<S> = object : YRouteFunctor<S> { }

fun <S> YRoute.Companion.applicative(): YRouteApplicative<S> = object : YRouteApplicative<S> { }

fun <S> YRoute.Companion.applicativeError(): YRouteApplicativeError<S> = object : YRouteApplicativeError<S> { }

fun <S> YRoute.Companion.monad(): YRouteMonad<S> = object : YRouteMonad<S> { }

fun <S> YRoute.Companion.monadError(): YRouteMonadError<S> = object : YRouteMonadError<S> { }

fun <S> YRoute.Companion.monadThrow(): YRouteMonadThrow<S> = object : YRouteMonadThrow<S> { }

fun <S> YRoute.Companion.fx(): YRouteFx<S> = object : YRouteFx<S> { }

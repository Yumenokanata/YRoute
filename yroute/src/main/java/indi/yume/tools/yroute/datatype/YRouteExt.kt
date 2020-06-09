package indi.yume.tools.yroute.datatype

import arrow.core.*
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
            routeF { s, cxt -> s toT Success(a) }

    override fun <A, B> YRouteOf<S, A>.ap(ff: YRouteOf<S, (A) -> B>): YRoute<S, B> =
            routeF { s, routeCxt ->
                val (innerState, aResult) = fix().runRoute(s, routeCxt)
                when (aResult) {
                    is Success -> {
                        val (newState, fResult) = ff.fix().runRoute(innerState, routeCxt)
                        newState toT fResult.map { it(aResult.t) }
                    }
                    is Fail -> innerState toT aResult
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

                tailrec suspend fun runner(param: A): Tuple2<S, YResult<B>> {
                    val (innerState, result) = f(param).fix().runRoute(newState, routeCxt)
                    newState = innerState

                    return when (result) {
                        is Fail -> innerState toT result
                        is Success -> when(result.t) {
                            is Either.Left -> runner(result.t.a)
                            is Either.Right -> innerState toT Success(result.t.b)
                        }
                    }
                }

                runner(a)
            }

    override fun <A, B> YRouteOf<S, A>.ap(ff: YRouteOf<S, (A) -> B>): YRoute<S, B> =
            routeF { s, routeCxt ->
                val (innerState, aResult) = fix().runRoute(s, routeCxt)
                when (aResult) {
                    is Success -> {
                        val (newState, fResult) = ff.fix().runRoute(innerState, routeCxt)
                        newState toT fResult.map { it(aResult.t) }
                    }
                    is Fail -> innerState toT aResult
                }
            }

}

//@extension
interface YRouteApplicativeError<S> : ApplicativeError<YRoutePartialOf<S>, Throwable>, YRouteApplicative<S> {

    override fun <A> raiseError(e: Throwable): YRouteOf<S, A> =
            routeF { s, routeCxt -> s toT Fail("raiseError", e) }

    override fun <A> YRouteOf<S, A>.handleErrorWith(f: (Throwable) -> YRouteOf<S, A>): YRoute<S, A> =
            routeF { s, routeCxt ->
                val (newState, result) = try {
                    this@handleErrorWith.fix().runRoute(s, routeCxt)
                } catch (t: Throwable) {
                    f(t).fix().runRoute(s, routeCxt)
                }

                when (result) {
                    is Fail -> f(result.error
                            ?: YRouteException(result)).fix().runRoute(newState, routeCxt)
                    is Success -> newState toT result
                }
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

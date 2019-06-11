package indi.yume.tools.yroute

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.annotation.IdRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import arrow.Kind
import arrow.core.ForTuple2
import arrow.effects.ForIO
import arrow.effects.IO
import arrow.effects.MVar
import arrow.effects.extensions.io.async.async
import arrow.effects.extensions.io.monad.binding
import arrow.effects.extensions.io.monad.flatMap
import arrow.effects.fix
import arrow.generic.coproduct10.Coproduct10
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.Subject
import java.lang.RuntimeException
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicReference

/**
typealias TableTag = String

sealed class Piece

//object NullPiece : Piece()
//
//object RootPiece : Piece()
//
//data class SinglePiece(val cxt: Context, val tag: String, val item: Any) : Piece()
//
//data class StackPiece(val stack: List<Piece>) : Piece()
//
//data class TablePiece(val table: Map<TableTag, Piece>, val current: Pair<TableTag, Piece>) : Piece()
//
//
//fun Piece.findFirst(matcher: (SinglePiece) -> Boolean): SinglePiece? = when(this) {
//    is NullPiece, is RootPiece -> null
//    is SinglePiece -> if (matcher(this)) this else null
//    is StackPiece -> stack.firstOrNull()?.findFirst(matcher)
//    is TablePiece -> current.second.findFirst(matcher)
//}

object NullPiece : Piece()

data class ActivityPiece(val tag: String, val activity: Activity) : Piece()

data class FActivityPiece(val tag: String, val activity: ContainerActivity, val stack: StackType) : Piece()

data class FragmentPiece(val tag: String, val container: FActivityPiece, val fragment: Fragment) : Piece()

data class ViewPiece(val tag: String, val view: View) : Piece()


interface ContainerActivity {
    val activity: Activity

    @get:IdRes
    val fragmentId: Int
}

sealed class StackType {
    data class Single(val list: List<FragmentPiece> = emptyList()) : StackType()

    data class Table(val table: Map<TableTag, List<FragmentPiece>>,
                     val current: Pair<TableTag, FragmentPiece>?) : StackType()
}


fun Piece.getTop(): Piece = when(this) {
    is NullPiece -> this
    is ActivityPiece -> this
    is FActivityPiece -> when(stack) {
        is StackType.Single -> stack.list.lastOrNull() ?: this
        is StackType.Table -> stack.current?.second ?: this
    }
    is FragmentPiece -> this
    is ViewPiece -> this
}


sealed class YView

class YActivity(val activity: Activity): YView()

class YFragment(val fragment: Fragment): YView()

class YSubView(val view: View): YView()


//
//
////fun Piece.push
//
//
//interface PieceCxt
//
//interface PieceActivityCxt : PieceCxt {
//    val activity: Activity
//}
//
//interface PieceInnerCxt : PieceCxt
//
//
//class ActivityCxt(val activity: Activity) : PieceCxt
//
//class FragmentActivityCxt(val activity: FragmentActivity) : PieceCxt
//
//class FragmentCxt(val fragment: Fragment) : PieceInnerCxt
//
//
//fun test() {
//
//}
//
///**
// * Side effect
// */
//class IO<T>(val run: () -> T)
//
//typealias RAction<T> = RouteContext.() -> T
//
//class RouteContext(val pieceStore: ValueStore<Piece>)
//
//class ValueStore<T>(initState: T) {
//    val subject: Subject<T> = BehaviorSubject.createDefault<T>(initState).toSerialized()
//
//    fun value(): Observable<T> = subject
//
//    fun set(newValue: T) = subject.onNext(newValue)
//
//    fun reduce(f: (T) -> T): Single<T> = value().firstOrError()
//        .map { oldValue ->
//            val newValue = f(oldValue)
//            subject.onNext(newValue)
//            newValue
//        }
//}
//
//class Route(appliction: Application) {
//    val routeCxt: RouteContext = RouteContext(ValueStore(RootPiece(appliction)))
//
//    fun <T> doAction(act: RAction<T>): T = routeCxt.act()
//}


//fun startActivity(intent: Intent): RAction<Completable> = {
//    pieceStore.value().firstElement()
//        .flatMapCompletable { piece ->
//            fun startInner(p: Piece): Completable = when (p) {
//                is RootPiece -> Completable.fromAction {
//                    p.appliction.startActivity(intent)
//                    pieceStore.reduce {
//
//                    }
//                }
//                is SinglePiece -> {
//                    when (val cxt = p.cxt) {
//                        is ActivityCxt -> Completable.fromAction { cxt.activity.startActivity(intent) }
//                        is FragmentActivityCxt -> Completable.fromAction { cxt.activity.startActivity(intent) }
//                        is FragmentCxt -> Completable.fromAction { cxt.fragment.startActivity(intent) }
//                        else -> Completable.complete()
//                    }
//                }
//                is StackPiece -> {
//                    when (val cxt = p.cxt) {
//                        is ActivityCxt -> Completable.fromAction { cxt.activity.startActivity(intent) }
//                        is FragmentActivityCxt -> Completable.fromAction { cxt.activity.startActivity(intent) }
//                        is FragmentCxt -> Completable.fromAction { cxt.fragment.startActivity(intent) }
//                        else -> Completable.complete()
//                    }
//                }
//                else -> Completable.complete()
//            }
//            startInner(piece)
//        }
//}



//typealias TableTag = String
//
//class ForPiece private constructor() { companion object }
//typealias PieceOf<A> = arrow.Kind<ForPiece, A>
//
//@Suppress("UNCHECKED_CAST", "NOTHING_TO_INLINE")
//inline fun <A> PieceOf<A>.fix(): Piece<A> =
//    this as Piece<A>
//
//
//
//sealed class Piece<C> : PieceOf<C> {
//    abstract val cxt: C
//}
//
//object NullPiece : Piece<Unit>() {
//    override val cxt: Unit = Unit
//}
//
//data class SinglePiece<C>(override val cxt: C) : Piece<C>()
//
//data class StackPiece<C, L : HListK<ForPiece, L>>(override val cxt: C, val stack: L) : Piece<C>()
//
//data class TablePiece<C, PC, P : Piece<PC>, L : HListK<Kind<ForPiece, TableTag>, L>>(override val cxt: C, val table: L, val current: Pair<TableTag, P>) : Piece<C>()
//
//
//data class TableItem<C, P : Piece<C>>(val tag: TableTag, val piece: P) : TableItemOf<C, P>
//
//class ForTableItem private constructor() { companion object }
//typealias TableItemOf<A, B> = arrow.Kind2<ForTableItem, A, B>
//
//@Suppress("UNCHECKED_CAST", "NOTHING_TO_INLINE")
//inline fun <A, B : Piece<A>> TableItemOf<A, B>.fix(): TableItem<A, B> =
//    this as TableItem<A, B>
//
//
//
//fun test() {
//
//}
//
///**
// * Side effect
// */
//class IO<T>(val run: () -> T)
//
//class RouteContext<C, P : Piece<C>>(val piece: ValueStore<P>)
//
class ValueStore<T>(initState: T) {
    val subject: Subject<T> = BehaviorSubject.createDefault<T>(initState).toSerialized()

    fun value(): Observable<T> = subject

    fun set(newValue: T) = subject.onNext(newValue)

    fun reduce(f: (T) -> T): Single<T> = value().firstOrError()
        .map { oldValue ->
            val newValue = f(oldValue)
            subject.onNext(newValue)
            newValue
        }
}
//
//class Route<> {
//    val routeCxt: RouteContext = RouteContext()
//
//    fun doAction()
//}

class RouteCxt(val app: Application) {
    var currentContext: WeakReference<Context?> = WeakReference(null)

    val state: MVar<ForIO, Piece> = MVar.uncancelableOf<ForIO, Piece>(NullPiece, IO.async()).fix().unsafeRunSync()
}


sealed class Result<out T>

class Success<T>(val t: T) : Result<T>()
class Fail(val message: String, val error: Throwable?) : Result<Nothing>()


//typealias IOAction<T> = () -> T
//
//fun <T> IOAction<T>.run(): Result<T> =
//    try {
//        Success(this@run.invoke())
//    } catch (t: Throwable) {
//        Fail(t.message ?: "run IOAction has error.", t)
//    }

//fun <R> IOAction<Result<R>>.toRx(): Single<R> = Single.fromCallable {
//    when (val result = this@toRx.run()) {
//        is Success -> when(result.t) {
//            is Success -> result.t.t
//            is Fail -> throw (result.t.error ?: RuntimeException(result.t.message))
//        }
//        is Fail -> throw (result.error ?: RuntimeException(result.message))
//    }
//}

typealias RouteAction<T> = RouteCxt.() -> T


interface YRoute<T, R> : YRouteOf<T, R> {
    fun jump(target: T): RouteAction<IO<Result<R>>>
}

class ForYRoute private constructor() { companion object }
typealias YRouteOf<A, B> = arrow.Kind<ForYRoute, Pair<A, B>>

@Suppress("UNCHECKED_CAST", "NOTHING_TO_INLINE")
inline fun <A, B> YRouteOf<A, B>.fix(): YRoute<A, B> =
    this as YRoute<A, B>

//infix fun <T1, R1, T2, R2> YRoute<T1, R1>.stick(route2: YRoute<T2, R2>): YRoute<>

infix fun <T, R1, R> YRoute<T, R1>.compose(route2: YRoute<R1, R>): YRoute<T, R> = object : YRoute<T, R> {
    override fun jump(target: T): RouteAction<IO<Result<R>>> = {
        this@compose.jump(target)().flatMap { result1 ->
            when (result1) {
                is Success -> route2.jump(result1.t)()
                is Fail -> IO<Result<R>> { result1 }
            }
        }
    }
}
//        IO {
//            val action : RouteAction<Result<R>> = {
//                when(val result1 = it()) {
//                    is Success -> route2.jump(result1.t).unsafeRunSync()()
//                    is Fail -> result1
//                }
//            }
//            action
//        }


object YActivityRoute : YRoute<ActivityBuilder<Activity>, Unit> {
    override fun jump(target: ActivityBuilder<Activity>): RouteAction<IO<Result<Unit>>> = {
        binding {
            val (currentState) = state.take()
            val top = currentState.findFirst { true }

            (top?.cxt ?: app).startActivity(target.intent)

            when (currentState) {
                is
            }
            state.take()

            Success(Unit)
        }
    }
}

class ActivityBuilder<A : Activity>(val intent: Intent)

fun <A: Activity> ActivityBuilder<A>.with(param: Bundle): ActivityBuilder<A> = ActivityBuilder(intent.putExtras(param))

**/

package indi.yume.tools.yroute

import arrow.Kind
import java.util.*

/**
 * An Heterogeneous list of values that preserves type information
 *
 * LIFO (Last In, First out)
 */
sealed class HListK<F, A: HListK<F, A>> {
    // Add item at top.
    abstract fun <T> extend(e: Kind<F, T>): HConsK<F, T, A>

    fun size(): Int = foldRec<F, Int>(0 to this) { _, _ -> 1 }.first

    fun <R> fold(initV: R, f: (Kind<F, *>, R) -> R): R =
        foldRec(initV to this) { v, r -> f(v, r) }.first

    fun find(f: (Kind<F, *>) -> Boolean): Kind<F, *>? =
        fold<Kind<F, *>?>(null) { v, r -> if (r == null && f(v)) v else r }

    fun toList(): List<Kind<F, *>> = fold(LinkedList<Kind<F, *>>())
    { repo, list -> list.addFirst(repo); list }.toList()

    companion object {
        fun <F> nil(): HNilK<F> = HNilK()

        fun <F, E, L: HListK<F, L>> cons(e: Kind<F, E>, l: L): HConsK<F, E, L> = HConsK(e, l)

        fun <F, E> single(e: Kind<F, E>): HConsK<F, E, HNilK<F>> = HConsK(e, nil())
    }
}


class HNilK<F> : HListK<F, HNilK<F>>() {
    override fun <T> extend(e: Kind<F, T>): HConsK<F, T, HNilK<F>> = cons(e, this)
}

data class HConsK<F, E, L: HListK<F, L>>(val head: Kind<F, E>, val tail: L) : HListK<F, HConsK<F, E, L>>() {
    override fun <T> extend(e: Kind<F, T>): HConsK<F, T, HConsK<F, E, L>> = cons(e, this)
}

fun <F, T> Kind<F, T>.toHListK(): HConsK<F, T, HNilK<F>> = HListK.single(this)

fun <F, T> hlistKOf(v1: Kind<F, T>): HConsK<F, T, HNilK<F>> = HListK.single(v1)

fun <F, T1, T2> hlistKOf(v1: Kind<F, T1>, v2: Kind<F, T2>): HConsK<F, T2, HConsK<F, T1, HNilK<F>>> =
    HListK.single(v1).extend(v2)

fun <F, T1, T2, T3> hlistKOf(v1: Kind<F, T1>, v2: Kind<F, T2>, v3: Kind<F, T3>)
        : HConsK<F, T3, HConsK<F, T2, HConsK<F, T1, HNilK<F>>>> =
    HListK.single(v1).extend(v2).extend(v3)

fun <F, T1, T2, T3, T4> hlistKOf1(v1: Kind<F, T1>, v2: Kind<F, T2>, v3: Kind<F, T3>, v4: Kind<F, T4>)
        : HConsK<F, T4, HConsK<F, T3, HConsK<F, T2, HConsK<F, T1, HNilK<F>>>>> =
    HListK.single(v1).extend(v2).extend(v3).extend(v4)

fun <F, T1, T2, T3, T4, T5> hlistKOf(v1: Kind<F, T1>, v2: Kind<F, T2>, v3: Kind<F, T3>, v4: Kind<F, T4>, v5: Kind<F, T5>)
        : HConsK<F, T5, HConsK<F, T4, HConsK<F, T3, HConsK<F, T2, HConsK<F, T1, HNilK<F>>>>>> =
    HListK.single(v1).extend(v2).extend(v3).extend(v4).extend(v5)


private typealias FoldData<F, T> = Pair<T, HListK<F, *>>

private tailrec fun <F, T> foldRec(data: FoldData<F, T>, f: (Kind<F, *>, T) -> T): FoldData<F, T> {
    val (value, list) = data
    return when(list) {
        is HNilK -> data
        is HConsK<F, *, *> -> foldRec(f(list.head, value) to (list.tail as HListK<F, *>), f)
    }
}
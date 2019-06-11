package indi.yume.tools.yroute.test4

import arrow.Kind

/**
interface CoproductK<F>


sealed class CoproductK2<F, A, B> : CoproductK<F> {
    data class First<F, A, B>(val a: A) : CoproductK2<F, A, B>()

    data class Second<F, A, B>(val b: B) : CoproductK2<F, A, B>()
}


object Cop2 {
    fun <F, A, B> first(a: A): CoproductK2<F, A, B> = CoproductK2.First(a)

    fun <F, A, B> second(b: B): CoproductK2<F, A, B> = CoproductK2.Second(b)
}

object Test1 {
    sealed class CopK<F, A: CopK<F, A>>

    class CopConsK<F> : CopK<F, CopConsK<F>>()

    data class HConsK<F, E, L: CopK<F, L>>(val head: Kind<F, E>, val tail: L) : CopK<F, HConsK<F, E, L>>()
}

object Test2 {
    sealed class CopK<A: CopK<A>>

    class CopConsK<T> : CopK<CopK<T>>(val t: T)

    data class HConsK<F, E, L: CopK<F, L>>(val head: Kind<F, E>, val tail: L) : CopK<F, HConsK<F, E, L>>()
}

**/
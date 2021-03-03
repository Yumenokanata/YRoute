package indi.yume.tools.yroute.fragmentmanager

import androidx.fragment.app.Fragment
import indi.yume.tools.yroute.*
import indi.yume.tools.yroute.datatype.startLazy

abstract class BaseSingleActivity<F> : BaseFragmentManagerActivity<F, StackType.Single<F>>()
        where F : Fragment, F : StackFragment


suspend fun <A, F> A.getCurrentStackSize(): Int where F : Fragment, F : StackFragment, A : BaseFragmentManagerActivity<F, StackType.Single<F>> =
        StackRoute.routeGetStackFromActivity(this).startLazy(core).flattenForYRoute()
                .let { it.stack.list.size }

suspend fun <A, F> A.getCurrentFragment(): F? where F : Fragment, F : StackFragment, A : BaseFragmentManagerActivity<F, StackType.Single<F>> =
        StackRoute.routeGetStackFromActivity(this).startLazy(core).flattenForYRoute()
                .let { it.stack.list.lastOrNull()?.t }

suspend fun <A, F> A.clearCurrentStack(): Boolean
        where F : Fragment, F : StackFragment, A : BaseFragmentManagerActivity<F, StackType.Single<F>> = StackRoute.run {
    routeClearCurrentStackForSingle<F>() runAtA this@clearCurrentStack
}.startLazy(core).flattenForYRoute()

suspend fun <A, F> A.backToTop(): Boolean
        where F : Fragment, F : StackFragment, A : BaseFragmentManagerActivity<F, StackType.Single<F>> = StackRoute.run {
    routeBackToTopForSingle<F>() runAtA this@backToTop
}.startLazy(core).flattenForYRoute()

suspend fun <A, F> A.getTopOfStack(): F?
        where F : Fragment, F : StackFragment, A : BaseFragmentManagerActivity<F, StackType.Single<F>> = StackRoute.run {
    getTopOfStackForSingle<F>() runAtA this@getTopOfStack
}.startLazy(core).flattenForYRoute()

suspend fun <A, F> A.isTopOfStack(fragment: F): Boolean
        where F : Fragment, F : StackFragment, A : BaseFragmentManagerActivity<F, StackType.Single<F>> =
        getTopOfStack() === fragment
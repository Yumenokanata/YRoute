package indi.yume.tools.yroute.fragmentmanager

import androidx.fragment.app.Fragment
import arrow.effects.IO
import indi.yume.tools.yroute.*
import indi.yume.tools.yroute.datatype.start

abstract class BaseSingleActivity<F> : BaseFragmentManagerActivity<F, StackType.Single<F>>()
        where F : Fragment, F : StackFragment


fun <A, F> A.getCurrentStackSize(): IO<Int> where F : Fragment, F : StackFragment, A : BaseFragmentManagerActivity<F, StackType.Single<F>> =
        StackRoute.routeGetStackFromActivity(this).start(core).flattenForYRoute()
                .map { it.stack.list.size }

fun <A, F> A.getCurrentFragment(): IO<F?> where F : Fragment, F : StackFragment, A : BaseFragmentManagerActivity<F, StackType.Single<F>> =
        StackRoute.routeGetStackFromActivity(this).start(core).flattenForYRoute()
                .map { it.stack.list.lastOrNull()?.t }

fun <A, F> A.clearCurrentStack(): IO<Boolean>
        where F : Fragment, F : StackFragment, A : BaseFragmentManagerActivity<F, StackType.Single<F>> = StackRoute.run {
    routeClearCurrentStackForSingle<F>() runAtA this@clearCurrentStack
}.start(core).flattenForYRoute()

fun <A, F> A.backToTop(): IO<Boolean>
        where F : Fragment, F : StackFragment, A : BaseFragmentManagerActivity<F, StackType.Single<F>> = StackRoute.run {
    routeBackToTopForSingle<F>() runAtA this@backToTop
}.start(core).flattenForYRoute()

fun <A, F> A.getTopOfStack(): IO<F?>
        where F : Fragment, F : StackFragment, A : BaseFragmentManagerActivity<F, StackType.Single<F>> = StackRoute.run {
    getTopOfStackForSingle<F>() runAtA this@getTopOfStack
}.start(core).flattenForYRoute()

fun <A, F> A.isTopOfStack(fragment: F): IO<Boolean>
        where F : Fragment, F : StackFragment, A : BaseFragmentManagerActivity<F, StackType.Single<F>> =
        getTopOfStack().map { it == fragment }
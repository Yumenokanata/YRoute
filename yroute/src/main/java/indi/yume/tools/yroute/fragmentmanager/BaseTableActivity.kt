package indi.yume.tools.yroute.fragmentmanager

import androidx.fragment.app.Fragment
import arrow.effects.IO
import indi.yume.tools.yroute.StackFragment
import indi.yume.tools.yroute.StackRoute
import indi.yume.tools.yroute.StackType
import indi.yume.tools.yroute.datatype.mapResult
import indi.yume.tools.yroute.datatype.start
import indi.yume.tools.yroute.flattenForYRoute

abstract class BaseTableActivity<F> : BaseFragmentManagerActivity<F, StackType.Table<F>>()
        where F : Fragment, F : StackFragment


fun <A, F> A.getCurrentStackTag(): IO<String?>
        where F : Fragment, F : StackFragment, A : BaseFragmentManagerActivity<F, StackType.Table<F>> =
        StackRoute.routeGetStackFromActivity(this).start(core).flattenForYRoute()
                .map { it.stack.current?.first }

fun <A, F> A.getCurrentStackSize(): IO<Int>
        where F : Fragment, F : StackFragment, A : BaseFragmentManagerActivity<F, StackType.Table<F>> =
        StackRoute.routeGetStackFromActivity(this).start(core).flattenForYRoute()
                .map { it.stack.table[it.stack.current?.first]?.size ?: 0 }

fun <A, F> A.getCurrentFragment(): IO<F?>
        where F : Fragment, F : StackFragment, A : BaseFragmentManagerActivity<F, StackType.Table<F>> =
        StackRoute.routeGetStackFromActivity(this).start(core).flattenForYRoute()
                .map { it.stack.current?.second?.t }

fun <A, F> A.switchToStackByTag(tag: String): IO<Boolean>
        where F : Fragment, F : StackFragment, A : BaseFragmentManagerActivity<F, StackType.Table<F>> = StackRoute.run {
    routeSwitchTag<F>(tag).mapResult { it != null } runAtA this@switchToStackByTag
}.start(core).flattenForYRoute()

fun <A, F> A.clearCurrentStack(resetStack: Boolean = false): IO<Boolean>
        where F : Fragment, F : StackFragment, A : BaseFragmentManagerActivity<F, StackType.Table<F>> = StackRoute.run {
    routeClearCurrentStackForTable<F>(resetStack) runAtA this@clearCurrentStack
}.start(core).flattenForYRoute()

fun <A, F> A.backToTop(): IO<Boolean>
        where F : Fragment, F : StackFragment, A : BaseFragmentManagerActivity<F, StackType.Table<F>> = StackRoute.run {
    routeBackToTopForTable<F>() runAtA this@backToTop
}.start(core).flattenForYRoute()

fun <A, F> A.getTopOfStack(): IO<F?>
        where F : Fragment, F : StackFragment, A : BaseFragmentManagerActivity<F, StackType.Table<F>> = StackRoute.run {
    getTopOfStackForTable<F>() runAtA this@getTopOfStack
}.start(core).flattenForYRoute()

fun <A, F> A.isTopOfStack(fragment: F): IO<Boolean>
        where F : Fragment, F : StackFragment, A : BaseFragmentManagerActivity<F, StackType.Table<F>> =
        getTopOfStack().map { it == fragment }
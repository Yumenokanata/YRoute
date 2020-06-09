package indi.yume.tools.yroute.fragmentmanager

import androidx.fragment.app.Fragment
import indi.yume.tools.yroute.*
import indi.yume.tools.yroute.datatype.mapResult
import indi.yume.tools.yroute.datatype.startLazy

abstract class BaseTableActivity<F> : BaseFragmentManagerActivity<F, StackType.Table<F>>()
        where F : Fragment, F : StackFragment


suspend fun <A, F> A.getCurrentStackTag(): String?
        where F : Fragment, F : StackFragment, A : BaseFragmentManagerActivity<F, StackType.Table<F>> =
        StackRoute.routeGetStackFromActivity(this).startLazy(core).flattenForYRoute()
                .let { it.stack.current?.first }

suspend fun <A, F> A.getCurrentStackSize(): Int
        where F : Fragment, F : StackFragment, A : BaseFragmentManagerActivity<F, StackType.Table<F>> =
        StackRoute.routeGetStackFromActivity(this).startLazy(core).flattenForYRoute()
                .let { it.stack.table[it.stack.current?.first]?.size ?: 0 }

suspend fun <A, F> A.getCurrentFragment(): F?
        where F : Fragment, F : StackFragment, A : BaseFragmentManagerActivity<F, StackType.Table<F>> =
        StackRoute.routeGetStackFromActivity(this).startLazy(core).flattenForYRoute()
                .let { it.stack.current?.second?.t }

suspend fun <A, F> A.switchToStackByTag(tag: String): Boolean
        where F : Fragment, F : StackFragment, A : BaseFragmentManagerActivity<F, StackType.Table<F>> = StackRoute.run {
    routeSwitchTag<F>(tag).mapResult { it != null } runAtA this@switchToStackByTag
}.startLazy(core).flattenForYRoute()

suspend fun <A, F> A.clearCurrentStack(resetStack: Boolean = false): Boolean
        where F : Fragment, F : StackFragment, A : BaseFragmentManagerActivity<F, StackType.Table<F>> = StackRoute.run {
    routeClearCurrentStackForTable<F>(resetStack) runAtA this@clearCurrentStack
}.startLazy(core).flattenForYRoute()

suspend fun <A, F> A.clearTargetStack(targetTag: TableTag, resetStack: Boolean = false): Boolean
        where F : Fragment, F : StackFragment, A : BaseFragmentManagerActivity<F, StackType.Table<F>> = StackRoute.run {
    routeClearStackForTable<F>(targetTag, resetStack) runAtA this@clearTargetStack
}.startLazy(core).flattenForYRoute()

suspend fun <A, F> A.backToTop(): Boolean
        where F : Fragment, F : StackFragment, A : BaseFragmentManagerActivity<F, StackType.Table<F>> = StackRoute.run {
    routeBackToTopForTable<F>() runAtA this@backToTop
}.startLazy(core).flattenForYRoute()

suspend fun <A, F> A.getTopOfStack(): F?
        where F : Fragment, F : StackFragment, A : BaseFragmentManagerActivity<F, StackType.Table<F>> = StackRoute.run {
    getTopOfStackForTable<F>() runAtA this@getTopOfStack
}.startLazy(core).flattenForYRoute()

suspend fun <A, F> A.isTopOfStack(fragment: F): Boolean
        where F : Fragment, F : StackFragment, A : BaseFragmentManagerActivity<F, StackType.Table<F>> =
        getTopOfStack() == fragment
package indi.yume.tools.yroute.fragmentmanager

import androidx.fragment.app.Fragment
import arrow.effects.IO
import indi.yume.tools.yroute.*
import indi.yume.tools.yroute.datatype.start

abstract class BaseSingleActivity<F> : BaseFragmentManagerActivity<F, StackType.Single<F>>()
        where F : Fragment, F : StackFragment {

    fun getCurrentStackSize(): IO<Int> =
            StackRoute.routeGetStackFromActivity(this).start(core).flattenForYRoute()
                    .map { it.stack.list.size }

    fun getCurrentFragment(): IO<F?> =
            StackRoute.routeGetStackFromActivity(this).start(core).flattenForYRoute()
                    .map { it.stack.list.lastOrNull()?.t }

    fun clearCurrentStack(): IO<Boolean> =StackRoute.run {
        routeClearCurrentStackForSingle<F>() runAtA this@BaseSingleActivity
    }.start(core).flattenForYRoute()

    fun backToTop(): IO<Boolean> = StackRoute.run {
        routeBackToTopForSingle<F>() runAtA this@BaseSingleActivity
    }.start(core).flattenForYRoute()

    fun getTopOfStack(): IO<F?> = StackRoute.run {
        getTopOfStackForSingle<F>() runAtA this@BaseSingleActivity
    }.start(core).flattenForYRoute()

    fun isTopOfStack(fragment: F): IO<Boolean> = getTopOfStack().map { it == fragment }
}
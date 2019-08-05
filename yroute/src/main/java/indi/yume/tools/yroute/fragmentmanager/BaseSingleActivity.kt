package indi.yume.tools.yroute.fragmentmanager

import androidx.fragment.app.Fragment
import arrow.effects.IO
import indi.yume.tools.yroute.StackFragment
import indi.yume.tools.yroute.StackRoute
import indi.yume.tools.yroute.StackType
import indi.yume.tools.yroute.datatype.start
import indi.yume.tools.yroute.flattenForYRoute

abstract class BaseSingleActivity<F> : BaseFragmentManagerActivity<F, StackType.Single<F>>()
        where F : Fragment, F : StackFragment {

    fun getCurrentStackSize(): IO<Int> =
            StackRoute.routeGetStackFromActivity(this).start(core).flattenForYRoute()
                    .map { it.stack.list.size }

    fun getCurrentFragment(): IO<F?> =
            StackRoute.routeGetStackFromActivity(this).start(core).flattenForYRoute()
                    .map { it.stack.list.lastOrNull()?.t }
}
package indi.yume.tools.yroute.fragmentmanager

import androidx.fragment.app.Fragment
import arrow.effects.IO
import indi.yume.tools.yroute.StackFragment
import indi.yume.tools.yroute.StackRoute
import indi.yume.tools.yroute.StackType
import indi.yume.tools.yroute.datatype.start
import indi.yume.tools.yroute.flattenForYRoute

abstract class BaseTableActivity<F> : BaseFragmentManagerActivity<F, StackType.Table<F>>()
        where F : Fragment, F : StackFragment {

    fun getCurrentStackSize(): IO<Int> =
            StackRoute.routeGetStackFromActivity(this).start(core).flattenForYRoute()
                    .map { it.stack.table[it.stack.current?.first]?.size ?: 0 }

    fun getCurrentFragment(): IO<F?> =
            StackRoute.routeGetStackFromActivity(this).start(core).flattenForYRoute()
                    .map { it.stack.current?.second?.t }
}
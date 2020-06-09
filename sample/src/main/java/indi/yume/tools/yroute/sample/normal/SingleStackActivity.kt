package indi.yume.tools.yroute.sample.normal

import android.os.Bundle
import indi.yume.tools.yroute.*
import indi.yume.tools.yroute.datatype.CoreEngine
import indi.yume.tools.yroute.datatype.start
import indi.yume.tools.yroute.sample.App
import indi.yume.tools.yroute.sample.R
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class SingleStackActivity : BaseFragmentActivity<StackType.Single<BaseFragment>>(), StackHost<BaseFragment, StackType.Single<BaseFragment>> {
    val core: CoreEngine<ActivitiesState> by lazy { (application as App).core }

    val scope = MainScope()

    override val fragmentId: Int = R.id.fragment_layout

    override val initStack: StackType.Single<BaseFragment> = StackType.Single()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.single_stack_activity)
    }

    override fun onBackPressed() { scope.launch {
        StackRoute.routeOnBackPress(this@SingleStackActivity).start(core).let { result ->
            println("onBackPressed | result=$result")
        }
    } }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
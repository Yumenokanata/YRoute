package indi.yume.tools.yroute.sample.fragmentmanager

import android.os.Bundle
import indi.yume.tools.yroute.*
import indi.yume.tools.yroute.datatype.CoreEngine
import indi.yume.tools.yroute.fragmentmanager.BaseSingleActivity
import indi.yume.tools.yroute.sample.App
import indi.yume.tools.yroute.sample.R

class SingleStackActivity : BaseSingleActivity<BaseFragment>() {
    override val core: CoreEngine<ActivitiesState> by lazy { (application as App).core }

    override val fragmentId: Int = R.id.fragment_layout

    override val initStack: StackType.Single<BaseFragment> = StackType.Single()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.single_stack_activity)
    }
}
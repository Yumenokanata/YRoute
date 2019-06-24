package indi.yume.tools.yroute.sample

import android.os.Bundle
import androidx.fragment.app.Fragment
import indi.yume.tools.yroute.*
import indi.yume.tools.yroute.datatype.CoreEngine

class SingleStackActivity : BaseFragmentActivity<StackType.Single<BaseFragment>>(), StackHost<BaseFragment, StackType.Single<BaseFragment>> {
    val core: CoreEngine<ActivitiesState> by lazy { (application as App).core }

    override val fragmentId: Int = R.id.fragment_layout

    override val initStack: StackType.Single<BaseFragment> = StackType.Single()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.single_stack_activity)
    }

    override fun onBackPressed() {
        super.onBackPressed()
    }
}
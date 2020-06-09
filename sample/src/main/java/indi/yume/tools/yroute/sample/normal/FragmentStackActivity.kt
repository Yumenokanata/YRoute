package indi.yume.tools.yroute.sample.normal

import android.os.Bundle
import android.widget.Button
import arrow.core.Either
import indi.yume.tools.yroute.*
import indi.yume.tools.yroute.datatype.CoreEngine
import indi.yume.tools.yroute.datatype.Fail
import indi.yume.tools.yroute.datatype.lazy.lazyR2
import indi.yume.tools.yroute.datatype.lazy.start
import indi.yume.tools.yroute.datatype.lazy.withParams
import indi.yume.tools.yroute.datatype.start
import indi.yume.tools.yroute.datatype.startLazy
import indi.yume.tools.yroute.sample.App
import indi.yume.tools.yroute.sample.R
import kotlinx.coroutines.launch

class FragmentStackActivity : BaseFragmentActivity<StackType.Table<BaseFragment>>() {
    val core: CoreEngine<ActivitiesState> by lazy { (application as App).core }

    override val fragmentId: Int = R.id.fragment_layout

    override val initStack: StackType.Table<BaseFragment> =
        StackType.Table.create(
            defaultMap = mapOf(
                "page1" to FragmentPage1::class.java,
                "page2" to FragmentPage2::class.java,
                "page3" to FragmentPage3::class.java
            )
        )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stack)

        findViewById<Button>(R.id.page_1_btn).setOnClickListener { this@FragmentStackActivity.launch {
            attempt {
                core.run(StackRoute.switchFragmentAtStackActivity<BaseFragment>(this@FragmentStackActivity, "page1"))
            }
                .let {
                    Logger.d("FragmentStackActivity", it.toString())
                    if (it is Either.Right && it.b is Fail) (it.b as Fail).error?.printStackTrace()
                }
        } }

        findViewById<Button>(R.id.page_2_btn).setOnClickListener { this@FragmentStackActivity.launch {
            StackRoute.switchFragmentAtStackActivity<BaseFragment>(this@FragmentStackActivity, "page2")
                .startLazy(core).flattenForYRoute()
                .let { Logger.d("FragmentStackActivity", it.toString()) }
        } }

        findViewById<Button>(R.id.page_3_btn).setOnClickListener { this@FragmentStackActivity.launch {
            lazyR2<ActivitiesState, StackHost<BaseFragment, StackType.Table<BaseFragment>>, TableTag, BaseFragment?>(
                StackRoute::switchFragmentAtStackActivity)
                .withParams(this@FragmentStackActivity, "page3")
                .start(core)
                .let { Logger.d("FragmentStackActivity", it.toString()) }
        } }
    }

    override fun onBackPressed() { this@FragmentStackActivity.launch {
        StackRoute.routeOnBackPress(this@FragmentStackActivity).start(core).let { result ->
            println("onBackPressed | result=$result")
        }
    } }
}
package indi.yume.tools.yroute.sample

import android.os.Bundle
import android.widget.Button
import androidx.fragment.app.Fragment
import arrow.core.Either
import arrow.core.toT
import arrow.effects.IO
import indi.yume.tools.yroute.*
import indi.yume.tools.yroute.datatype.CoreEngine
import indi.yume.tools.yroute.datatype.Fail
import indi.yume.tools.yroute.datatype.lazy.lazyR
import indi.yume.tools.yroute.datatype.lazy.start
import indi.yume.tools.yroute.datatype.lazy.withParams
import indi.yume.tools.yroute.datatype.start

class FragmentStackActivity : BaseFragmentActivity<StackType.Table<Fragment>>() {
    val core: CoreEngine<ActivitiesState> by lazy { (application as App).core }

    override val fragmentId: Int = R.id.fragment_layout

    override val initStack: StackType.Table<Fragment> =
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

        findViewById<Button>(R.id.page_1_btn).setOnClickListener {
            core.run(StackActivityRoute.switchFragmentAtStackActivity<Fragment>(this, "page1"))
                .runAsync {
                    Logger.d("FragmentStackActivity", it.toString())
                    if (it is Either.Right && it.b is Fail) (it.b as Fail).error?.printStackTrace()
                    IO.unit
                }
                .unsafeRunSync()
        }

        findViewById<Button>(R.id.page_2_btn).setOnClickListener {
            StackActivityRoute.switchFragmentAtStackActivity<Fragment>(this, "page2")
                .start(core)
                .unsafeRunAsync { Logger.d("FragmentStackActivity", it.toString()) }
        }

        findViewById<Button>(R.id.page_3_btn).setOnClickListener {
            lazyR<ActivitiesState, StackHost<Fragment, StackType.Table<Fragment>>, TableTag, Fragment?>(
                StackActivityRoute::switchFragmentAtStackActivity)
                .withParams(this, "page3")
                .start(core)
                .unsafeRunAsync { Logger.d("FragmentStackActivity", it.toString()) }
        }
    }

    override fun onBackPressed() {

        super.onBackPressed()
    }
}
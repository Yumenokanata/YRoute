package indi.yume.tools.yroute

import android.os.Bundle
import android.os.PersistableBundle
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import arrow.core.Either
import arrow.core.toT
import arrow.effects.IO
import indi.yume.tools.yroute.test4.*

class FragmentStackActivity : BaseFragmentActivity() {
    val core: CoreEngine<ActivitiesState> by lazy { (application as App).core }

    override val fragmentId: Int = R.id.fragment_layout

    override val initStack: StackType<Fragment> = StackType.Table.create(defaultMap = mapOf(
        "page1" to FragmentPage1::class.java,
        "page2" to FragmentPage2::class.java,
        "page3" to FragmentPage3::class.java
    ))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stack)

        findViewById<Button>(R.id.page_1_btn).setOnClickListener {
            core.run(StackActivityRoute.switchFragmentAtStackActivity<Fragment>(), this toT "page1")
                .runAsync {
                    Logger.d("FragmentStackActivity", it.toString())
                    if (it is Either.Right && it.b is Fail) (it.b as Fail).error?.printStackTrace()
                    IO.unit
                }
                .unsafeRunSync()
        }

        findViewById<Button>(R.id.page_2_btn).setOnClickListener {
            StackActivityRoute.switchFragmentAtStackActivity<Fragment>()
                .withParams(this, "page2")
                .start(core)
                .unsafeRunAsync { Logger.d("FragmentStackActivity", it.toString()) }
        }

        findViewById<Button>(R.id.page_3_btn).setOnClickListener {
            StackActivityRoute.switchFragmentAtStackActivity<Fragment>()
                .withParams(this, "page3")
                .start(core)
                .unsafeRunAsync { Logger.d("FragmentStackActivity", it.toString()) }
        }
    }

    override fun onBackPressed() {

        super.onBackPressed()
    }
}
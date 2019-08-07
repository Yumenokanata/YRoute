package indi.yume.tools.yroute.sample.fragmentmanager

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import indi.yume.tools.yroute.*
import indi.yume.tools.yroute.datatype.CoreEngine
import indi.yume.tools.yroute.datatype.flatMapR
import indi.yume.tools.yroute.datatype.start
import indi.yume.tools.yroute.fragmentmanager.BaseLifeActivity
import indi.yume.tools.yroute.sample.R
import indi.yume.tools.yroute.sample.App
import indi.yume.tools.yroute.sample.YRouteNavi


class SupportMainActivity : BaseLifeActivity() {
    val core: CoreEngine<ActivitiesState> by lazy { (application as App).core }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<TextView>(R.id.text_view).text = "hash: ${this.hashCode()}"
        findViewById<Button>(R.id.new_activity_button).setOnClickListener {
            ActivitiesRoute.run {
                createActivityIntent<BaseLifeActivity, ActivitiesState>(ActivityBuilder(OtherActivity::class.java))
                    .flatMapR { startActivityForResult(it, 1) }
            }
                .start(core)
                .unsafeRunAsync { result ->
                    Logger.d("MainActivity", result.toString())
                }
        }

        findViewById<Button>(R.id.fragment_stack_button).setOnClickListener {
//            StackRoute
//                .startStackFragActivity(ActivityBuilder(FragmentStackActivity::class.java))
//                .start(core)
//                .unsafeRunAsync { result ->
//                    Logger.d("FragmentStackActivity", result.toString())
//                }
            startActivity(Intent(this, FragmentStackActivity::class.java))
        }

        findViewById<Button>(R.id.fragment_single_stack_button).setOnClickListener {
//            StackRoute.run {
//                routeStartFragAtNewSingleActivity(
//                        ActivityBuilder(SingleStackActivity::class.java)
//                                .withAnimData(AnimData()),
//                        FragmentBuilder(FragmentOther::class.java)
//                                .withParam(OtherParam("Msg from MainActivity."))
//                )
//            }.start(core).unsafeRunAsync { result ->
//                Logger.d("SingleStackActivity", result.toString())
//            }
            YRouteNavi.run("route://test/other/fragment?param=this+is+Msg+from+YRouteNavi")
                    .unsafeRunAsync { result ->
                        Logger.d("SingleStackActivity", result.toString())
                    }
        }
    }
}


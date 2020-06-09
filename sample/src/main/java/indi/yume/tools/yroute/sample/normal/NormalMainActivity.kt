package indi.yume.tools.yroute.sample.normal

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import indi.yume.tools.yroute.*
import indi.yume.tools.yroute.datatype.CoreEngine
import indi.yume.tools.yroute.datatype.flatMapR
import indi.yume.tools.yroute.datatype.start
import indi.yume.tools.yroute.datatype.startLazy
import indi.yume.tools.yroute.fragmentmanager.BaseLifeActivity
import indi.yume.tools.yroute.sample.R
import indi.yume.tools.yroute.sample.App
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class NormalMainActivity : BaseLifeActivity() {
    val core: CoreEngine<ActivitiesState> by lazy { (application as App).core }
    val scope = MainScope()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<TextView>(R.id.text_view).text = "hash: ${this.hashCode()}"
        findViewById<Button>(R.id.new_activity_button).setOnClickListener { scope.launch {
            ActivitiesRoute.run {
                createActivityIntent<BaseLifeActivity, ActivitiesState>(ActivityBuilder(OtherActivity::class.java))
                    .flatMapR { startActivityForRx(ActivityBuilder<OtherActivity>(it)) }
            }
                .startLazy(core).flattenForYRoute().let { result ->
                        Logger.d("MainActivity", result.toString())
                        result.b.doOnSuccess { (resultCode, data) ->

                        }
                    }
//            findViewById<TextView>(R.id.text_view).setText("hash: ${this.hashCode()}")
        } }

        findViewById<Button>(R.id.fragment_stack_button).setOnClickListener { scope.launch {
            StackRoute
                .startStackFragActivity(ActivityBuilder(FragmentStackActivity::class.java))
                .start(core)
                .let { result ->
                    Logger.d("FragmentStackActivity", result.toString())
                }
//            recreate()
        } }

        findViewById<Button>(R.id.fragment_single_stack_button).setOnClickListener { scope.launch {
            StackRoute.run {
//                routeStartFragAtNewSingleActivity()
                val requestCode = 2
                ActivitiesRoute.routeStartActivityForResult(
                        ActivityBuilder(SingleStackActivity::class.java)
                        .withAnimData(AnimData()), requestCode)
                        .flatMapR {
                            routeStartFragmentForRx<BaseFragment>(FragmentBuilder(FragmentOther::class.java)
                                    .withParam(OtherParam("Msg from MainActivity."))) runAtA it
                        }
            }.startLazy(core).flattenForYRoute().let { result ->
                Logger.d("SingleStackActivity", result.toString())
                result
                        .doOnSuccess { (resultCode: Int, data: Bundle?) ->
                            Toast.makeText(this@NormalMainActivity,
                                    "Normal2 on result: $resultCode, data=$data", Toast.LENGTH_SHORT).show()
                        }
                        .subscribe()
            }
//            YRouteNavi.run("route://test/other/fragment?param=this+is+Msg+from+YRouteNavi")
//                    .unsafeRunAsync { result ->
//                        Logger.d("SingleStackActivity", result.toString())
//                    }
        } }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Toast.makeText(this, "Normal on result: $resultCode, data=$data", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}


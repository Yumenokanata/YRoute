package indi.yume.tools.yroute.sample

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.lifecycle.lifecycleScope
import indi.yume.tools.yroute.*
import indi.yume.tools.yroute.datatype.CoreEngine
import indi.yume.tools.yroute.datatype.startLazy
import indi.yume.tools.yroute.fragmentmanager.BaseLifeActivity
import indi.yume.tools.yroute.sample.fragmentmanager.SupportMainActivity
import indi.yume.tools.yroute.sample.normal.*
import kotlinx.coroutines.launch


class MainActivity : BaseLifeActivity() {
    val core: CoreEngine<ActivitiesState> by lazy { (application as App).core }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sum_main)

        findViewById<Button>(R.id.normal_btn).setOnClickListener { lifecycleScope.launch {
            ActivitiesRoute
                    .routeStartActivity(ActivityBuilder(Intent(this@MainActivity, NormalMainActivity::class.java)))
                    .startLazy(core).flattenForYRoute()
//            startActivity(Intent(this, NormalMainActivity::class.java))
        } }

        findViewById<Button>(R.id.support_btn).setOnClickListener { lifecycleScope.launch {
            ActivitiesRoute
                    .routeStartActivity(ActivityBuilder(SupportMainActivity::class.java))
                    .startLazy(core).flattenForYRoute()
//            startActivity(Intent(this, NormalMainActivity::class.java))
        } }
    }
}


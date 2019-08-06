package indi.yume.tools.yroute.sample

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import indi.yume.tools.yroute.*
import indi.yume.tools.yroute.datatype.CoreEngine
import indi.yume.tools.yroute.fragmentmanager.BaseLifeActivity
import indi.yume.tools.yroute.sample.fragmentmanager.SupportMainActivity
import indi.yume.tools.yroute.sample.normal.*


class MainActivity : BaseLifeActivity() {
    val core: CoreEngine<ActivitiesState> by lazy { (application as App).core }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sum_main)

        findViewById<Button>(R.id.normal_btn).setOnClickListener {
            startActivity(Intent(this, NormalMainActivity::class.java))
        }

        findViewById<Button>(R.id.support_btn).setOnClickListener {
            startActivity(Intent(this, SupportMainActivity::class.java))
        }
    }
}


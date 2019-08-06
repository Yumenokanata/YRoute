package indi.yume.tools.yroute.sample.fragmentmanager

import android.content.Intent
import android.os.Bundle
import android.view.View
import indi.yume.tools.yroute.fragmentmanager.BaseLifeActivity
import indi.yume.tools.yroute.sample.R

class OtherActivity : BaseLifeActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_other)

        findViewById<View>(R.id.new_activity_button).setOnClickListener {
            startActivity(Intent(this, OtherActivity::class.java))
        }
        findViewById<View>(R.id.finish_button).setOnClickListener {
            finish()
        }
    }
}
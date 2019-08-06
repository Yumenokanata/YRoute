package indi.yume.tools.yroute.fragmentmanager

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import indi.yume.tools.yroute.ActivityLifeEvent
import indi.yume.tools.yroute.ActivityLifecycleOwner
import io.reactivex.subjects.Subject

abstract class BaseLifeActivity : Activity(), ActivityLifecycleOwner {
    override val lifeSubject: Subject<ActivityLifeEvent> = ActivityLifecycleOwner.defaultLifeSubject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        makeState(ActivityLifeEvent.OnCreate(this, savedInstanceState))
    }

    override fun onStart() {
        super.onStart()
        makeState(ActivityLifeEvent.OnStart(this))
    }

    override fun onResume() {
        super.onResume()
        makeState(ActivityLifeEvent.OnResume(this))
    }

    override fun onPause() {
        super.onPause()
        makeState(ActivityLifeEvent.OnPause(this))
    }

    override fun onStop() {
        super.onStop()
        makeState(ActivityLifeEvent.OnStop(this))
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        makeState(ActivityLifeEvent.OnNewIntent(this, intent))
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)
        makeState(ActivityLifeEvent.OnSaveInstanceState(this, outState))
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        makeState(ActivityLifeEvent.OnConfigurationChanged(this, newConfig))
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        makeState(ActivityLifeEvent.OnActivityResult(this, requestCode, resultCode, data))
    }

    override fun onDestroy() {
        super.onDestroy()
        makeState(ActivityLifeEvent.OnDestroy(this))
        destroyLifecycle()
    }
}
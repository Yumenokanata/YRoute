package indi.yume.tools.yroute

import android.app.Application
import indi.yume.tools.yroute.test4.ActivitiesState
import indi.yume.tools.yroute.test4.CoreEngine
import indi.yume.tools.yroute.test4.bindApp

class App : Application() {
    lateinit var core: CoreEngine<ActivitiesState>

    override fun onCreate() {
        super.onCreate()

        CoreEngine.apply {
            core = create(this@App, ActivitiesState(emptyList())).unsafeRunSync()
            core.start().subscribe()
            core.bindApp().subscribe()
        }
    }
}
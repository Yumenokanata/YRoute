package indi.yume.tools.yroute

import android.app.Application
import indi.yume.tools.yroute.test4.ActivitiesState
import indi.yume.tools.yroute.test4.MainCoreEngine
import indi.yume.tools.yroute.test4.bindApp

class App : Application() {
    lateinit var core: MainCoreEngine<ActivitiesState>

    override fun onCreate() {
        super.onCreate()

        MainCoreEngine.apply {
            core = create(this@App, ActivitiesState(emptyList())).unsafeRunSync()
            core.start().subscribe()
            core.bindApp().subscribe()
        }
    }
}
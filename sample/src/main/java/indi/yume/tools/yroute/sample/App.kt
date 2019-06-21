package indi.yume.tools.yroute.sample

import android.app.Application
import indi.yume.tools.yroute.ActivitiesState
import indi.yume.tools.yroute.datatype.MainCoreEngine
import indi.yume.tools.yroute.bindApp

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
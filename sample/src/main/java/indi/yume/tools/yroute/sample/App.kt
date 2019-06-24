package indi.yume.tools.yroute.sample

import android.app.Application
import arrow.effects.IO
import indi.yume.tools.yroute.*
import indi.yume.tools.yroute.datatype.MainCoreEngine
import indi.yume.tools.yroute.datatype.YResult
import indi.yume.tools.yroute.datatype.flatMapR
import indi.yume.tools.yroute.datatype.lazy.start
import indi.yume.tools.yroute.datatype.lazy.withParam

class App : Application() {
    lateinit var core: MainCoreEngine<ActivitiesState>

    override fun onCreate() {
        super.onCreate()

        MainCoreEngine.apply {
            core = create(this@App, ActivitiesState(emptyList())).unsafeRunSync()
            core.start().subscribe()
            core.bindApp().subscribe()
        }

        YRouteNavi.initNavi(core, UriRoute.build("test") {
            putRoute("/other/activity",
                    ActivitiesRoute.run {
                        createActivityIntent<BaseActivity, ActivitiesState>(ActivityBuilder(OtherActivity::class.java))
                                .flatMapR { startActivityForResult(it, 1) }
                    })

            put("/other/fragment") { uri ->
                val param = OtherParam(uri.query.getAllQuery()["param"] ?: "No param start.")
                StackRoute.run {
                    routeStartFragAtNewSingleActivity(
                            ActivityBuilder(SingleStackActivity::class.java),
                            FragmentBuilder(FragmentOther::class.java).withParam(param)
                    )
                }
            }
        })
    }
}

object YRouteNavi {
    lateinit var core: MainCoreEngine<ActivitiesState>

    lateinit var uriRoute: RouteNavi<ActivitiesState>

    fun initNavi(core: MainCoreEngine<ActivitiesState>,
                 uriRoute: RouteNavi<ActivitiesState>) {
        this.core = core
        this.uriRoute = uriRoute
    }

    fun run(uri: String): IO<YResult<Any?>> = uriRoute.withParam(uri).start(core)
}
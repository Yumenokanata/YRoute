# YRoute

[![](https://jitpack.io/v/Yumenokanata/YRoute.svg)](https://jitpack.io/#Yumenokanata/YRoute)

Functional Route Util for Android

[中文文档](https://github.com/Yumenokanata/YRoute/blob/master/README_CN.md)

This is a routing library for Android, use `Kotlin coroutines` and functional library [Arrow](https://github.com/arrow-kt/arrow) as the core, and it is built based on the combinator-oriented idea of the functional paradigm.
 
The goal is to build a flexible, simple structure, static type routing library

Features：
1. The core is simplified to only the composite type `YRoute` and the runner `CoreEngine`
2. Static type
3. Based on combinator-oriented programming, there is no complicated inheritance structure, hierarchical concept, only the combination of `YRoute`
4. State and logic are separated, state protected by the core, so logic can be combined safely and flexibly
5. Unlike `Redux` or `Flux` which separate side effects through the `Middleware` structure, YRoute uses the coroutine `suspend` to separate side effects, which is more flexible, composable, and contagious
6. The core type `YRoute` is a monad and has no side effects, so it can be combined and created arbitrarily
7. It does not restrict the use of `CoreEngine` in the global singleton mode, and you can also create a sub-Core or a completely independent Core

---

## Basic features

1. Activity lifecycle management
2. Start Activity in Rx mode
3. Activity of multi-stack Fragment
4. The same startFragment and finishFragment operation methods as Activity
5. startFragmentForResult (onFragmentResult callback method)
6. startFragmentForRx and startActivityForRx
7. Fragment and Activity start and exit animation control
8. onShow and onHide callback methods
9. StackActivity is divided into Single and Table, supporting different modes of single stack and multi stack
10. Support global Activity life cycle management
11. Support to start Route from Uri
12. You can choose to construct two routes: ordinary `YRoute` without parameters and `LazyYRoute` with parameters
13. Fragment and Activity do not need to inherit a certain basic class (but need to implement some basic interfaces)
14. The new parameter transfer interface can support parameter transfer in ways other than Intent, and can transfer any type of object (including non-serializable types)
15. You can get the result of Route running, failure or success, or even get the new Activity started after `startActivity`

---

## Add to Project
Step1: Add in root build.gradle:
```groovy
allprojects {
	repositories {
        jcenter()
		maven { url "https://jitpack.io" }
	}
}
```

Step2: Add dependencies in target module:
```groovy
dependencies {
    implementation 'com.github.Yumenokanata:YRoute:x.y.z'
}
```

---

## Example:

```kotlin
// Config at App
class App : Application() {
    lateinit var core: MainCoreEngine<ActivitiesState>

    override fun onCreate() {
        super.onCreate()

        MainCoreEngine.apply {
            core = create(this@App, ActivitiesState(emptyList())).unsafeRunSync()
            core.start().catchSubscribe()
            core.bindApp().catchSubscribe()
        }
    }
}

// Use
launch {
    StackRoute
        .startStackFragActivity(ActivityBuilder(FragmentStackActivity::class.java))
        .start(core)
}

launch {
    val result = ActivitiesRoute.run {
        createActivityIntent<BaseActivity, ActivitiesState>(ActivityBuilder(OtherActivity::class.java))
            .flatMapR { startActivityForResult(it, 1) }
    }
    .start(core)

    Logger.d("MainActivity", result.toString())        
}

launch {
    try {
        val result = StackRoute.run {
            routeStartFragmentForRx(FragmentBuilder(FragmentOther::class.java)
                    .withParam(OtherParam("This is param from FragmentPage1."))
            ) runAtF this@FragmentPage1
        }.startLazy(core).flattenForYRoute()

        withContext(Dispatchers.Main) {
            Toast.makeText(activity,
                            "YResult from Other fragment: \nresultCode=${it.a}, data=${it.b?.getString("msg")}",
                            Toast.LENGTH_LONG).show()
        }
    } catch (e: Throwable) {
        e.printStackTrace()
    }
}
```

## [FragmentManager](https://github.com/Yumenokanata/FragmentManager)Alternative use of this library

[Sample code](https://github.com/Yumenokanata/YRoute/tree/master/sample/src/main/java/indi/yume/tools/yroute/sample/fragmentmanager), the main alternative class is:

1. BaseFragmentManagerActivity -> indi.yume.tools.yroute.fragmentmanager.BaseTableActivity<F> and BaseSingleActivity<F>
2. BaseManagerFragment -> indi.yume.tools.yroute.fragmentmanager.BaseManagerFragment

## Usage

The library has two core types: `YRoute<S, R>` and `CoreEngine`. The steps to use are

1. Build `YRoute`
2. Put into `CoreEngine` to run

### 1. Build YRoute

The two paradigms of `YRoute<S, R>` are: the State data type corresponding to `S`, and the return value after running the `R` route

The `YRoute<S, R>` type can be regarded as a pure function:

```
suspend (S, Cxt) -> Pair<S, YResult<R>>
```

The meaning is: input the current state `S` and the context `Cxt`, and output a new state and the running result `YResult<R>`

There are currently some functional routes developed in the library:

#### ActivitiesRoute

The corresponding State is `ActivitiesState`, which saves the state of all Activities

The routes are:

```kotlin
object ActivitiesRoute {
    fun routeStartActivityByIntent(intent: Intent): YRoute<ActivitiesState, Activity>

    fun <A : Activity> routeStartActivity(builder: ActivityBuilder<A>): YRoute<ActivitiesState, A>

    fun routeStartActivityForResult(builder: ActivityBuilder<Activity>, requestCode: Int): YRoute<ActivitiesState, Activity>

    fun routeStartActivityForRx(builder: ActivityBuilder): YRoute<ActivitiesState, Maybe<Tuple2<Int, Bundle?>>>

    val routeFinishTop: YRoute<ActivitiesState, Unit>

    fun routeFinish(activity: Activity): YRoute<ActivitiesState, Unit>
}
```

#### StackRoute

This is a route similar to the FragmentManager library that manages the Fragment stack in the Activity, but compared to the FragmentManager library, it can choose a single stack or multi-stack switching, and it can be in the Activity without limitation, and it can also manage the Fragment nested in the ParentFragment mode; and Not limited to having to inherit the base class.

Activity or Fragment as a container needs to inherit `StackHost<F, out Type: StackType<F>>`:

```kotlin
abstract class FragmentTableActivity : FragmentActivity(), StackHost<BaseFragment, StackType.Table<BaseFragment> {
    override val fragmentId: Int = R.id.fragment_layout

    override var controller: StackController = StackController.defaultController()

    override val initStack: StackType.Table<BaseFragment> =
            StackType.Table.create(
                defaultMap = mapOf(
                    "page1" to FragmentPage1::class.java,
                    "page2" to FragmentPage2::class.java,
                    "page3" to FragmentPage3::class.java
                )
            )

    ...
}
```

As a managed sub-Fragment, you need to implement the `StackFragment` interface:

```kotlin
class FragmentPage1 : Fragment(), StackFragment {
    override var controller: FragController = FragController.defaultController()

    ...
}
```

The managed Fragment can choose to implement the `FragmentParam<T>` interface:

```kotlin
class FragmentPage1 : Fragment(), StackFragment, FragmentParam<ParamModel> {
    override val injector: Subject<OtherParam> = FragmentParam.defaultInjecter()

    override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)

            injector.subscribe {
                Toast.makeText(activity, it.toString(), Toast.LENGTH_LONG).show()
            }
        }
    ...
}
```

This way, when building a `FragmentBuilder` that implements the `FragmentParam<T>` interface, a method `withParam()` will be added:

```kotlin
FragmentBuilder(FragmentPage1::class.java)
        .withParam(OtherParam("This is param.")
```

In this way, any type of parameter can be injected.


After the above work is completed, StackRoute can be used. The routing functions are:

```kotlin
object StackRoute {
    fun <F, T, A> routeStartStackActivity(builder: ActivityBuilder<A>)
            : YRoute<ActivitiesState, A>
            where F : Fragment, T : StackType<F>, A : FragmentActivity, A : StackHost<F, T>

    fun <F, T, A> routeGetStackFromActivity(activity: A)
            : YRoute<ActivitiesState, Lens<ActivitiesState, StackFragState<F, T>?>>
            where F : Fragment, T : StackType<F>, A : FragmentActivity, A : StackHost<F, T>

    fun <F, T> routeGetStackActivityFromFrag(frag: Fragment)
            : YRoute<ActivitiesState, Lens<ActivitiesState, StackFragState<F, T>?>>
            where F : Fragment, T : StackType<F>

    fun <F, R> routeRunAtFrag(frag: Fragment, route: YRoute<StackFragState<F, StackType<F>>, R>): YRoute<ActivitiesState, R>
            where F : Fragment

    infix fun <F, R> YRoute<StackFragState<F, StackType<F>>, R>.runAtF(frag: Fragment): YRoute<ActivitiesState, R>
            where F : Fragment

    fun <F, A, T, R> routeRunAtAct(act: A, route: YRoute<StackFragState<F, T>, R>): YRoute<ActivitiesState, R>
            where F : Fragment, T : StackType<F>, A : FragmentActivity, A : StackHost<F, T>

    infix fun <F, A, T, R> YRoute<StackFragState<F, T>, R>.runAtA(act: A): YRoute<ActivitiesState, R>
            where F : Fragment, T : StackType<F>, A : FragmentActivity, A : StackHost<F, T>

    fun <F> routeStartFragmentAtSingle(builder: FragmentBuilder<F>): YRoute<StackFragState<F, StackType.Single<F>>, F>
            where F : Fragment, F : StackFragment

    fun <F> routeStartFragmentAtTable(builder: FragmentBuilder<F>): YRoute<StackFragState<F, StackType.Table<F>>, F>
            where F : Fragment, F : StackFragment

    fun <F> routeStartFragment(builder: FragmentBuilder<F>): YRoute<StackFragState<F, StackType<F>>, F>
            where F : Fragment, F : StackFragment

    fun <F> routeStartFragmentForResultAtSingle(builder: FragmentBuilder<F>, requestCode: Int): YRoute<StackFragState<F, StackType.Single<F>>, F>
            where F : Fragment, F : StackFragment

    fun <F> routeStartFragmentForResultAtTable(builder: FragmentBuilder<F>, requestCode: Int): YRoute<StackFragState<F, StackType.Table<F>>, F>
            where F : Fragment, F : StackFragment

    fun <F> routeStartFragmentForResult(builder: FragmentBuilder<F>, requestCode: Int): YRoute<StackFragState<F, StackType<F>>, F>
            where F : Fragment, F : StackFragment

    fun <F> routeStartFragmentForRxAtSingle(builder: FragmentBuilder<F>): YRoute<StackFragState<F, StackType.Single<F>>, Maybe<Tuple2<Int, Bundle?>>>
            where F : Fragment, F : StackFragment, F : FragmentLifecycleOwner

    fun <F> routeStartFragmentForRxAtTable(builder: FragmentBuilder<F>): YRoute<StackFragState<F, StackType.Table<F>>, Maybe<Tuple2<Int, Bundle?>>>
            where F : Fragment, F : StackFragment, F : FragmentLifecycleOwner

    fun <F> routeStartFragmentForRx(builder: FragmentBuilder<F>): YRoute<StackFragState<F, StackType<F>>, Maybe<Tuple2<Int, Bundle?>>>
            where F : Fragment, F : StackFragment, F : FragmentLifecycleOwner

    fun <F> routeSwitchTag(tag: TableTag): YRoute<StackFragState<F, StackType.Table<F>>, F?> where F : Fragment

    fun <F : Fragment> routeFinishFragmentAtSingle(target: StackFragment?): YRoute<StackFragState<F, StackType.Single<F>>, Tuple2<SingleTarget<F>?, FinishResult>>

    fun <F : Fragment> routeFinishFragmentAtTable(target: StackFragment?): YRoute<StackFragState<F, StackType.Table<F>>, Tuple2<TableTarget<F>?, FinishResult>>

    fun <F : Fragment> routeFinishFragment(target: StackFragment?): YRoute<StackFragState<F, StackType<F>>, Tuple2<Either<SingleTarget<F>, TableTarget<F>>, FinishResult>>

    fun <F, A> routeStartFragAtNewSingleActivity(activityBuilder: ActivityBuilder<A>,
                                                 fragBuilder: FragmentBuilder<F>)
            : YRoute<ActivitiesState, Tuple2<A, F>>
            where F : Fragment, F : StackFragment, A : FragmentActivity, A : StackHost<F, StackType.Single<F>>

    fun <F, A> routeStartFragAtNewTableActivity(activityBuilder: ActivityBuilder<A>,
                                                fragBuilder: FragmentBuilder<F>)
            : YRoute<ActivitiesState, Tuple2<A, F>>
            where F : Fragment, F : StackFragment, A : FragmentActivity, A : StackHost<F, StackType.Table<F>>
}
```

#### UriRoute

Route to jump by Uri string:

```kotlin
val routeNavi = UriRoute.build("main") {
    put("/test/other",
        routeStartStackActivity(ActivityBuilder(FragmentStackActivity::class.java)))

    put("/test/page1",
        routeStartFragAtNewSingleActivity(
                ActivityBuilder(SingleStackActivity::class.java),
                FragmentBuilder(FragmentOther::class.java).withParam(OtherParam("Msg from MainActivity."))
        ))
}

routeNavi.withParam("route://main/test/other").start(core) // return IO<Result<Any?>>
```

### 2. Run YRoute

YRoute is an `Action` that transforms according to the current context and state. It needs to be actually executed in `CoreEngine`

`CoreEngine` is an interface that describes the usual method of running YRoute:

```kotlin
interface CoreEngine<S> {
    val routeCxt: RouteCxt

    @CheckResult
    fun runIO(io: IO<*>): IO<Unit>

    @CheckResult
    fun putStream(stream: Completable): IO<Unit>

    @CheckResult
    fun <R> runAsync(route: YRoute<S, R>, callback: (YResult<R>) -> Unit): IO<Unit>

    @CheckResult
    fun <R> run(route: YRoute<S, R>): IO<YResult<R>>
}
```

You can implement this interface yourself to provide different operating modes. By default, a `MainCoreEngine` is implemented, and the run queue is managed through Rx, running serially:

```kotlin
MainCoreEngine.apply {
    core = create(this@App, ActivitiesState(emptyList())).unsafeRunSync()
    core.start().catchSubscribe()
}
```

After the Core is created, you can run YRoute:

```kotlin
routeStartStackActivity(ActivityBuilder(FragmentStackActivity::class.java)) // YRoute
    .start(core) //suspend Result<R>

lazyR1(routeStartStackActivity(ActivityBuilder(FragmentStackActivity::class.java)))
    .withParams("param")// LazyYRoute
    .start(core) //suspend Result<R>
```

If you don't want to execute the route immediately, you can use the `startLazy` method:

```kotlin
routeStartStackActivity(ActivityBuilder(FragmentStackActivity::class.java)) // YRoute
    .startLazy(core) //SuspendP<Result<R>>

lazyR1(routeStartStackActivity(ActivityBuilder(FragmentStackActivity::class.java)))
    .withParams("param")// LazyYRoute
    .startLazy(core) //SuspendP<Result<R>>
```

`SuspendP` can be converted to Rx stream and then started with Rx:

```kotlin
suspendP.asSingle()
```

After the actual executed, you can get a value of type `YResult`, which has two optional types, `Success` and `Fail`, representing whether the result is success or failure

```kotlin
sealed class YResult<out T> : YResultOf<T>

data class Success<T>(val t: T) : YResult<T>()
data class Fail(val message: String, val error: Throwable? = null) : YResult<Nothing>()
```

---

Routes can be freely combined to achieve more complex custom functions:

```kotlin
// Use Monad to combine:
StackRoute.run { YRoute.monadError<StackFragState<BaseFragment, StackType.Table<BaseFragment>>>().fx.monad {
    val currentStackTab = !routeFromState<StackFragState<BaseFragment, StackType.Table<BaseFragment>>, String?> { it.stack.current?.first }
    
    if (currentStackTab == tab) {
        // If the Tab to be switched is the current Tab
        val currentStackSize = !routeFromState<StackFragState<BaseFragment, StackType.Table<BaseFragment>>, Int> {
            it.stack.table[it.stack.current?.first]?.size ?: 0
        }
        if (currentStackSize > 1)
            // If the current Tab is not at the top level, return to the top level Fragment
            !routeBackToTopForTable<BaseFragment>()
        else
            // If you are currently at the top Fragment, restart the top Fragment
            !routeClearCurrentStackForTable<BaseFragment>(true)
    } else {
        // Switch Tab directly
        !routeSwitchTag<BaseFragment>(tag)
    }
    Unit
}.fix() runAtA this@MainBaseActivity }

// Use Route constructor to combine:
routeF<StackFragState<BaseFragment, StackType.Table<BaseFragment>>, Unit> { state, routeCxt ->
    val currentStackTab = state.stack.current?.first
    val (newState, _) = if (currentStackTab == tab) {
        // If the Tab to be switched is the current Tab
        val currentStackSize = state.stack.table[state.stack.current?.first]?.size ?: 0
        if (currentStackSize > 1)
            // If the current Tab is not at the top level, return to the top level Fragment
            StackRoute.routeBackToTopForTable<BaseFragment>()
                    .runRoute(state, routeCxt)
        else
            // If you are currently at the top Fragment, restart the top Fragment
            StackRoute.routeClearCurrentStackForTable<BaseFragment>(true)
                    .runRoute(state, routeCxt)
    } else {
        // Switch Tab directly
        StackRoute.routeSwitchTag<BaseFragment>(tag)
                .runRoute(state, routeCxt)
    }
    newState toT YResult.success(Unit)
}
```

---

## Next

Provide routing support for Jetpack Compose

---

## Related articles

[YRoute开发随笔](https://segmentfault.com/a/1190000019575543)

## Author

Yumenokanata:
[Segmentfault](https://segmentfault.com/u/yumenokanata)

### License

<pre>
Copyright 2019 Yumenokanata

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
</pre>






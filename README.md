# YRoute

[![](https://jitpack.io/v/Yumenokanata/YRoute.svg)](https://jitpack.io/#Yumenokanata/YRoute)

Functional Route Util for Android

这是一个用于Android的路由库，使用了函数式库[Arrow](https://github.com/arrow-kt/arrow)的部分数据结构作为核心，本身基于函数范式的面向组合子思想构建，目标是构建一个灵活、结构简单、静态类型的路由库

本库目前还处于开发阶段，目前作为架构测试实现了一些基础功能

特性：
1. 核心简化到只有组合类型`YRoute`和运行器`CoreEngine`
2. 多种范型技法实现的静态类型
3. 基于面向组合子编程构建，没有复杂的继承结构、分层概念，只有`YRoute`的组合
4. 状态和逻辑分离，全局只有被核心保护的一个状态，因此逻辑可以被安全、灵活地组合
5. 不同于Redux和Flux的通过Middleware结构分离副作用，YRoute使用`IO`类型分离副作用，更加灵活和具有组合性
6. 核心类型`YRoute`是一个单子，也是无副作用的，因此可以被任意组合和创建
7. 并不限制全局单例方式使用`CoreEngine`，也可以创建子Core或者完全独立的Core

---

## 开发进程

### 第一阶段：完成[FragmentManager](https://github.com/Yumenokanata/FragmentManager)功能覆盖

1. ~~Activity生命周期管理~~
2. ~~Rx方式启动Activity~~
3. ~~多栈Fragment的Activity~~
4. ~~与Activity相同的startFragment和finishFragment操作方式~~
5. ~~startFragmentForResult（onFragmentResult回调方法）~~
6. ~~startFragmentForRx和startActivityForRx~~
7. ~~Fragment和Activity的启动和退出动画控制~~
8. ~~onShow和onHide回调方法~~

**Tips(2019/08): 现已完成对[FragmentManager](https://github.com/Yumenokanata/FragmentManager)库主要功能的90%覆盖**

当然相比FragmentManager功能还有一些已经完成的额外功能：
1. StackActivity分为了Single和Table两种，支持单栈和多栈的不同模式
2. 支持全局的Activity生命周期管理
3. 支持从Uri启动Route
4. 可选择构建无参的普通`YRoute`和有参数的`LazyYRoute`两种路由
5. Fragment和Activity不需要强继承某个基础类了（但需要实现一些基础接口）
6. 新的传参接口，可以支持Intent之外的方式传参，可以传递任意类型的对象（包括不可序列化的类型）
7. 可以获取Route运行的结果，失败或者成功，甚至获取startActivity之后启动的新Activity

---

## 添加到Android studio
Step1: 在根build.gradle中添加仓库：
```groovy
allprojects {
	repositories {
        jcenter()
		maven { url "https://jitpack.io" }
	}
}
```

Step2: 在工程中添加依赖：
```groovy
dependencies {
    implementation 'com.github.Yumenokanata:YRoute:-SNAPSHOT'
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

// 使用
StackRoute
    .startStackFragActivity(ActivityBuilder(FragmentStackActivity::class.java))
    .start(core).toSingle().catchSubscribe()

ActivitiesRoute.run {
    createActivityIntent<BaseActivity, ActivitiesState>(ActivityBuilder(OtherActivity::class.java))
        .flatMapR { startActivityForResult(it, 1) }
}
    .start(core)
    .unsafeRunAsync { result ->
        Logger.d("MainActivity", result.toString())
    }

StackRoute.run {
    routeStartFragmentForRx(FragmentBuilder(FragmentOther::class.java)
            .withParam(OtherParam("This is param from FragmentPage1."))
    ) runAtF this@FragmentPage1
}.start(core).flattenForYRoute().unsafeAsyncRunDefault({
    val s = it.doOnSuccess {
        Toast.makeText(activity,
                "YResult from Other fragment: \nresultCode=${it.a}, data=${it.b?.getString("msg")}",
                Toast.LENGTH_LONG).show()
    }.catchSubscribe()
})
```

## [FragmentManager](https://github.com/Yumenokanata/FragmentManager)库的替代使用

可参照[sample包](https://github.com/Yumenokanata/YRoute/tree/master/sample/src/main/java/indi/yume/tools/yroute/sample/fragmentmanager)下的示例使用, 主要替代类为:

1. BaseFragmentManagerActivity -> indi.yume.tools.yroute.fragmentmanager.BaseTableActivity<F> 和 BaseSingleActivity<F>
2. BaseManagerFragment -> indi.yume.tools.yroute.fragmentmanager.BaseManagerFragment

## 详细使用方法

库有两个核心类型：`YRoute<S, R>`和`CoreEngine`，使用步骤就是

1. 构造YRoute
2. 放入CoreEngine运行

### 1. 构造YRoute

`YRoute<S, R>`的两个范型为：`S`对应的State数据类型、`R`路由运行后的返回值

`YRoute<S, R>`类型可以看作一个纯函数：

```
(S, Cxt) -> IO<Pair<S, YResult<R>>>
```

意义为：输入当前状态`S`和上下文`Cxt`, 输出一个被IO类型包裹的新的状态和运行结果`YResult<R>`

因为这只是个函数， 因此可以任意构造新的Route添加新的功能，不同的Route之间可以相互组合

库中目前已经开发有一些功能性的路由：

#### ActivitiesRoute

对应的State为`ActivitiesState`, 其中保存着所有Activity的状态

路由有：

```kotlin
object ActivitiesRoute {
    fun routeStartActivityByIntent(intent: Intent): YRoute<ActivitiesState, Activity>

    fun <A : Activity> routeStartActivity(builder: ActivityBuilder<A>): YRoute<ActivitiesState, A>

    fun routeStartActivityForResult(builder: ActivityBuilder<Activity>, requestCode: Int): YRoute<ActivitiesState, Activity>

    fun routeStartActivityForRx(builder: RxActivityBuilder): YRoute<ActivitiesState, Single<Tuple2<Int, Bundle?>>>

    val routeFinishTop: YRoute<ActivitiesState, Unit>

    fun routeFinish(activity: Activity): YRoute<ActivitiesState, Unit>
}
```

#### StackRoute

这是一个类似FragmentManager库的管理Activity中Fragment栈的路由，但相比FragmentManager库可以自选单栈还是可多栈切换，并且可以不限制必须在Activity中，也可以管理ParentFragment方式嵌套的Fragment；而且不限制于必须继承基础类

作为容器的Activity或者Fragment需要继承`StackHost<F, out Type : StackType<F>>`：

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

而作为被管理的子Fragment需要实现`StackFragment`接口：

```kotlin
class FragmentPage1 : Fragment(), StackFragment {
    override var controller: FragController = FragController.defaultController()

    ...
}
```

被管理的Fragment可以选择实现`FragmentParam<T>`接口：
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

这样构建实现了`FragmentParam<T>`接口的`FragmentBuilder`时会增加一个方法`withParam()`：

```kotlin
FragmentBuilder(FragmentPage1::class.java)
        .withParam(OtherParam("This is param.")
```

这样可以实现注入任意类型参数。


以上工作完成后就可以使用StackRoute了，路由功能有：

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

    fun <F> routeStartFragmentForRxAtSingle(builder: FragmentBuilder<F>): YRoute<StackFragState<F, StackType.Single<F>>, Single<Tuple2<Int, Bundle?>>>
            where F : Fragment, F : StackFragment, F : FragmentLifecycleOwner

    fun <F> routeStartFragmentForRxAtTable(builder: FragmentBuilder<F>): YRoute<StackFragState<F, StackType.Table<F>>, Single<Tuple2<Int, Bundle?>>>
            where F : Fragment, F : StackFragment, F : FragmentLifecycleOwner

    fun <F> routeStartFragmentForRx(builder: FragmentBuilder<F>): YRoute<StackFragState<F, StackType<F>>, Single<Tuple2<Int, Bundle?>>>
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

通过Uri字符串进行跳转的路由：

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

### 2. 运行YRoute

YRoute是根据当前上下文和状态进行变换的`Action`, 它需要在`CoreEngine`中才会被实际执行

`CoreEngine`是一个接口，描述了通常的运行YRoute方法：

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

可以自己实现该接口以提供不同的运行方式方式，默认实现了一个`MainCoreEngine`，通过Rx来进行运行队列的管理，串行运行：

```kotlin
MainCoreEngine.apply {
    core = create(this@App, ActivitiesState(emptyList())).unsafeRunSync()
    core.start().catchSubscribe()
}
```

Core创建好后就可以运行YRoute了：

```kotlin
routeStartStackActivity(ActivityBuilder(FragmentStackActivity::class.java)) // YRoute
    .start(core) //IO<Result<R>>

lazyR1(routeStartStackActivity(ActivityBuilder(FragmentStackActivity::class.java)))
    .withParams("param")// LazyYRoute
    .start(core) //IO<Result<R>>
```

运行得到`IO<Result<R>>`， 这时候其实并没有真正被执行，还需要启动它，启动方式有两种：

可以通过`IO`类型自己的启动方式启动：

```kotlin
io.unsafeAsyncRunDefault()

io.unsafeRunSync()

io.unsafeRunAsync()

io.unsafeRunAsyncCancellable()
```

也可以转换为Rx流后用Rx启动：

```kotlin
io.toSingle()
```

会得到一个`YResult`类型的值，它有`Success`和`Fail`两种可选类型，代表结果是成功还是失败

---

## 相关文章

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






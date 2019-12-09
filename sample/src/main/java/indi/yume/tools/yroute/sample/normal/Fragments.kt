package indi.yume.tools.yroute.sample.normal

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.CallSuper
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import arrow.core.Either
import indi.yume.tools.yroute.*
import indi.yume.tools.yroute.datatype.*
import indi.yume.tools.yroute.sample.App
import indi.yume.tools.yroute.sample.R
import io.reactivex.subjects.Subject

abstract class BaseFragment : Fragment(), StackFragment, FragmentLifecycleOwner {
    override val lifeSubject: Subject<FragmentLifeEvent> = FragmentLifecycleOwner.defaultLifeSubject()

    override var controller: FragController = FragController.defaultController()

    open val core: CoreEngine<ActivitiesState> by lazy { (activity!!.application as App).core }

    init {
        bindFragmentLife()
                .doOnNext { Logger.d("---> ${this::class.java.simpleName}", it.toString()) }
                .catchSubscribe()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) StackRoute.run {
            SaveInstanceFragmentUtil.routeRestore<BaseFragment>(this@BaseFragment,
                    savedInstanceState) runAtF this@BaseFragment
        }.start(core).flattenForYRoute().unsafeRunAsync { either ->
            if (either is Either.Left) either.a.printStackTrace()
        }

        super.onCreate(savedInstanceState)
        makeState(FragmentLifeEvent.OnCreate(this, savedInstanceState))
    }

    @CallSuper
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        makeState(FragmentLifeEvent.OnCreateView(this, inflater, container, savedInstanceState))
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        makeState(FragmentLifeEvent.OnViewCreated(this, view, savedInstanceState))
    }

    override fun onStart() {
        super.onStart()
        makeState(FragmentLifeEvent.OnStart(this))
    }

    override fun preSendFragmentResult(requestCode: Int, resultCode: Int, data: Bundle?) {
        makeState(FragmentLifeEvent.PreSendFragmentResult(this, requestCode, resultCode, data))
    }

    override fun onFragmentResult(requestCode: Int, resultCode: Int, data: Bundle?) {
        makeState(FragmentLifeEvent.OnFragmentResult(this, requestCode, resultCode, data))
    }

    override fun onResume() {
        super.onResume()
        makeState(FragmentLifeEvent.OnResume(this))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        StackRoute.run {
            SaveInstanceFragmentUtil.routeSave<BaseFragment>(this@BaseFragment,
                    outState) runAtF this@BaseFragment
        }.start(core).flattenForYRoute().unsafeRunAsync { either ->
            if (either is Either.Left) either.a.printStackTrace()
        }
        makeState(FragmentLifeEvent.OnSaveInstanceState(this, outState))
    }

    override fun onDestroy() {
        super.onDestroy()
        makeState(FragmentLifeEvent.OnDestroy(this))
        destroyLifecycle()
    }
}

class FragmentPage1 : BaseFragment() {
    override val core: CoreEngine<ActivitiesState> by lazy { (activity!!.application as App).core }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        Logger.d("FragmentPage1", "onCreateView")
        return inflater.inflate(R.layout.fragment_1, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<Button>(R.id.jump_other_for_result_btn).setOnClickListener {
            StackRoute.run {
                val requestCode = 2
                ActivitiesRoute.routeStartActivityForResult(
                        ActivityBuilder(SingleStackActivity::class.java)
                                .withAnimData(AnimData()), requestCode)
                        .flatMapR {
                            routeStartFragmentForRx<BaseFragment>(FragmentBuilder(FragmentOther::class.java)
                                    .withParam(OtherParam("This is param from FragmentPage1."))
                            ) runAtA it
                        }
            }.start(core).flattenForYRoute().unsafeAsyncRunDefault({
                val s = it.doOnSuccess {
                    Toast.makeText(activity,
                            "YResult from Other fragment: \nresultCode=${it.a}, data=${it.b?.getString("msg")}",
                            Toast.LENGTH_SHORT).show()
                }.catchSubscribe()

                println("Shared FragmentPage1: $it")
            })
        }

        view.findViewById<View>(R.id.jump_other_with_shared_view_btn).setOnClickListener {
            StackRoute.run {
                val builder: FragmentBuilder<BaseFragment> = FragmentBuilder(FragmentOther::class.java)
                        .withParam(OtherParam("This is param from FragmentPage1."))
                startWithShared(builder, view.findViewById(R.id.page_1_search_edit)) runAtF
                        this@FragmentPage1
            }.start(core).flattenForYRoute().unsafeAsyncRunDefault({
                println("Shared FragmentPage1: $it")
            })
        }
    }
}

class FragmentPage2 : BaseFragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        Logger.d("FragmentPage2", "onCreateView")
        return inflater.inflate(R.layout.fragment_2, container, false)
    }
}

class FragmentPage3 : BaseFragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        Logger.d("FragmentPage3", "onCreateView")
        return inflater.inflate(R.layout.fragment_3, container, false)
    }
}


class FragmentOther : BaseFragment(), FragmentParam<OtherParam> {
    override val injector: Subject<OtherParam> = FragmentParam.defaultInjecter()

    override val core: CoreEngine<ActivitiesState> by lazy { (activity!!.application as App).core }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        val view = inflater.inflate(R.layout.fragment_other, container, false)
        view.findViewById<TextView>(R.id.other_message).text = "page hash: ${hashCode()}"

        view.findViewById<Button>(R.id.jump_with_anim_btn).setOnClickListener listener@{
            StackRoute.run {
                val builder: FragmentBuilder<BaseFragment> = FragmentBuilder(FragmentOther::class.java)
                        .withAnimData(AnimData())
                        .withParam(OtherParam("This is param from FragmentPage1."))

                routeStartFragmentForRx(builder) runAtF this@FragmentOther
            }.start(core).flattenForYRoute().unsafeAsyncRunDefault({
                println("Start Anim FragmentOther: $it")
                it.doOnSuccess { (resultCode, data) ->
                    Toast.makeText(requireContext(),
                            "Other${hashCode()} on result: $resultCode, data=$data",
                            Toast.LENGTH_SHORT).show()
                }.catchSubscribe()
            })
        }
        return view
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val s = injector.subscribe {
            Toast.makeText(activity, it.toString(), Toast.LENGTH_SHORT).show()
        }

        setResult(999, bundleOf(
                "msg" to "This is result from FragmentOther."
        ))
    }
}

data class OtherParam(val message: String)
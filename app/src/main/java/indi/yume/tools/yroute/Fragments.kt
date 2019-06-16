package indi.yume.tools.yroute

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.CallSuper
import androidx.fragment.app.Fragment
import indi.yume.tools.yroute.test4.*
import io.reactivex.subjects.Subject

abstract class BaseFragment : Fragment(), StackFragment, FragmentLifecycleOwner {
    override val lifeSubject: Subject<FragmentLifeEvent> = FragmentLifecycleOwner.defaultLifeSubject()

    override var controller: FragController = FragController.defaultController()

    override fun onCreate(savedInstanceState: Bundle?) {
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

    override fun onFragmentResult(requestCode: Int, resultCode: Int, data: Bundle?) {
        makeState(FragmentLifeEvent.OnFragmentResult(this, requestCode, resultCode, data))
    }

    override fun onResume() {
        super.onResume()
        makeState(FragmentLifeEvent.OnResume(this))
    }

    override fun onDestroy() {
        super.onDestroy()
        makeState(FragmentLifeEvent.OnDestroy(this))
        destroyLifecycle()
    }
}

class FragmentPage1 : BaseFragment() {
    init {
        bindFragmentLife()
            .doOnNext { Logger.d("FragmentPage1", it.toString()) }
            .subscribe()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        Logger.d("FragmentPage1", "onCreateView")
        return inflater.inflate(R.layout.fragment_1, container, false)
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

    val core: CoreEngine<ActivitiesState> by lazy { (activity!!.application as App).core }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        val view = inflater.inflate(R.layout.fragment_3, container, false)
        view.findViewById<TextView>(R.id.other_message).text = "page hash: ${hashCode()}"
        return view
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val s = injector.subscribe {
            Toast.makeText(activity, it.toString(), Toast.LENGTH_LONG).show()
        }
    }
}

data class OtherParam(val message: String)
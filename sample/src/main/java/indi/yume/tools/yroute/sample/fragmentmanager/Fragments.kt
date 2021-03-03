package indi.yume.tools.yroute.sample.fragmentmanager

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.os.bundleOf
import indi.yume.tools.yroute.*
import indi.yume.tools.yroute.datatype.*
import indi.yume.tools.yroute.fragmentmanager.BaseManagerFragment
import indi.yume.tools.yroute.sample.App
import indi.yume.tools.yroute.sample.R
import io.reactivex.rxjava3.subjects.Subject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

abstract class BaseFragment : BaseManagerFragment<BaseFragment>(), CoroutineScope by MainScope() {
    override val core: CoreEngine<ActivitiesState> by lazy { (activity!!.application as App).core }

    override fun onDestroy() {
        super.onDestroy()
        cancel()
    }
}

class FragmentPage1 : BaseFragment() {
    init {
        bindFragmentLife()
            .doOnNext { Logger.d("FragmentPage1", it.toString()) }
            .catchSubscribe()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        Logger.d("FragmentPage1", "onCreateView")
        return inflater.inflate(R.layout.fragment_1, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<Button>(R.id.jump_other_for_result_btn).setOnClickListener { this@FragmentPage1.launch {
            val result = startFragmentForRx(FragmentBuilder(FragmentOther::class.java)
                    .withParam(OtherParam("This is param from FragmentPage1.")))

            Toast.makeText(activity,
                    "YResult from Other fragment: \nresultCode=${result?.a}, data=${result?.b?.getString("msg")}",
                    Toast.LENGTH_SHORT).show()
        } }

        view.findViewById<View>(R.id.jump_other_with_shared_view_btn).setOnClickListener { this@FragmentPage1.launch {
            StackRoute.run {
                val builder: FragmentBuilder<BaseFragment> = FragmentBuilder(FragmentOther::class.java)
                        .withParam(OtherParam("This is param from FragmentPage1."))
                startWithShared(builder, view.findViewById(R.id.page_1_search_edit)) runAtF
                        this@FragmentPage1
            }.startLazy(core).flattenForYRoute().let {
                println("Shared FragmentPage1: $it")
            }
        } }
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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        val view = inflater.inflate(R.layout.fragment_other, container, false)
        view.findViewById<TextView>(R.id.other_message).text = "page hash: ${hashCode()}"

        view.findViewById<Button>(R.id.jump_with_anim_btn).setOnClickListener listener@{  this@FragmentOther.launch {
            startFragment(FragmentBuilder(FragmentOther::class.java)
                    .withAnimData(AnimData())
                    .withParam(OtherParam("This is param from FragmentPage1.")))
                    .let {
                        println("Start Anim FragmentOther: $it")
                    }
        } }
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
package org.soralis.droidsillica.ui.tab

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import org.soralis.droidsillica.databinding.FragmentTabBinding
import org.soralis.droidsillica.model.TabContent
import org.soralis.droidsillica.ui.tab.view.BaseTabView
import org.soralis.droidsillica.ui.tab.view.HistoryView
import org.soralis.droidsillica.ui.tab.view.ManualView
import org.soralis.droidsillica.ui.tab.view.ReadView
import org.soralis.droidsillica.ui.tab.view.TabView
import org.soralis.droidsillica.ui.tab.view.WriteView

class TabFragment : Fragment() {

    private var _binding: FragmentTabBinding? = null
    private val binding get() = _binding!!
    private var tabView: TabView? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTabBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val args = requireArguments()
        val key = args.getString(ARG_KEY).orEmpty()
        val title = args.getString(ARG_TITLE).orEmpty()
        val description = args.getString(ARG_DESCRIPTION).orEmpty()
        val actions = args.getStringArray(ARG_ACTIONS)?.toList().orEmpty()

        val content = TabContent(
            key = key,
            title = title,
            description = description,
            actions = actions
        )

        tabView = createTabView(content.key)
        tabView?.render(content)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        tabView = null
        _binding = null
    }

    private fun createTabView(key: String): TabView = when (key) {
        "read" -> ReadView(binding)
        "write" -> WriteView(binding)
        "manual" -> ManualView(binding)
        "history" -> HistoryView(binding)
        else -> BaseTabView(binding)
    }

    companion object {
        private const val ARG_KEY = "arg_key"
        private const val ARG_TITLE = "arg_title"
        private const val ARG_DESCRIPTION = "arg_description"
        private const val ARG_ACTIONS = "arg_actions"

        fun newInstance(content: TabContent): TabFragment = TabFragment().apply {
            arguments = bundleOf(
                ARG_KEY to content.key,
                ARG_TITLE to content.title,
                ARG_DESCRIPTION to content.description,
                ARG_ACTIONS to content.actions.toTypedArray()
            )
        }
    }
}

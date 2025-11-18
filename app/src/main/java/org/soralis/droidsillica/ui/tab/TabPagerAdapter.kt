package org.soralis.droidsillica.ui.tab

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import org.soralis.droidsillica.model.TabContent

class TabPagerAdapter(
    activity: FragmentActivity,
    private val tabs: List<TabContent>,
    private val expertMode: Boolean
) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = tabs.size

    override fun createFragment(position: Int): Fragment =
        TabFragment.newInstance(tabs[position], expertMode)
}

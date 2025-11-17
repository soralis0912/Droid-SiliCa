package org.soralis.droidsillica.ui.tab

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import org.soralis.droidsillica.controller.TabController

class TabPagerAdapter(
    activity: FragmentActivity,
    private val controller: TabController
) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = controller.getTabCount()

    override fun createFragment(position: Int): Fragment =
        TabFragment.newInstance(controller.getTab(position))
}

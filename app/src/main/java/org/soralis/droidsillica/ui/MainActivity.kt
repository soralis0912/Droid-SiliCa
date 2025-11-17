package org.soralis.droidsillica.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.tabs.TabLayoutMediator
import org.soralis.droidsillica.controller.TabController
import org.soralis.droidsillica.databinding.ActivityMainBinding
import org.soralis.droidsillica.ui.tab.TabPagerAdapter

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val tabController = TabController()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupTabs()
    }

    private fun setupTabs() {
        val pagerAdapter = TabPagerAdapter(this, tabController)
        binding.viewPager.adapter = pagerAdapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = tabController.getTitle(position)
        }.attach()
    }
}

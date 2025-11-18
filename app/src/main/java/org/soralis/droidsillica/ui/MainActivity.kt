package org.soralis.droidsillica.ui

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayoutMediator
import org.soralis.droidsillica.R
import org.soralis.droidsillica.controller.TabController
import org.soralis.droidsillica.databinding.ActivityMainBinding
import org.soralis.droidsillica.ui.tab.TabPagerAdapter
import org.soralis.droidsillica.util.ExpertModeManager

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val tabController = TabController()
    private var tabMediator: TabLayoutMediator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.topAppBar)
        setupTabs()
    }

    private fun setupTabs() {
        tabMediator?.detach()
        val expertMode = ExpertModeManager.isExpertModeEnabled(this)
        val tabs = tabController.getTabs(expertMode)
        binding.viewPager.adapter = TabPagerAdapter(this, tabs, expertMode)
        tabMediator = TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = tabs[position].title
        }.also { it.attach() }
    }

    override fun onDestroy() {
        tabMediator?.detach()
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_expert_mode -> {
                showExpertModeDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showExpertModeDialog() {
        val isExpert = ExpertModeManager.isExpertModeEnabled(this)
        val options = arrayOf(
            getString(R.string.settings_mode_basic),
            getString(R.string.settings_mode_expert)
        )
        val checkedItem = if (isExpert) 1 else 0
        var selectedIndex = checkedItem
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_expert_mode_title)
            .setSingleChoiceItems(options, checkedItem) { _, which ->
                selectedIndex = which
            }
            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                val enableExpert = selectedIndex == 1
                ExpertModeManager.setExpertModeEnabled(this, enableExpert)
                dialog.dismiss()
                setupTabs()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}

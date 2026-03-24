package io.josrom.whereismycar

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import io.josrom.whereismycar.databinding.ActivityOnboardingBinding

class OnboardingActivity : AppCompatActivity() {
    private lateinit var binding: ActivityOnboardingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val pages = listOf(
            OnboardingPage(getString(R.string.onboarding_title_1), getString(R.string.onboarding_description_1), R.layout.item_onboarding_1),
            OnboardingPage(getString(R.string.onboarding_title_2), getString(R.string.onboarding_description_2), R.layout.item_onboarding_2),
            OnboardingPage(getString(R.string.onboarding_title_3), getString(R.string.onboarding_description_3), R.layout.item_onboarding_3)
        )

        val adapter = OnboardingAdapter(pages) {
            // Handle Skip click from items
            navigateToMain()
        }
        binding.viewPager.adapter = adapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { _, _ -> }.attach()

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateDots(position)

                if (position == pages.size - 1) {
                    binding.btnNext.text = getString(R.string.get_started)
                    // Optionally add an icon: binding.btnNext.setIconResource(R.drawable.ic_arrow_right)
                } else {
                    binding.btnNext.text = getString(R.string.next)
                }
            }
        })

        binding.btnNext.setOnClickListener {
            if (binding.viewPager.currentItem < pages.size - 1) {
                binding.viewPager.currentItem += 1
            } else {
                navigateToMain()
            }
        }

        this.updateDots(0)
    }

    private fun navigateToMain() {
        // Mark onboarding as completed
        val sharedPref = getSharedPreferences("app_prefs", MODE_PRIVATE)
        sharedPref.edit {
            putBoolean("onboarding_completed", true)
        }

        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun updateDots(selectedPosition: Int) {
        for (i in 0 until binding.tabLayout.tabCount) {
            val tab = binding.tabLayout.getTabAt(i)
            tab?.setIcon(if (i == selectedPosition) R.drawable.dot_active else R.drawable.dot_inactive)
        }
    }
}

package io.josrom.whereismycar

import androidx.annotation.DrawableRes
import androidx.annotation.LayoutRes

data class OnboardingPage(
    val title: String = "",
    val description: String = "",
    @field:DrawableRes val imageRes: Int = 0,
    val showSkip: Boolean = true,
    @field:LayoutRes val layoutRes: Int = R.layout.item_onboarding
)

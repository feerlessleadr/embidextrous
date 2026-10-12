package com.feerlessleadr.embidextrous.watching

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.graphics.toArgb
import com.feerlessleadr.embidextrous.App
import com.feerlessleadr.embidextrous.ui.colorSchemeFor

/** The colours the home-screen widgets need at runtime: the chosen accent, in the light or dark form that suits the phone's current setting. */
object WidgetColors {
    private fun dark(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    fun accent(context: Context): Int {
        val settings = (context.applicationContext as App).container.settings
        return colorSchemeFor(settings.accent.value, dark(context)).primary.toArgb()
    }

    /** The colour for "needs attention": amber, darker on a light widget so it stays readable. */
    fun warning(context: Context): Int = if (dark(context)) 0xFFFFB74D.toInt() else 0xFF9A5B00.toInt()
}

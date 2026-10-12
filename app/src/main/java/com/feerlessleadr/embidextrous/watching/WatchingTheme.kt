package com.feerlessleadr.embidextrous.watching

import androidx.compose.runtime.Composable

/**
 * Every colour scheme in the app now has neutral greys for its surfaces and uses the accent only for highlights, so the Home and
 * Hall Monitor screens need no special treatment any more. Kept as a wrapper so the screens do not change.
 */
@Composable
internal fun WatchingEyeTheme(content: @Composable () -> Unit) = content()

package com.feerlessleadr.embidextrous.push

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/**
 * The shape of every screen that stays up to date: look once at the start, then let Emby push the changes, and look again by asking only while the push
 * connection is not up (every [fallbackEveryMs]). Runs until the calling coroutine is cancelled (a screen leaving the foreground), and gives the topics back then.
 *
 * - [topics] are held open for as long as this runs.
 * - [accept] picks the pushed messages that matter; [onPush] applies each one.
 * - [refresh] asks over REST: once at the start, and as the fallback.
 */
suspend fun EmbyPush.keepFresh(
    topics: Array<EmbyTopic>,
    fallbackEveryMs: Long,
    accept: (PushEvent) -> Boolean,
    onPush: suspend (PushEvent) -> Unit,
    refresh: suspend () -> Unit,
) {
  coroutineScope {
    val lease = acquire(*topics)
    try {
        launch { events.filter(accept).collect { onPush(it) } }
        refresh()
        // Pushes are flowing: nothing more to do. They are not: ask now and then, until they are.
        connected.collectLatest { up ->
            if (!up) {
                while (true) {
                    delay(fallbackEveryMs)
                    refresh()
                }
            }
        }
    } finally {
        lease.release()
    }
  }
}

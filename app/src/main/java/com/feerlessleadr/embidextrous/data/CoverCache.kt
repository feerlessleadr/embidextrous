package com.feerlessleadr.embidextrous.data

import android.graphics.Bitmap
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Small cover pictures of the Emby lists, kept in memory while the app runs. A cover is looked up again when the
 * list's [stamp] (its last refresh time) changes, because a refresh can change the collage.
 */
class CoverCache {
    private val covers = LruCache<String, Bitmap>(64)
    private val missing = ConcurrentHashMap.newKeySet<String>()

    suspend fun load(api: ApiClient, itemId: String, stamp: String?): Bitmap? {
        val key = "$itemId@${stamp.orEmpty()}"
        covers.get(key)?.let { return it }
        if (key in missing) return null
        val bytes = try {
            api.embyCover(itemId)
        } catch (e: ApiException) {
            return null // a failed look-up is not remembered: try again next time
        }
        val bitmap = bytes?.let { withContext(Dispatchers.Default) { Safety.decodeBounded(it, 400) } }
        if (bitmap == null) {
            missing.add(key)
            return null
        }
        covers.put(key, bitmap)
        return bitmap
    }

    fun clear() {
        covers.evictAll()
        missing.clear()
    }
}
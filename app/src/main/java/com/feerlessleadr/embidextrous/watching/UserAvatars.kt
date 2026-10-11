package com.feerlessleadr.embidextrous.watching

import android.graphics.Bitmap
import android.util.LruCache
import com.feerlessleadr.embidextrous.data.ApiClient
import com.feerlessleadr.embidextrous.data.ApiException
import com.feerlessleadr.embidextrous.data.Safety
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/** Small profile pictures of the limited users, kept in memory while the app runs. A user without a picture is remembered, so the server is not asked again. */
class UserAvatars {
    private val pictures = LruCache<String, Bitmap>(32)
    private val without = ConcurrentHashMap.newKeySet<String>()

    suspend fun load(api: ApiClient, userId: String): Bitmap? {
        pictures.get(userId)?.let { return it }
        if (userId in without) return null
        val bytes = try {
            api.embyUserImage(userId)
        } catch (e: ApiException) {
            return null // a failed look-up is not remembered: try again next time
        } catch (e: IllegalArgumentException) {
            return null // not a usable user id
        }
        val bitmap = bytes?.let { withContext(Dispatchers.Default) { Safety.decodeBounded(it, 192) } }
        if (bitmap == null) {
            without.add(userId)
            return null
        }
        pictures.put(userId, bitmap)
        return bitmap
    }

    fun clear() {
        pictures.evictAll()
        without.clear()
    }
}

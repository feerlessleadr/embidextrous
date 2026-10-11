package com.feerlessleadr.embidextrous.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.InputStream

// Limits for things that come from outside the app: a shared list, pictures sent by the server.
object Safety {
    // The largest shared list (pasted or from a file) the app reads: real lists are a few kilobytes.
    const val MAX_IMPORT_BYTES = 1_000_000

    // JSON nested deeper than this is refused before parsing, so a hostile paste cannot overflow the parser's stack.
    const val MAX_JSON_DEPTH = 64

    // The most a cover picture response may be (the server is asked for a 200 px one).
    const val MAX_COVER_BYTES = 8L * 1024 * 1024

    // The most a custom list image response may be (the plugin accepts up to 20 MB).
    const val MAX_IMAGE_BYTES = 25L * 1024 * 1024

    // Pictures with more pixels than this are not decoded at all.
    private const val MAX_PIXELS = 100_000_000L

    // Reads at most MAX_IMPORT_BYTES from the stream; null when there is more than that. Never holds more than the limit.
    fun readImport(input: InputStream): ByteArray? {
        val out = ByteArrayOutputStream()
        val chunk = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val n = input.read(chunk)
            if (n < 0) break
            total += n
            if (total > MAX_IMPORT_BYTES) return null
            out.write(chunk, 0, n)
        }
        return out.toByteArray()
    }

    // True when the text nests arrays or objects deeper than maxDepth (brackets inside strings do not count).
    fun jsonTooDeep(text: String, maxDepth: Int = MAX_JSON_DEPTH): Boolean {
        var depth = 0
        var inString = false
        var escaped = false
        for (c in text) {
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '[', '{' -> if (++depth > maxDepth) return true
                ']', '}' -> if (depth > 0) depth--
            }
        }
        return false
    }

    // The power-of-two shrink factor that brings a width by height picture within maxSide pixels.
    fun sampleSize(width: Int, height: Int, maxSide: Int): Int {
        var sample = 1
        while (width / sample > maxSide || height / sample > maxSide) sample *= 2
        return sample
    }

    // A picture decoded no larger than about maxSide pixels, or null when it is not a picture, absurdly large, or does not fit in memory.
    fun decodeBounded(bytes: ByteArray, maxSide: Int): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outWidth.toLong() * bounds.outHeight > MAX_PIXELS) {
            null
        } else {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxSide) })
        }
    } catch (e: OutOfMemoryError) {
        null
    }
}

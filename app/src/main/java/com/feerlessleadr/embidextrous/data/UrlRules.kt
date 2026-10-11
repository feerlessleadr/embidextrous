package com.feerlessleadr.embidextrous.data

import java.net.URI

/** Turns what a person types into the server address the app uses, and enforces HTTPS. */
object UrlRules {

    sealed interface Result {
        data class Ok(val serverUrl: String) : Result
        data class Rejected(val reason: String) : Result
    }

    /**
     * @param allowCleartext true only in debug builds, so a local test server can be used without TLS.
     */
    fun normalize(input: String, allowCleartext: Boolean): Result {
        var text = input.trim()
        if (text.isEmpty()) return Result.Rejected("Enter the address of your Emby server.")

        if (!text.contains("://")) text = "https://$text"

        val uri = try {
            URI(text)
        } catch (e: Exception) {
            return Result.Rejected("That does not look like a valid address.")
        }

        val scheme = uri.scheme?.lowercase()
        if (scheme != "https" && scheme != "http") {
            return Result.Rejected("Use an https:// address.")
        }
        if (scheme == "http" && !allowCleartext) {
            return Result.Rejected("This app only connects over HTTPS. Use an https:// address.")
        }
        if (uri.host.isNullOrBlank()) return Result.Rejected("That does not look like a valid address.")
        if (!uri.userInfo.isNullOrEmpty()) return Result.Rejected("Do not put a user name or password in the address.")

        // Keep scheme, host, port and any sub-path (a reverse proxy may serve Emby under /emby), drop a trailing slash.
        val port = if (uri.port > 0) ":${uri.port}" else ""
        val path = (uri.rawPath ?: "").trimEnd('/')
        return Result.Ok("$scheme://${uri.host}$port$path")
    }
}

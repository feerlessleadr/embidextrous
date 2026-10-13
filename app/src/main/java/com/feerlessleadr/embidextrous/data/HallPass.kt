package com.feerlessleadr.embidextrous.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

/** What the Hall Pass plugin (explicit-content tagger for music) reports at /HallPass/Info. */
data class HpInfo(val version: String, val apiVersion: Int)

/** Where the Hall Pass tab stands. */
sealed interface HpGate {
    data object Checking : HpGate
    data object NotInstalled : HpGate
    data class TooOld(val info: HpInfo) : HpGate
    data class Ready(val info: HpInfo) : HpGate
    data class Unreachable(val message: String) : HpGate
}

object HallPass {
    /** The Hall Pass API version this app understands. */
    const val SUPPORTED = 1

    /** Hall Pass 1.1.0.0 is the first version with everything this tab uses (albums, select all matching). */
    const val MIN_PLUGIN = "1.1.0.0"
}

data class HpScanJob(val state: String, val total: Int, val processed: Int, val message: String) {
    val isRunning: Boolean get() = state == "Running"
}

data class HpWriteJob(val state: String, val total: Int, val processed: Int, val message: String) {
    val isRunning: Boolean get() = state == "Running"
}

data class HpStatus(
    val total: Int,
    val byDecision: Map<String, Int>,
    /** How many tracks are in review for each reason (NoEvidence, Suspected, Conflict, WeakClean). */
    val byReason: Map<String, Int>,
    val overrides: Int,
    val dryRun: Boolean,
    val scan: HpScanJob,
    val write: HpWriteJob,
    val version: String,
) {
    fun count(decision: String): Int = byDecision[decision] ?: 0

    fun reasonCount(reason: String): Int = byReason[reason] ?: 0
}

data class HpRow(
    val id: Long,
    val name: String,
    val artist: String,
    val album: String,
    val decision: String,
    val confidence: Double,
    val reviewReason: String,
    val reason: String,
    val override: String,
    val skipped: Boolean,
    val evidence: List<String>,
    /** How often the track has been played, all Emby users added up (0 from a plugin older than 1.2). */
    val plays: Int = 0,
) {
    val manual: Boolean get() = override != "None" && override.isNotEmpty()
}

data class HpAlbum(val artist: String, val album: String, val matching: Int, val total: Int, val byDecision: Map<String, Int>, val plays: Int = 0)

data class HpArtist(val artist: String, val matching: Int, val total: Int, val albums: Int, val byDecision: Map<String, Int>, val plays: Int = 0)

data class HpPreview(val total: Int, val wouldChange: Int, val rollUps: Int, val unchanged: Int, val dryRun: Boolean, val byTag: Map<String, Int>)

data class HpDecideResult(val changed: Int, val matched: Int, val truncated: Boolean)

/** Reads the Hall Pass plugin's answers. Lenient: a missing field becomes a harmless default so a newer plugin cannot break the tab. */
object HallPassParser {
    private fun obj(text: String): JsonObject = AppJson.parseToJsonElement(text).jsonObject

    private fun JsonObject.str(key: String): String = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull.orEmpty()
    private fun JsonObject.int(key: String): Int = (this[key] as? JsonPrimitive)?.intOrNull ?: 0
    private fun JsonObject.bool(key: String): Boolean = (this[key] as? JsonPrimitive)?.booleanOrNull ?: false

    private fun counts(e: JsonElement?): Map<String, Int> =
        (e as? JsonObject)?.entries?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.intOrNull?.let { k to it } }?.toMap().orEmpty()

    fun info(text: String): HpInfo {
        val o = obj(text)
        return HpInfo(o.str("Version"), o.int("ApiVersion"))
    }

    fun status(text: String): HpStatus {
        val o = obj(text)
        val stats = o["Stats"] as? JsonObject ?: JsonObject(emptyMap())
        val job = o["Job"] as? JsonObject ?: JsonObject(emptyMap())
        val write = o["Write"] as? JsonObject ?: JsonObject(emptyMap())
        return HpStatus(
            total = stats.int("Total"),
            byDecision = counts(stats["ByDecision"]),
            byReason = counts(stats["ByReason"]),
            overrides = stats.int("Overrides"),
            dryRun = o.bool("DryRun"),
            scan = HpScanJob(job.str("State"), job.int("Total"), job.int("Processed"), job.str("Message")),
            write = HpWriteJob(write.str("State"), write.int("Total"), write.int("Processed"), write.str("Message")),
            version = o.str("Version"),
        )
    }

    fun row(o: JsonObject): HpRow = HpRow(
        id = (o["Id"] as? JsonPrimitive)?.longOrNull ?: 0L,
        name = o.str("Name"),
        artist = o.str("Artist"),
        album = o.str("Album"),
        decision = o.str("Decision"),
        confidence = (o["Confidence"] as? JsonPrimitive)?.doubleOrNull ?: 0.0,
        reviewReason = o.str("ReviewReason"),
        reason = o.str("Reason"),
        override = o.str("Override"),
        skipped = o.bool("Skipped"),
        evidence = (o["Evidence"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty(),
        plays = o.int("Plays"),
    )

    /** A page of the review list: the total that matches and the rows of this page. */
    fun review(text: String): Pair<Int, List<HpRow>> {
        val o = obj(text)
        return o.int("Total") to ((o["Items"] as? JsonArray)?.mapNotNull { (it as? JsonObject)?.let(::row) }.orEmpty())
    }

    fun albums(text: String): Pair<Int, List<HpAlbum>> {
        val o = obj(text)
        val items = (o["Items"] as? JsonArray)?.mapNotNull { e ->
            (e as? JsonObject)?.let { HpAlbum(it.str("Artist"), it.str("Album"), it.int("Matching"), it.int("Total"), counts(it["ByDecision"]), it.int("Plays")) }
        }.orEmpty()
        return o.int("Total") to items
    }

    fun artists(text: String): Pair<Int, List<HpArtist>> {
        val o = obj(text)
        val items = (o["Items"] as? JsonArray)?.mapNotNull { e ->
            (e as? JsonObject)?.let { HpArtist(it.str("Artist"), it.int("Matching"), it.int("Total"), it.int("Albums"), counts(it["ByDecision"]), it.int("Plays")) }
        }.orEmpty()
        return o.int("Total") to items
    }

    fun preview(text: String): HpPreview {
        val o = obj(text)
        return HpPreview(o.int("Total"), o.int("WouldChange"), o.int("RollUps"), o.int("Unchanged"), o.bool("DryRun"), counts(o["ByTag"]))
    }

    fun decide(text: String): HpDecideResult {
        val o = obj(text)
        return HpDecideResult(o.int("Changed"), o.int("Matched"), o.bool("Truncated"))
    }

    /** True when [version] (like "1.1.0.0") is at least [minimum]; anything unreadable counts as too old. */
    fun atLeast(version: String, minimum: String): Boolean {
        val a = version.split('.').map { it.toIntOrNull() ?: return false }
        val b = minimum.split('.').map { it.toIntOrNull() ?: return false }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return true
    }

    fun gate(info: HpInfo): HpGate =
        if (info.apiVersion < HallPass.SUPPORTED || !atLeast(info.version, HallPass.MIN_PLUGIN)) HpGate.TooOld(info) else HpGate.Ready(info)
}

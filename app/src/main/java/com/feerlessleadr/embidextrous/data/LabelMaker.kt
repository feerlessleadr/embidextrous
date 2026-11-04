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

/** What the Label Maker plugin reports at /LabelMaker/Info. */
data class LmInfo(val version: String, val apiLevel: Int, val features: Set<String>)

/** Where the Label Maker tab stands. */
sealed interface LmGate {
    data object Checking : LmGate
    data object NotInstalled : LmGate
    data class TooOld(val info: LmInfo) : LmGate
    data class Ready(val info: LmInfo) : LmGate
    data class Unreachable(val message: String) : LmGate
}

object LabelMaker {
    /** Plugin 0.1.8.0 is the first with everything this tab uses (keep both / replace / file only the new files, and filing of later tracks). */
    const val MIN_PLUGIN = "0.1.8.0"

    /** The states an album can be in, in the plugin's words, in the order the filter shows them. */
    val STATES = listOf("NeedsReview", "Ready", "NoMatch", "Waiting", "Failed", "Duplicate", "Skipped", "Applying", "Done")

    /** True while the plugin is working on the album, so no choice can be made on it. */
    fun isBusy(state: String): Boolean = state == "Applying" || state == "Matching"

    /** The plugin says a folder holds several albums in its reason sentence; only then is Split worth offering. */
    fun canSplit(album: LmAlbum): Boolean = album.kind == "Album" && Regex("holds \\d+ different albums").containsMatchIn(album.reason)

    fun stateLabel(state: String): String = when (state) {
        "NeedsReview" -> "Needs review"
        "NoMatch" -> "No match"
        "Ready" -> "Ready"
        "Waiting" -> "Waiting"
        "Matching" -> "Checking"
        "Applying" -> "Filing"
        "Done" -> "Filed"
        "Failed" -> "Failed"
        "Duplicate" -> "Already in library"
        "Skipped" -> "Left alone"
        else -> state
    }
}

data class LmProblem(val kind: String, val message: String, val blocking: Boolean)

data class LmLane(val id: String, val name: String, val counts: Map<String, Int>, val problems: List<LmProblem>) {
    fun count(state: String): Int = counts[state] ?: 0
}

/** The body of GET /LabelMaker/Status, which is also what the status websocket channel pushes. */
data class LmStatus(
    val version: Long,
    val paused: Boolean,
    val dryRun: Boolean,
    val lastPassUtc: String,
    val counts: Map<String, Int>,
    val lanes: List<LmLane>,
    val musicBrainzTrouble: Boolean,
    /** Set when the plugin refused the websocket start (not an administrator); never set for the REST route. */
    val error: String? = null,
) {
    fun count(state: String): Int = counts[state] ?: 0

    val problems: List<LmProblem> get() = lanes.flatMap { it.problems }
}

data class LmAlbum(
    val id: String,
    val laneId: String,
    val laneName: String,
    val artist: String,
    val album: String,
    val folder: String,
    val kind: String,
    val state: String,
    val reason: String,
    val files: Int,
    val held: Boolean,
    val discarded: Boolean,
    val score: Double,
    val bestReleaseId: String,
    val bestTitle: String,
    val bestArtist: String,
    val destination: String,
)

data class LmCandidate(val releaseId: String, val title: String, val artist: String, val country: String, val date: String, val score: Double, val weakFiles: Int)

data class LmFile(val file: String, val title: String, val artist: String, val album: String, val track: Int, val disc: Int, val seconds: Int, val error: String = "")

data class LmAlbumDetail(val album: LmAlbum, val notes: List<String>, val candidates: List<LmCandidate>, val files: List<LmFile>, val outcome: String)

data class LmPreviewFile(val source: String, val destination: String, val disc: Int, val position: Int, val title: String, val score: Double, val weak: Boolean, val existing: Boolean)

data class LmPreview(
    val success: Boolean,
    val message: String,
    val releaseId: String,
    val title: String,
    val artist: String,
    val date: String,
    val country: String,
    val score: Double,
    val files: List<LmPreviewFile>,
    val problems: List<String>,
    val notes: List<String>,
    val canApply: Boolean,
    val isDuplicate: Boolean,
    val duplicateCount: Int,
    val destination: String,
)

/** The answer of a review action: whether it worked, a sentence for the person, and the album's state afterwards. */
data class LmActionResult(val success: Boolean, val message: String, val state: String)

/** The answer of a search or a pasted link: the editions found, each scored against the files. */
data class LmSearchResult(val success: Boolean, val message: String, val candidates: List<LmCandidate>)

/** What to do about files the library already has: nothing chosen yet, keep both, replace, or file only the new ones. */
enum class LmDuplicateChoice(val wire: String, val label: String) {
    Ask("", "Ask me"),
    KeepBoth("KeepBoth", "Keep both"),
    Replace("Replace", "Replace the old ones"),
    SkipExisting("SkipExisting", "File only the new files"),
}

/** Reads the Label Maker plugin's answers. Lenient: a missing field becomes a harmless default so a newer plugin cannot break the tab. */
object LabelMakerParser {
    private fun obj(text: String): JsonObject = AppJson.parseToJsonElement(text).jsonObject

    private fun JsonObject.str(key: String): String = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull.orEmpty()
    private fun JsonObject.int(key: String): Int = (this[key] as? JsonPrimitive)?.intOrNull ?: 0
    private fun JsonObject.long(key: String): Long = (this[key] as? JsonPrimitive)?.longOrNull ?: 0L
    private fun JsonObject.dbl(key: String): Double = (this[key] as? JsonPrimitive)?.doubleOrNull ?: 0.0
    private fun JsonObject.bool(key: String): Boolean = (this[key] as? JsonPrimitive)?.booleanOrNull ?: false
    private fun JsonObject.objs(key: String): List<JsonObject> = (this[key] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
    private fun JsonObject.strings(key: String): List<String> = (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()

    private fun counts(e: JsonElement?): Map<String, Int> =
        (e as? JsonObject)?.entries?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.intOrNull?.let { k to it } }?.toMap().orEmpty()

    fun info(text: String): LmInfo {
        val o = obj(text)
        return LmInfo(o.str("PluginVersion"), o.int("ApiLevel"), o.strings("Features").toSet())
    }

    fun gate(info: LmInfo): LmGate =
        if (info.apiLevel < 1 || !HallPassParser.atLeast(info.version, LabelMaker.MIN_PLUGIN)) LmGate.TooOld(info) else LmGate.Ready(info)

    /** The status from the REST route or from the websocket's `Data` object. */
    fun status(o: JsonObject): LmStatus = LmStatus(
        version = o.long("Version"),
        paused = o.bool("Paused"),
        dryRun = o.bool("DryRun"),
        lastPassUtc = o.str("LastPassUtc"),
        counts = counts(o["Counts"]),
        lanes = o.objs("Lanes").map { lane ->
            LmLane(
                id = lane.str("LaneId"),
                name = lane.str("LaneName"),
                counts = counts(lane["Counts"]),
                problems = lane.objs("Problems").map { LmProblem(it.str("Kind"), it.str("Message"), it.bool("Blocking")) },
            )
        },
        musicBrainzTrouble = (o["Service"] as? JsonObject)?.str("MusicBrainz") == "Trouble",
        error = o.str("Error").takeIf { it.isNotEmpty() },
    )

    fun status(text: String): LmStatus = status(obj(text))

    fun album(o: JsonObject): LmAlbum = LmAlbum(
        id = o.str("Id"),
        laneId = o.str("LaneId"),
        laneName = o.str("LaneName"),
        artist = o.str("Artist"),
        album = o.str("Album"),
        folder = o.str("Folder"),
        kind = o.str("Kind"),
        state = o.str("State"),
        reason = o.str("Reason"),
        files = o.int("Files"),
        held = o.bool("Held"),
        discarded = o.bool("Discarded"),
        score = o.dbl("Score"),
        bestReleaseId = o.str("BestReleaseId"),
        bestTitle = o.str("BestTitle"),
        bestArtist = o.str("BestArtist"),
        destination = o.str("Destination"),
    )

    /** A page of albums: the total that matches and the albums of this page. */
    fun albums(text: String): Pair<Int, List<LmAlbum>> {
        val o = obj(text)
        return o.int("Total") to o.objs("Items").map(::album)
    }

    private fun candidate(o: JsonObject) = LmCandidate(o.str("ReleaseId"), o.str("Title"), o.str("Artist"), o.str("Country"), o.str("Date"), o.dbl("Score"), o.int("WeakFiles"))

    fun detail(text: String): LmAlbumDetail {
        val o = obj(text)
        return LmAlbumDetail(
            album = album(o),
            notes = o.strings("Notes"),
            candidates = o.objs("Candidates").map(::candidate),
            files = o.objs("FileList").map { LmFile(it.str("File"), it.str("Title"), it.str("Artist"), it.str("Album"), it.int("Track"), it.int("Disc"), it.int("Seconds"), it.str("Error")) },
            outcome = o.str("Outcome"),
        )
    }

    fun preview(text: String): LmPreview {
        val o = obj(text)
        return LmPreview(
            success = o.bool("Success"),
            message = o.str("Message"),
            releaseId = o.str("ReleaseId"),
            title = o.str("Title"),
            artist = o.str("Artist"),
            date = o.str("Date"),
            country = o.str("Country"),
            score = o.dbl("Score"),
            files = o.objs("Files").map {
                LmPreviewFile(it.str("Source"), it.str("Destination"), it.int("Disc"), it.int("Position"), it.str("Title"), it.dbl("Score"), it.bool("Weak"), it.bool("Existing"))
            },
            problems = o.strings("Problems"),
            notes = o.strings("Notes"),
            canApply = o.bool("CanApply"),
            isDuplicate = o.bool("IsDuplicate"),
            duplicateCount = o.int("DuplicateCount"),
            destination = o.str("Destination"),
        )
    }

    fun action(text: String): LmActionResult {
        val o = obj(text)
        return LmActionResult(o.bool("Success"), o.str("Message"), o.str("State"))
    }

    fun search(text: String): LmSearchResult {
        val o = obj(text)
        return LmSearchResult(o.bool("Success"), o.str("Message"), o.objs("Candidates").map(::candidate))
    }

    /** A score as a whole percent, for display (0.9234 is "92%"). */
    fun percent(score: Double): String = "${Math.round(score.coerceIn(0.0, 1.0) * 100)}%"
}

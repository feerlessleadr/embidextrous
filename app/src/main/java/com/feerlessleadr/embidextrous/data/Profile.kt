package com.feerlessleadr.embidextrous.data

/**
 * How a phone uses the app. Chosen once on that phone (and changeable in Settings), not taken from the Emby account: two people
 * can share one account and still want different things. It decides which tabs and Home cards show; it does not lock anything.
 */
enum class AppProfile(val label: String, val summary: String) {
    Parent("Parent", "Just the kids' watch time: who is watching, time left, extra time, time-outs and Bedtime."),
    Administrator("Administrator", "Everything: watch-time limits, smart lists, backups and the server's state."),
    Custom("Custom", "Pick the tabs yourself."),
    ;

    companion object {
        fun fromName(name: String?): AppProfile? = entries.firstOrNull { it.name == name }
    }
}

/** What a profile shows. Pure, so it is unit tested. */
object Profiles {
    /**
     * The Smart Lists tab needs an Emby administrator account (the SmartLists plugin demands one) and a profile that wants it.
     * [customSmartLists] only matters for [AppProfile.Custom].
     */
    fun showsSmartLists(profile: AppProfile?, isAdministrator: Boolean, customSmartLists: Boolean): Boolean {
        if (!isAdministrator) return false
        return when (profile) {
            AppProfile.Parent -> false
            AppProfile.Custom -> customSmartLists
            AppProfile.Administrator, null -> true
        }
    }

    /** The Home cards about the server (smart lists, server, plugins) need an administrator account and a profile that wants them: the same rule as the Smart Lists tab. */
    fun showsAdminCards(profile: AppProfile?, isAdministrator: Boolean, customSmartLists: Boolean): Boolean =
        showsSmartLists(profile, isAdministrator, customSmartLists)
}

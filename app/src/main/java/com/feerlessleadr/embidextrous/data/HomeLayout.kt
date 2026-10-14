package com.feerlessleadr.embidextrous.data

/** A card on the Home screen that the person can hide or move. The status card at the top is always there and is not listed. */
enum class HomeCard(val label: String, val summary: String, val administratorOnly: Boolean) {
    WatchingNow("Watching now", "Who is playing, with +30 min and Stop stream.", false),
    Kids("Kids today", "Each child's time left and this week's total.", false),
    Bedtime("Bedtime", "Time everyone out for an hour or until morning.", false),
    SmartLists("Smart lists", "How many lists, any that failed, and Refresh all.", true),
    Server("Server", "Emby's version, who is connected, failed tasks.", true),
    Plugins("Plugins", "Plugin versions and updates.", true),
    ;

    companion object {
        fun fromName(name: String): HomeCard? = entries.firstOrNull { it.name == name }

        /** The cards a phone can use: the administrator ones only with [administrator]. */
        fun availableFor(administrator: Boolean): Set<HomeCard> = entries.filter { administrator || !it.administratorOnly }.toSet()
    }
}

/** Which Home cards show, and in what order. Kept on the phone. Pure, so it is unit tested. */
data class HomeLayout(val order: List<HomeCard> = HomeCard.entries.toList(), val hidden: Set<HomeCard> = emptySet()) {
    /** The cards to draw: in the chosen order, not hidden, and only those this phone can use ([available]). */
    fun visible(available: Set<HomeCard>): List<HomeCard> = order.filter { it in available && it !in hidden }

    /** Every card the person can set up (shown or not), in order. */
    fun configurable(available: Set<HomeCard>): List<HomeCard> = order.filter { it in available }

    fun toggled(card: HomeCard): HomeLayout = copy(hidden = if (card in hidden) hidden - card else hidden + card)

    /** Moves [card] one place up (-1) or down (+1) among the cards in [available]. */
    fun moved(card: HomeCard, delta: Int, available: Set<HomeCard>): HomeLayout {
        val shown = configurable(available)
        val from = shown.indexOf(card)
        val to = from + delta
        if (from < 0 || to !in shown.indices) return this
        val swapWith = shown[to]
        val next = order.toMutableList()
        val i = next.indexOf(card)
        val j = next.indexOf(swapWith)
        next[i] = swapWith
        next[j] = card
        return copy(order = next)
    }

    fun encode(): String = order.joinToString(",") { it.name } + ";" + hidden.joinToString(",") { it.name }

    companion object {
        /** Reads what [encode] wrote. Unknown names are dropped and cards missing from the text are added at the end, so a newer app's new card appears. Anything unreadable gives the default. */
        fun decode(text: String?): HomeLayout {
            if (text.isNullOrBlank()) return HomeLayout()
            val parts = text.split(';')
            val order = parts[0].split(',').mapNotNull { HomeCard.fromName(it.trim()) }.distinct()
            val hidden = parts.getOrNull(1).orEmpty().split(',').mapNotNull { HomeCard.fromName(it.trim()) }.toSet()
            return HomeLayout(order + HomeCard.entries.filter { it !in order }, hidden)
        }
    }
}

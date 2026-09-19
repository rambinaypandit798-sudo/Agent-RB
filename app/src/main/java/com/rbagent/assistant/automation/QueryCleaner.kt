package com.rbagent.assistant.automation

/**
 * QueryCleaner
 * YouTube search ke liye filler phrases strip karta hai.
 *
 * Example:
 *   "open youtube and play hindi songs" -> "hindi songs"
 *   "hindi gaana bajao"                 -> "hindi gaana"
 *   "play kesariya on youtube"          -> "kesariya"
 */
object QueryCleaner {

    private val PREFIX = listOf(
        Regex("(?i)^open\\s+(?:the\\s+)?youtube\\s+(?:app\\s+)?(?:and\\s+)?"),
        Regex("(?i)^launch\\s+(?:the\\s+)?youtube\\s+(?:app\\s+)?(?:and\\s+)?"),
        Regex("(?i)^start\\s+(?:the\\s+)?youtube\\s+(?:app\\s+)?(?:and\\s+)?"),
        Regex("(?i)^youtube\\s+(?:app\\s+)?(?:and\\s+)?"),
        Regex("(?i)^(?:play|search|find|sunao|chalao|bajao|dikhao)\\s+"),
        Regex("(?i)^(?:song|music|video)\\s+play\\s+")
    )

    private val SUFFIX = listOf(
        Regex("(?i)\\s+(?:on|in|pe|par)\\s+youtube$"),
        Regex("(?i)\\s+youtube$"),
        Regex("(?i)\\s+(?:bajao|baja\\s*do|chalao|chala\\s*do|sunao|sun\\s*do|play\\s+karo|dikhao)$"),
        Regex("(?i)\\s+play\\s+karo$")
    )

    /**
     * Safely strips filler phrases. Never returns empty — falls back
     * to the original query if cleaning would erase everything.
     */
    fun cleanYouTube(raw: String): String {
        var s = raw.trim()
        var changed = true
        var guard = 0
        while (changed && guard < 6) {
            changed = false
            guard++
            for (p in PREFIX) {
                val n = p.replace(s, "").trim()
                if (n != s && n.isNotBlank()) { s = n; changed = true }
            }
            for (p in SUFFIX) {
                val n = p.replace(s, "").trim()
                if (n != s && n.isNotBlank()) { s = n; changed = true }
            }
        }
        return s.trim().ifBlank { raw.trim() }
    }
}

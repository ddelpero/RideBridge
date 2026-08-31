package com.ddelpero.ridebridge.voice

sealed class VoiceIntent {
    data object Play : VoiceIntent()
    data object Pause : VoiceIntent()
    data object Next : VoiceIntent()
    data object Previous : VoiceIntent()
    data class PlaySearch(val query: String) : VoiceIntent()
    data class Call(val nameOrNumber: String) : VoiceIntent()
    data class Navigate(val query: String) : VoiceIntent()
    data object Unknown : VoiceIntent()
}

object VoiceCommandParser {
    private val filler = Regex("\\b(please|hey ridebridge|ok|okay)\\b")
    private val punctuation = Regex("[.!?]+")
    private val spaces = Regex("\\s+")
    private val bareFuel = Regex("^(gas|petrol|fuel)( station)?$")
    private val ignoreTokens = setOf("weather", "time", "hello", "hi", "thanks", "thank")

    fun parse(utterance: String): VoiceIntent {
        val t = utterance.lowercase()
            .replace(punctuation, " ")
            .replace(filler, " ")
            .replace(spaces, " ")
            .trim()
        if (t.isEmpty()) return VoiceIntent.Unknown

        callTarget(t)?.let { return VoiceIntent.Call(it) }

        when {
            t.matches(Regex("^(pause|stop)( (the )?(music|song|track))?$")) ->
                return VoiceIntent.Pause
            t.matches(Regex("^(resume|unpause|continue)( (the )?(music|song))?$")) ->
                return VoiceIntent.Play
            t == "play" || t == "play music" || t == "play song" ->
                return VoiceIntent.Play
            t.matches(Regex("^(next|skip)( (song|track|one))?$")) || t == "skip this" ->
                return VoiceIntent.Next
            t.matches(Regex("^(previous|prev|last|back)( (song|track|one))?$")) ->
                return VoiceIntent.Previous
        }

        if (t.startsWith("play ")) {
            val query = t.removePrefix("play ").trim()
            return if (query.isEmpty() || query == "music") VoiceIntent.Play
            else VoiceIntent.PlaySearch(query)
        }

        navQuery(t)?.let { return VoiceIntent.Navigate(it) }

        titleByArtist(t)?.let { return VoiceIntent.PlaySearch(it) }

        val tokens = t.split(" ").filter { it.isNotBlank() }
        if (tokens.any { it in ignoreTokens }) return VoiceIntent.Unknown
        if (tokens.size >= 2 || looksLikeAddress(t)) {
            return VoiceIntent.Navigate(normalizePlace(t))
        }
        return VoiceIntent.Unknown
    }

    private fun looksLikeAddress(t: String): Boolean {
        return t.contains(Regex("\\d")) &&
            t.contains(Regex("\\b(st|street|rd|road|ave|avenue|blvd|dr|drive|ln|lane|hwy|highway)\\b"))
    }

    private fun callTarget(t: String): String? {
        val prefixes = listOf("call ", "phone ", "dial ", "ring ")
        for (prefix in prefixes) {
            if (t.startsWith(prefix)) {
                val rest = t.removePrefix(prefix).trim()
                return rest.ifEmpty { null }
            }
        }
        return null
    }

    private fun navQuery(t: String): String? {
        val prefixes = listOf(
            "give me directions to ",
            "get me directions to ",
            "i need directions to ",
            "how do i get to ",
            "get directions to ",
            "navigate to ",
            "navigate ",
            "directions to ",
            "directions ",
            "take me to ",
            "take me ",
            "route to ",
            "go to ",
            "where is ",
            "where's ",
            "find ",
            "to "
        )
        for (prefix in prefixes) {
            if (t.startsWith(prefix)) {
                val rest = normalizePlace(t.removePrefix(prefix).trim())
                if (rest.isEmpty()) return null
                if (rest.split(" ").all { it in ignoreTokens }) return null
                return rest
            }
        }
        val asPlace = normalizePlace(t)
        if (asPlace == "gas station") return asPlace
        return null
    }

    private fun titleByArtist(t: String): String? {
        if (looksLikeAddress(t)) return null
        val parts = t.split(Regex("\\s+by\\s+"), limit = 2)
        if (parts.size != 2) return null
        if (parts[0].isBlank() || parts[1].isBlank()) return null
        return t
    }

    private fun normalizePlace(query: String): String {
        var s = query
        val articles = listOf("the ", "a ", "an ", "nearest ", "nearby ", "closest ")
        var changed = true
        while (changed) {
            changed = false
            for (article in articles) {
                if (s.startsWith(article)) {
                    s = s.removePrefix(article).trim()
                    changed = true
                }
            }
        }
        if (s.isNotEmpty()) {
            s = s.replace(Regex("\\s+(near me|nearby|around here|around me)$"), "").trim()
        }
        if (bareFuel.matches(s)) return "gas station"
        return s
    }
}

package com.imkolganov.datagate.update

/**
 * Compares dotted version strings (e.g. tag [v1.0.5] vs app [1.0.4-dev]).
 */
object SemanticVersionCompare {

    fun isRemoteNewer(remoteTag: String, currentVersionName: String): Boolean {
        val remote = parse(remoteTag) ?: return false
        val current = parse(currentVersionName) ?: return true
        return compare(remote, current) > 0
    }

    private fun parse(raw: String): List<Int>? {
        val cleaned = raw.trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore('-')
            .ifBlank { return null }
        val parts = cleaned.split('.').mapNotNull { p -> p.toIntOrNull() }
        return parts.ifEmpty { null }
    }

    private fun compare(a: List<Int>, b: List<Int>): Int {
        val n = maxOf(a.size, b.size)
        for (i in 0 until n) {
            val av = a.getOrElse(i) { 0 }
            val bv = b.getOrElse(i) { 0 }
            if (av != bv) return av.compareTo(bv)
        }
        return 0
    }
}

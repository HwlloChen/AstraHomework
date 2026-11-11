package com.etaris.astrahomework

const val NEW_FILE_WINDOW_MILLIS = 24L * 60 * 60 * 1000

/** Discovery, not provider metadata, determines freshness. A backwards clock has zero elapsed time. */
fun isNewFile(firstDiscoveredAt: Long?, isUploaded: Boolean, now: Long): Boolean {
    if (isUploaded || firstDiscoveredAt == null || firstDiscoveredAt < 0) return false
    if (now < firstDiscoveredAt) return true
    return now - firstDiscoveredAt < NEW_FILE_WINDOW_MILLIS
}

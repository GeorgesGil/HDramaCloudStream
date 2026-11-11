package com.hdrama.network

/**
 * Cache en memoria con TTL y limite de entradas. No persiste en disco.
 */
class TtlCache<K, V>(
    private val ttlMillis: Long,
    private val maxEntries: Int = 64,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private data class Entry<V>(val value: V, val expiresAt: Long)

    private val entries = object : LinkedHashMap<K, Entry<V>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, Entry<V>>?): Boolean {
            return size > maxEntries
        }
    }

    @Synchronized
    fun get(key: K): V? {
        val entry = entries[key] ?: return null
        if (entry.expiresAt <= clock()) {
            entries.remove(key)
            return null
        }
        return entry.value
    }

    @Synchronized
    fun put(key: K, value: V) {
        entries[key] = Entry(value, clock() + ttlMillis)
    }

    @Synchronized
    fun clear() {
        entries.clear()
    }
}

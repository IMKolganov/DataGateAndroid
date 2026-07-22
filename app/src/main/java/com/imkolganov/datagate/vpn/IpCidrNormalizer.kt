package com.imkolganov.datagate.vpn

import java.net.Inet6Address
import java.net.InetAddress

/**
 * Prefix-trie normalization for bypass CIDR lists.
 *
 * Stages (IPv4 and IPv6 separately):
 * 1. exact-duplicate removal
 * 2. nested-prefix removal (narrower fully covered by broader → drop narrower)
 * 3. recursive sibling merge (`a/n` + `a^(1<<(32-n-1))/n` → parent `/(n-1)`), repeated to fixpoint
 *
 * Coverage is preserved: every address covered by the input remains covered by the output,
 * and the output does not cover any address outside the input.
 */
object IpCidrNormalizer {

    data class FamilyStats(
        val original: Int,
        val distinct: Int,
        val afterNestedRemoval: Int,
        val afterSiblingMerge: Int,
    )

    data class Result(
        val routes: List<IpCidrRoute>,
        val ipv4: FamilyStats,
        val ipv6: FamilyStats,
    ) {
        val originalCount: Int get() = ipv4.original + ipv6.original
        val distinctCount: Int get() = ipv4.distinct + ipv6.distinct
        val afterNestedRemovalCount: Int get() = ipv4.afterNestedRemoval + ipv6.afterNestedRemoval
        val afterSiblingMergeCount: Int get() = ipv4.afterSiblingMerge + ipv6.afterSiblingMerge
    }

    fun normalize(routes: List<IpCidrRoute>): Result {
        val v4In = routes.filterIsInstance<Ipv4CidrRoute>()
        val v6In = routes.filterIsInstance<Ipv6CidrRoute>()
        val (v4Out, v4Stats) = normalizeIpv4(v4In)
        val (v6Out, v6Stats) = normalizeIpv6(v6In)
        // Deterministic order: IPv4 then IPv6, each by ascending prefix length then address.
        val ordered = (v4Out + v6Out).sortedWith(
            compareBy<IpCidrRoute> { it is Ipv6CidrRoute }
                .thenBy { it.prefixLength }
                .thenBy { it.networkAddress }
        )
        return Result(routes = ordered, ipv4 = v4Stats, ipv6 = v6Stats)
    }

    private fun normalizeIpv4(routes: List<Ipv4CidrRoute>): Pair<List<Ipv4CidrRoute>, FamilyStats> {
        val original = routes.size
        val distinctMap = LinkedHashMap<String, Ipv4CidrRoute>()
        for (r in routes) {
            distinctMap.putIfAbsent(r.toCidrString(), r)
        }
        val distinct = distinctMap.values.toList()
        val trie = BitTrie(bitLength = 32)
        for (r in distinct) {
            val network = ipv4ToLong(r.networkAddress) ?: continue
            val aligned = network and prefixToMask(r.prefixLength)
            trie.insert(aligned, r.prefixLength)
        }
        val afterNested = trie.exportPrefixes().size
        trie.compress()
        val merged = trie.exportPrefixes().map { (addr, len) ->
            Ipv4CidrRoute(
                networkAddress = longToIpv4(addr),
                netmask = longToIpv4(prefixToMask(len)),
                prefixLength = len,
            )
        }
        return merged to FamilyStats(
            original = original,
            distinct = distinct.size,
            afterNestedRemoval = afterNested,
            afterSiblingMerge = merged.size,
        )
    }

    private fun normalizeIpv6(routes: List<Ipv6CidrRoute>): Pair<List<Ipv6CidrRoute>, FamilyStats> {
        val original = routes.size
        val distinctMap = LinkedHashMap<String, Ipv6CidrRoute>()
        for (r in routes) {
            distinctMap.putIfAbsent(r.toCidrString(), r)
        }
        val distinct = distinctMap.values.toList()
        val trie = BitTrie(bitLength = 128)
        for (r in distinct) {
            val bits = ipv6ToBits(r.networkAddress) ?: continue
            applyIpv6PrefixBits(bits, r.prefixLength)
            trie.insertBits(bits, r.prefixLength)
        }
        val afterNested = trie.exportPrefixesBits().size
        trie.compress()
        val merged = trie.exportPrefixesBits().mapNotNull { (bits, len) ->
            val host = bitsToIpv6Host(bits) ?: return@mapNotNull null
            Ipv6CidrRoute(networkAddress = host, prefixLength = len)
        }
        return merged to FamilyStats(
            original = original,
            distinct = distinct.size,
            afterNestedRemoval = afterNested,
            afterSiblingMerge = merged.size,
        )
    }

    /** Binary prefix trie for one address family. */
    private class BitTrie(private val bitLength: Int) {
        private class Node {
            var terminal: Boolean = false
            var left: Node? = null
            var right: Node? = null
        }

        private val root = Node()

        fun insert(address: Long, prefixLength: Int) {
            require(bitLength == 32)
            insertBits(LongArray(1) { address }, prefixLength)
        }

        fun insertBits(addressBits: LongArray, prefixLength: Int) {
            require(prefixLength in 0..bitLength)
            var node = root
            for (i in 0 until prefixLength) {
                if (node.terminal) {
                    // Broader prefix already covers this range.
                    return
                }
                val bit = bitAt(addressBits, i)
                node = if (bit == 0) {
                    node.left ?: Node().also { node.left = it }
                } else {
                    node.right ?: Node().also { node.right = it }
                }
            }
            node.terminal = true
            // Nested narrower prefixes under this node are redundant.
            node.left = null
            node.right = null
        }

        fun compress() {
            compressNode(root)
        }

        private fun compressNode(node: Node?): Boolean {
            if (node == null) return false
            if (node.terminal) {
                node.left = null
                node.right = null
                return true
            }
            val leftFull = compressNode(node.left)
            val rightFull = compressNode(node.right)
            if (leftFull && rightFull) {
                node.terminal = true
                node.left = null
                node.right = null
                return true
            }
            return false
        }

        fun exportPrefixes(): List<Pair<Long, Int>> {
            require(bitLength == 32)
            return exportPrefixesBits().map { (bits, len) -> bits[0] to len }
        }

        fun exportPrefixesBits(): List<Pair<LongArray, Int>> {
            val out = ArrayList<Pair<LongArray, Int>>()
            fun dfs(node: Node, depth: Int, bits: LongArray) {
                if (node.terminal) {
                    out += bits.copyOf() to depth
                    return
                }
                node.left?.let { child ->
                    dfs(child, depth + 1, bits)
                }
                node.right?.let { child ->
                    val next = bits.copyOf()
                    setBit(next, depth)
                    dfs(child, depth + 1, next)
                }
            }
            dfs(root, 0, LongArray((bitLength + 63) / 64))
            return out
        }

        private fun bitAt(bits: LongArray, index: Int): Int {
            val word = index / 64
            val offset = 63 - (index % 64) // MSB-first within each 64-bit word for network order
            // For IPv4 we store in bits[0] with MSB = bit 0 of address (0x80000000).
            return if (bitLength == 32) {
                (((bits[0] ushr (31 - index)) and 1L).toInt())
            } else {
                (((bits[word] ushr offset) and 1L).toInt())
            }
        }

        private fun setBit(bits: LongArray, index: Int) {
            if (bitLength == 32) {
                bits[0] = bits[0] or (1L shl (31 - index))
            } else {
                val word = index / 64
                val offset = 63 - (index % 64)
                bits[word] = bits[word] or (1L shl offset)
            }
        }
    }

    private fun ipv4ToLong(value: String): Long? {
        val octets = value.split('.')
        if (octets.size != 4) return null
        var result = 0L
        for (octet in octets) {
            val n = octet.toIntOrNull() ?: return null
            if (n !in 0..255) return null
            result = (result shl 8) or n.toLong()
        }
        return result and 0xffffffffL
    }

    private fun prefixToMask(prefixLength: Int): Long {
        if (prefixLength == 0) return 0L
        return (0xffffffffL shl (32 - prefixLength)) and 0xffffffffL
    }

    private fun longToIpv4(value: Long): String =
        listOf(
            (value shr 24) and 0xff,
            (value shr 16) and 0xff,
            (value shr 8) and 0xff,
            value and 0xff
        ).joinToString(".")

    private fun ipv6ToBits(host: String): LongArray? {
        val addr = runCatching { InetAddress.getByName(host) }.getOrNull() as? Inet6Address
            ?: return null
        val bytes = addr.address
        require(bytes.size == 16)
        val bits = LongArray(2)
        for (i in 0 until 8) {
            bits[0] = (bits[0] shl 8) or (bytes[i].toLong() and 0xff)
        }
        for (i in 8 until 16) {
            bits[1] = (bits[1] shl 8) or (bytes[i].toLong() and 0xff)
        }
        return bits
    }

    private fun bitsToIpv6Host(bits: LongArray): String? {
        val bytes = ByteArray(16)
        var hi = bits[0]
        var lo = bits[1]
        for (i in 7 downTo 0) {
            bytes[i] = (hi and 0xff).toByte()
            hi = hi ushr 8
        }
        for (i in 15 downTo 8) {
            bytes[i] = (lo and 0xff).toByte()
            lo = lo ushr 8
        }
        return InetAddress.getByAddress(bytes).hostAddress
    }

    private fun applyIpv6PrefixBits(bits: LongArray, prefixLength: Int) {
        when {
            prefixLength <= 0 -> {
                bits[0] = 0L
                bits[1] = 0L
            }
            prefixLength >= 128 -> Unit
            prefixLength < 64 -> {
                bits[0] = bits[0] and (-1L shl (64 - prefixLength))
                bits[1] = 0L
            }
            prefixLength == 64 -> bits[1] = 0L
            else -> bits[1] = bits[1] and (-1L shl (128 - prefixLength))
        }
    }
}

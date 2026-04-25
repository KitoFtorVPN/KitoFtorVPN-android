package `fun`.kitoftorvpn.android.whitelist

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetAddress

/**
 * Движок split-туннелинга. Порт логики с Windows (main.js, функции
 * cleanWhitelistEntry, resolveWhitelistEntries, parseCidr, subtractCidrs,
 * applyWhitelistToConfig).
 *
 * Алгоритм:
 *   1. Каждую запись (домен, IP, CIDR) нормализуем: обрезаем http(s)://, путь, порт.
 *   2. Домены резолвим в IPv4 (А-записи), расширяем до /24 — как на Windows
 *      (помогает с CDN, где одна запись отдаёт пул IP).
 *   3. IP/CIDR добавляем как есть. CIDR — как есть, IP → /32.
 *   4. В тексте .conf находим AllowedIPs, содержащие 0.0.0.0/0,
 *      вычитаем из неё собранный набор и заменяем на список CIDR'ов.
 *   5. IP Endpoint'а из конфига никогда не вычитаем (иначе туннель не установится).
 */
object WhitelistEngine {

    private const val TAG = "WhitelistEngine"

    // ─── Cleanup ────────────────────────────────────────────

    private val urlPrefixes = listOf("https://", "http://", "ftp://")

    internal fun cleanEntry(raw: String): String? {
        var e = raw.trim()
        if (e.isEmpty() || e.startsWith("#")) return null
        for (p in urlPrefixes) {
            if (e.lowercase().startsWith(p)) { e = e.substring(p.length); break }
        }
        // Отсекаем путь, query, фрагмент.
        e = e.split("/")[0].split("?")[0].split("#")[0]
        // Отсекаем порт (не трогаем IPv6 в квадратных скобках).
        if (e.contains(":") && !e.startsWith("[")) {
            e = e.split(":")[0]
        }
        e = e.trim().trimEnd('.')
        return if (e.isEmpty()) null else e
    }

    private val ipOrCidrRe = Regex("""^[\d./]+$""")
    internal fun isIpOrCidr(s: String) = ipOrCidrRe.matches(s)

    // ─── DNS resolution ─────────────────────────────────────

    /**
     * Резолвит все записи из whitelist в множество CIDR'ов (строками).
     * Работает в IO-контексте, делает DNS-lookup для каждого домена.
     */
    suspend fun resolveEntries(entries: List<String>): Set<String> =
        withContext(Dispatchers.IO) {
            val out = LinkedHashSet<String>()
            for (raw in entries) {
                val e = cleanEntry(raw) ?: continue

                if (isIpOrCidr(e)) {
                    out.add(e)
                    continue
                }

                // Домен — резолвим IPv4.
                val ips = try {
                    InetAddress.getAllByName(e)
                        .filterIsInstance<Inet4Address>()
                        .map { it.hostAddress ?: "" }
                        .filter { it.isNotEmpty() }
                } catch (ex: Exception) {
                    Log.w(TAG, "resolve $e failed: ${ex.message}")
                    emptyList()
                }
                if (ips.isEmpty()) continue

                // Расширяем каждый IP до /24 — помогает с CDN.
                for (ip in ips) {
                    val parts = ip.split(".")
                    if (parts.size == 4) {
                        out.add("${parts[0]}.${parts[1]}.${parts[2]}.0/24")
                    }
                }
            }
            out
        }

    // ─── CIDR arithmetic ────────────────────────────────────
    // IPv4, ip как Long (беззнаковый в пределах 0..2^32-1).

    data class Cidr(val ip: Long, val bits: Int)

    internal fun parseCidr(s: String): Cidr? {
        val slash = s.indexOf('/')
        val ipStr = if (slash >= 0) s.substring(0, slash) else s
        val bits = if (slash >= 0) s.substring(slash + 1).toIntOrNull() ?: return null else 32
        if (bits !in 0..32) return null
        val parts = ipStr.split(".")
        if (parts.size != 4) return null
        var ip = 0L
        for (p in parts) {
            val n = p.toIntOrNull() ?: return null
            if (n < 0 || n > 255) return null
            ip = (ip shl 8) or n.toLong()
        }
        val mask = if (bits == 0) 0L else ((1L shl 32) - 1) xor ((1L shl (32 - bits)) - 1)
        return Cidr(ip and mask, bits)
    }

    internal fun cidrToString(c: Cidr): String {
        val a = (c.ip shr 24) and 0xff
        val b = (c.ip shr 16) and 0xff
        val cc = (c.ip shr 8) and 0xff
        val d = c.ip and 0xff
        return "$a.$b.$cc.$d/${c.bits}"
    }

    private fun splitCidr(c: Cidr): Pair<Cidr, Cidr>? {
        if (c.bits >= 32) return null
        val childBits = c.bits + 1
        val halfSize = 1L shl (32 - childBits)
        return Cidr(c.ip, childBits) to Cidr(c.ip + halfSize, childBits)
    }

    private fun cidrContains(a: Cidr, b: Cidr): Boolean {
        if (a.bits > b.bits) return false
        val mask = if (a.bits == 0) 0L else ((1L shl 32) - 1) xor ((1L shl (32 - a.bits)) - 1)
        return (b.ip and mask) == a.ip
    }

    /**
     * Вычитает excluded из network. Возвращает список CIDR'ов, покрывающих network
     * минус все excluded. Эквивалент Python-ского ipaddress.address_exclude.
     */
    internal fun subtractCidrs(network: Cidr, excluded: List<Cidr>): List<Cidr> {
        var result = mutableListOf(network)
        for (exc in excluded) {
            val next = mutableListOf<Cidr>()
            for (n in result) {
                if (!cidrContains(n, exc) && !cidrContains(exc, n)) {
                    next.add(n); continue
                }
                if (cidrContains(exc, n)) continue   // exc полностью накрывает n → n исчезает
                // n содержит exc — режем пополам до совпадения.
                val queue = ArrayDeque<Cidr>()
                queue.addLast(n)
                while (queue.isNotEmpty()) {
                    val cur = queue.removeLast()
                    if (cur.ip == exc.ip && cur.bits == exc.bits) continue
                    if (!cidrContains(cur, exc)) { next.add(cur); continue }
                    val halves = splitCidr(cur) ?: run { next.add(cur); continue }
                    queue.addLast(halves.first)
                    queue.addLast(halves.second)
                }
            }
            result = next
        }
        return result.sortedBy { it.ip }
    }

    // ─── Applying to .conf ──────────────────────────────────

    private val endpointRe = Regex("""(?im)^\s*Endpoint\s*=\s*([^\s:]+)""")

    private fun extractEndpointIps(confText: String): List<String> =
        endpointRe.findAll(confText).mapNotNull { m ->
            val host = m.groupValues[1]
            if (host.matches(Regex("""^[\d.]+$"""))) host else null
        }.toList()

    /**
     * Применяет whitelist к тексту конфига. Если whitelist пустой или ничего не
     * резолвится — возвращает исходный текст без изменений.
     */
    suspend fun applyToConfig(confText: String, whitelistEntries: List<String>): String {
        if (whitelistEntries.isEmpty()) return confText

        val excludedSet = resolveEntries(whitelistEntries).toMutableSet()
        if (excludedSet.isEmpty()) return confText

        // Никогда не вычитаем IP самого сервера.
        val epIps = extractEndpointIps(confText).toSet()
        // В excludedSet могут быть /24 с сервером — удаляем только точный IP.
        for (ep in epIps) {
            excludedSet.remove(ep)
            excludedSet.remove("$ep/32")
        }

        val excluded = excludedSet.mapNotNull { s ->
            parseCidr(if (s.contains('/')) s else "$s/32")
        }
        if (excluded.isEmpty()) return confText

        val lines = confText.split(Regex("""\r?\n""")).toMutableList()
        var modified = false
        val allowedRe = Regex("""^\s*allowedips\s*=""", RegexOption.IGNORE_CASE)
        for (i in lines.indices) {
            val line = lines[i]
            if (!allowedRe.containsMatchIn(line.trim())) continue
            val value = line.substringAfter('=', "").trim()
            val nets = value.split(",").map { it.trim() }.filter { it.isNotEmpty() }

            val outNets = mutableListOf<String>()
            for (n in nets) {
                if (n == "0.0.0.0/0") {
                    val base = parseCidr("0.0.0.0/0") ?: continue
                    val sub = subtractCidrs(base, excluded)
                    outNets.addAll(sub.map(::cidrToString))
                    modified = true
                } else {
                    outNets.add(n)
                }
            }
            lines[i] = "AllowedIPs = " + outNets.joinToString(", ")
        }
        if (!modified) return confText
        return lines.joinToString("\n")
    }
}

package com.example.data.lan

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException

/**
 * الاكتشاف التلقائي لسيرفرات الشبكة المحلية عبر بثّ UDP.
 *
 * الفكرة (زي ما بيشتغل أي لعبة هوتسبوت):
 *  - **المضيف** ([LanAnnouncer]) يبثّ رسالة `HELLO` كل [ANNOUNCE_INTERVAL_MS]
 *    على العنوان `255.255.255.255` وعلى عنوان بثّ الشبكة الفرعية.
 *  - **الضيف** ([LanDiscoveryListener]) بيسمع على نفس البورت، ويحوّل كل `HELLO`
 *    إلى [DiscoveredHost] يظهر في قائمة الواجهة.
 *  - الضيف كمان يبعت `DISCOVER` مرة عشان يجبر المضيفين على الرد فوراً بدل
 *    استثناء دورة البثّ الكاملة.
 *
 * ده بيحقّق الطريقة الأولى (تلقائي). الطريقة التانية (كتابة الـIP يدوياً)
 * موجودة في [LanClient] كاحتياط — لأن بعض الراوترات/الأجهزة بتمنع البثّ.
 */

/**
 * مُعرِّف السيرفر: يبثّ وجوده على الشبكة المحلية.
 *
 * يُشغَّل من [LanHostServer] تلقائياً عند بدء السيرفر.
 */
class LanAnnouncer(
    private val hostName: String,
    private val port: Int,
    private val roomCodeProvider: () -> String?,
    private val gameTypeProvider: () -> String?,
    private val playerCountProvider: () -> Int,
    private val myIpProvider: () -> String?
) {

    @Volatile
    private var running = false
    private var thread: Thread? = null

    /** عدد البثّات الناجحة — مفيد للتشخيص في الواجهة. */
    @Volatile
    var announcementsSent: Long = 0
        private set

    fun start() {
        if (running) return
        running = true
        thread = Thread({ announceLoop() }, "lan-announcer").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running = false
        thread?.interrupt()
        thread = null
    }

    private fun announceLoop() {
        val socket = try {
            DatagramSocket().apply { broadcast = true }
        } catch (_: Exception) {
            running = false
            return
        }

        try {
            while (running) {
                val message = LanProtocol.hello(
                    hostIp = myIpProvider().orEmpty(),
                    port = port,
                    hostName = hostName,
                    roomCode = roomCodeProvider(),
                    gameType = gameTypeProvider(),
                    playerCount = playerCountProvider()
                )
                val payload = LanProtocol.encode(message).toByteArray(Charsets.UTF_8)

                // نبثّ على العنوان العام وعلى بثّ الشبكة الفرعية: بعض الأجهزة
                // بتستجيب لواحد بس منهم حسب إعدادات الراوتر/الهوتسبوت
                broadcastTargets().forEach { target ->
                    runCatching {
                        val address = InetAddress.getByName(target)
                        socket.send(
                            DatagramPacket(payload, payload.size, address, LanProtocol.DISCOVERY_PORT)
                        )
                        announcementsSent++
                    }
                }

                try {
                    Thread.sleep(ANNOUNCE_INTERVAL_MS)
                } catch (_: InterruptedException) {
                    break
                }
            }
        } finally {
            runCatching { socket.close() }
        }
    }

    /** عناوين البثّ المستهدفة: العام + بثّ الشبكة الفرعية إن أمكن استنتاجه. */
    private fun broadcastTargets(): List<String> {
        val targets = mutableListOf(LanProtocol.BROADCAST_ADDRESS)
        val localIp = myIpProvider() ?: LanNetworkUtils.localIpAddress()
        LanNetworkUtils.subnetBroadcastAddress(localIp)?.let { subnet ->
            if (subnet != LanProtocol.BROADCAST_ADDRESS) targets.add(subnet)
        }
        return targets
    }

    companion object {
        /** فترة البثّ (مللي ثانية). */
        const val ANNOUNCE_INTERVAL_MS = 2_000L
    }
}

/**
 * مستمع الاكتشاف على جانب الضيف.
 *
 * يستقبل رسائل `HELLO` ويستدعي [onHostDiscovered] لكل سيرفر جديد أو مُحدَّث.
 * القائمة الداخلية بتتخلص من السيرفرات اللي بطلت تبثّ (خرجت من المدى) بعد
 * [STALE_HOST_MS] عشان الواجهة ما تعرضش سيرفرات ميتة.
 */
class LanDiscoveryListener(
    private val onHostDiscovered: (DiscoveredHost) -> Unit,
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) {

    private val lock = Any()
    private val hosts = mutableMapOf<String, DiscoveredHost>()

    @Volatile
    private var running = false
    private var socket: DatagramSocket? = null
    private var thread: Thread? = null

    /** هل المستمع شغّال وبيردّ فعلياً؟ */
    val isListening: Boolean get() = running

    fun start() {
        if (running) return
        val datagramSocket = try {
            DatagramSocket(null).apply {
                reuseAddress = true
                broadcast = true
                bind(InetSocketAddress(LanProtocol.DISCOVERY_PORT))
                soTimeout = SOCKET_POLL_TIMEOUT_MS
            }
        } catch (_: Exception) {
            // البورت محجوز لتطبيق تاني — المستخدم يقدر يستخدم الإدخال اليدوي
            return
        }

        socket = datagramSocket
        running = true
        thread = Thread({ listenLoop(datagramSocket) }, "lan-discovery").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running = false
        thread?.interrupt()
        thread = null
        runCatching { socket?.close() }
        socket = null
        synchronized(lock) { hosts.clear() }
    }

    /**
     * يبثّ طلب اكتشاف مرة واحدة عشان المضيفين يردّوا فوراً.
     * يُستدعى عند فتح شاشة الهوتسبوت وعند الضغط على «بحث 🔄».
     */
    fun sendDiscoverRequest() {
        val payload = LanProtocol.encode(LanProtocol.discover()).toByteArray(Charsets.UTF_8)
        runCatching {
            DatagramSocket().use { sender ->
                sender.broadcast = true
                listOf(LanProtocol.BROADCAST_ADDRESS, LanNetworkUtils.subnetBroadcastAddress(null))
                    .filterNotNull()
                    .distinct()
                    .forEach { target ->
                        runCatching {
                            sender.send(
                                DatagramPacket(
                                    payload,
                                    payload.size,
                                    InetAddress.getByName(target),
                                    LanProtocol.DISCOVERY_PORT
                                )
                            )
                        }
                    }
            }
        }
    }

    /** السيرفرات المكتشفة حالياً بعد التخلص من المنتهية صلاحيتها. */
    fun discoveredHosts(): List<DiscoveredHost> {
        pruneStale()
        return synchronized(lock) { hosts.values.sortedBy { it.hostIp } }
    }

    private fun listenLoop(datagramSocket: DatagramSocket) {
        val buffer = ByteArray(MAX_DATAGRAM_BYTES)

        while (running) {
            val packet = DatagramPacket(buffer, buffer.size)
            try {
                datagramSocket.receive(packet)
            } catch (_: SocketTimeoutException) {
                // مهلة قصيرة عشان نقدر نتخلص من السيرفرات الميتة ونفحص العلم
                pruneStale()
                continue
            } catch (_: Exception) {
                // البورت اتقفل = إيقاف طبيعي
                break
            }

            val raw = String(packet.data, 0, packet.length, Charsets.UTF_8)
            val message = LanProtocol.decode(raw) ?: continue
            if (message.type != LanMessageType.HELLO) continue
            if (!message.isCompatibleVersion) continue

            // لو المضيف مابعتش عنوانه في الرسالة، نستخدم عنوان المصدر
            val ip = message.hostIp?.takeIf { it.isNotBlank() } ?: packet.address?.hostAddress ?: continue
            val host = DiscoveredHost(
                hostIp = ip,
                port = message.port ?: LanProtocol.DEFAULT_GAME_PORT,
                hostName = message.hostName.orEmpty(),
                roomCode = message.roomCode,
                gameType = message.gameType,
                playerCount = message.playerCount ?: 0,
                discoveredAt = timeProvider()
            )

            synchronized(lock) { hosts["$ip:${host.port}"] = host }
            onHostDiscovered(host)
        }
    }

    private fun pruneStale() {
        val now = timeProvider()
        synchronized(lock) {
            val stale = hosts.entries.filter { now - it.value.discoveredAt > STALE_HOST_MS }
            stale.forEach { hosts.remove(it.key) }
        }
    }

    companion object {
        /** السيرفر اللي مابثّش من الفترة دي يُعتبر خارج المدى. */
        const val STALE_HOST_MS = 9_000L

        /** مهلة الاستماع — تسمح بفحص علم الإيقاف والتخلص من المنتهي. */
        const val SOCKET_POLL_TIMEOUT_MS = 2_000

        /** أقصى حجم حزمة اكتشاف مقبولة. */
        const val MAX_DATAGRAM_BYTES = 4 * 1024
    }
}

/**
 * أدوات الشبكة المحلية.
 *
 * دوال نقية قدر الإمكان (Java قياسية فقط، مفيش Android) ⇒ قابلة للاختبار.
 */
object LanNetworkUtils {

    /**
     * عنوان IPv4 لهذا الجهاز على الشبكة المحلية.
     *
     * بيفضّل عناوين `192.168.*` (النمط المعتاد للهوتسبوت والراوتر المنزلي)،
     * وبيتجاهل الـloopback والـIPv6 والعناوين غير المحلية.
     */
    fun localIpAddress(): String? {
        val candidates = mutableListOf<String>()
        runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { networkInterface ->
                if (networkInterface.isLoopback || !networkInterface.isUp) return@forEach
                networkInterface.inetAddresses?.toList()?.forEach { address ->
                    if (address.isLoopbackAddress) return@forEach
                    val ip = address.hostAddress ?: return@forEach
                    if (!ip.contains('.')) return@forEach // IPv6
                    if (ip.startsWith("127.")) return@forEach
                    candidates.add(ip)
                }
            }
        }
        if (candidates.isEmpty()) return null

        // الأفضلية لشبكة الهوتسبوت/المنزل المعتادة
        return candidates.firstOrNull { it.startsWith("192.168.") }
            ?: candidates.firstOrNull { it.startsWith("10.") }
            ?: candidates.firstOrNull { it.startsWith("172.") }
            ?: candidates.first()
    }

    /**
     * عنوان بثّ الشبكة الفرعية (مثال: `192.168.43.5` ⇒ `192.168.43.255`).
     *
     * يعيد null لو العنوان مش صالح. بعض الأجهزة بتستجيب لبثّ الشبكة الفرعية
     * أكتر من `255.255.255.255`، فبنبعت للاتنين.
     */
    fun subnetBroadcastAddress(localIp: String?): String? {
        val ip = localIp ?: localIpAddress() ?: return null
        val parts = ip.split('.')
        if (parts.size != 4) return null
        val valid = parts.all { part ->
            val value = part.toIntOrNull()
            value != null && value in 0..255
        }
        if (!valid) return null
        return "${parts[0]}.${parts[1]}.${parts[2]}.255"
    }

    /**
     * هل العنوان ده من نفس شبكة الجهاز؟ (يُستخدم لعرض تلميح في الواجهة)
     *
     * مقارنة على أول 3 octets — تقريب مناسب لشبكات /24 المعتادة في الهوتسبوت.
     */
    fun isSameSubnet(localIp: String?, remoteIp: String?): Boolean {
        if (localIp.isNullOrBlank() || remoteIp.isNullOrBlank()) return false
        val localParts = localIp.split('.')
        val remoteParts = remoteIp.split('.')
        if (localParts.size != 4 || remoteParts.size != 4) return false
        return localParts[0] == remoteParts[0] &&
            localParts[1] == remoteParts[1] &&
            localParts[2] == remoteParts[2]
    }
}

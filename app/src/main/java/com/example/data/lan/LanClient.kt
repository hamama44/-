package com.example.data.lan

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * عميل الشبكة المحلية: يتصل بسيرفر مضيف (هوتسبوت أو واي فاي منزلي) عبر TCP.
 *
 * نموذج الاتصال: **سطر JSON لكل رسالة**. خيط قارئ دائم بيفصل بين نوعين:
 *  - `STATE` ⇒ تُخزَّن كأحدث حالة + تُمرَّر لـ[onStateReceived] (دفع من السيرفر،
 *    بيحصل مع كل أمر من أي لاعب ومع نبضة الـkeep-alive).
 *  - `ACK`/`ERROR` ⇒ تُسلَّم لطابور الردّ اللي [request] مستنيه.
 *
 * الفصل ده ضروري: السيرفر بيبعت حالات غير مطلوبة (لما لاعب تاني يلعب)،
 * ولو قرأنا الردّ على نفس الخيط هنتلخبط بين الردّ والدفع.
 */
class LanClient(
    private val connectTimeoutMs: Int = DEFAULT_CONNECT_TIMEOUT_MS,
    private val responseTimeoutMs: Int = DEFAULT_RESPONSE_TIMEOUT_MS
) {

    @Volatile
    var isConnected: Boolean = false
        private set

    /** عنوان السيرفر المتصل بيه حالياً (يظهر في الواجهة). */
    @Volatile
    var connectedHost: String? = null
        private set

    @Volatile
    var lastError: String? = null
        private set

    /** يُستدعى من خيط القراءة عند وصول أي حالة جديدة من السيرفر. */
    @Volatile
    var onStateReceived: ((LanMessage) -> Unit)? = null

    private var socket: Socket? = null
    private var reader: BufferedReader? = null
    private var writer: BufferedWriter? = null
    private var readerThread: Thread? = null

    private val writeLock = Any()
    private val responseQueue = LinkedBlockingQueue<LanMessage>(RESPONSE_QUEUE_CAPACITY)

    @Volatile
    private var latestState: LanMessage? = null

    /** أحدث حالة وصلت من السيرفر (قد تكون دُفعت بدون طلب). */
    fun latestState(): LanMessage? = latestState

    /**
     * الاتصال بالسيرفر. يعيد true عند النجاح.
     *
     * بيتحقق من صحة العنوان والبورت قبل المحاولة عشان يعيد رسالة عربية مفهومة
     * بدل استثناء `SocketTimeoutException` غامض.
     */
    fun connect(host: String, port: Int = LanProtocol.DEFAULT_GAME_PORT): Boolean {
        if (isConnected) disconnect()

        val addressError = LanProtocol.validateHostAddress(host)
        if (addressError != null) {
            lastError = addressError
            return false
        }
        val portError = LanProtocol.validatePort(port)
        if (portError != null) {
            lastError = portError
            return false
        }

        val ip = host.trim()
        return try {
            val newSocket = Socket()
            newSocket.tcpNoDelay = true
            newSocket.connect(InetSocketAddress(ip, port), connectTimeoutMs)

            socket = newSocket
            reader = BufferedReader(InputStreamReader(newSocket.getInputStream(), Charsets.UTF_8))
            writer = BufferedWriter(OutputStreamWriter(newSocket.getOutputStream(), Charsets.UTF_8))
            latestState = null
            responseQueue.clear()
            isConnected = true
            connectedHost = ip
            lastError = null

            readerThread = Thread({ readLoop() }, "lan-client-reader").apply {
                isDaemon = true
                start()
            }
            true
        } catch (error: Exception) {
            lastError = describeConnectionFailure(ip, port, error)
            teardown()
            false
        }
    }

    /** قطع الاتصال بشكل نظيف. */
    fun disconnect() {
        isConnected = false
        connectedHost = null
        readerThread?.interrupt()
        readerThread = null
        teardown()
        latestState = null
        responseQueue.clear()
    }

    /**
     * إرسال أمر وانتظار ردّه (`ACK` أو `ERROR`).
     *
     * يعيد null لو انتهى الانتظار بدون ردّ (السيرفر مش بيردّ / انقطع الاتصال) —
     * وده بيترجمه مصدر البيانات لفشل بدل رمي استثناء.
     */
    fun request(message: LanMessage): LanMessage? {
        if (!isConnected) {
            lastError = GameRoomRepositoryErrors.NOT_CONNECTED
            return null
        }

        // نفضّ الطابور قبل الإرسال عشان ردّ قديم ميتحسبش ردّ على الأمر ده
        responseQueue.clear()

        val sent = sendRaw(message)
        if (!sent) {
            lastError = GameRoomRepositoryErrors.CONNECTION_LOST
            isConnected = false
            return null
        }

        return try {
            responseQueue.poll(responseTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            null
        }
    }

    /**
     * طلب الحالة الحالية من السيرفر.
     *
     * الحالة بتوصل كدفع على خيط القراءة (مش كردّ على الطابور)، لذلك هنا
     * نرسل `QUERY` ثم ننتظر تغيّر [latestState].
     */
    fun queryState(): LanMessage? {
        if (!isConnected) return null
        val before = latestState
        sendRaw(LanMessage(type = LanMessageType.QUERY))

        val deadline = System.currentTimeMillis() + responseTimeoutMs
        while (System.currentTimeMillis() < deadline) {
            val current = latestState
            if (current != null && current !== before) return current
            try {
                Thread.sleep(STATE_POLL_STEP_MS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return null
            }
        }
        return latestState
    }

    private fun sendRaw(message: LanMessage): Boolean {
        val activeWriter = writer ?: return false
        return try {
            val payload = LanProtocol.encode(message)
            synchronized(writeLock) {
                activeWriter.write(payload)
                activeWriter.write(LanProtocol.DELIMITER)
                activeWriter.flush()
            }
            true
        } catch (_: Exception) {
            isConnected = false
            lastError = GameRoomRepositoryErrors.CONNECTION_LOST
            false
        }
    }

    private fun readLoop() {
        val activeReader = reader ?: return
        try {
            while (isConnected) {
                val line = activeReader.readLine() ?: break
                val message = LanProtocol.decode(line) ?: continue

                when (message.type) {
                    LanMessageType.STATE -> {
                        latestState = message
                        runCatching { onStateReceived?.invoke(message) }
                    }
                    LanMessageType.ACK, LanMessageType.ERROR -> {
                        // الطابور محدود السعة: لو مليان (ردّ قديم متروك) نتجاهل
                        runCatching { responseQueue.offer(message) }
                    }
                    LanMessageType.BYE -> {
                        lastError = "السيرفر قفل الغرفة 📴"
                        isConnected = false
                        break
                    }
                    LanMessageType.PING -> {
                        // نبضة: الحالة محفوظة لو وصلت قبلها، ومفيش إجراء مطلوب
                    }
                }
            }
        } catch (_: Exception) {
            lastError = GameRoomRepositoryErrors.CONNECTION_LOST
        } finally {
            isConnected = false
        }
    }

    private fun teardown() {
        runCatching { reader?.close() }
        runCatching { writer?.close() }
        runCatching { socket?.close() }
        reader = null
        writer = null
        socket = null
    }

    /** ترجمة استثناء الاتصال لرسالة عربية عملية (أهم سبب: مش على نفس الشبكة). */
    private fun describeConnectionFailure(ip: String, port: Int, error: Exception): String {
        val reason = error.javaClass.simpleName
        return when {
            error is java.net.SocketTimeoutException ->
                "مفيش ردّ من $ip:$port — تأكد إنك متصل بنفس الهوتسبوت 📶"
            error is java.net.ConnectException ->
                "تعذّر الاتصال بـ $ip:$port — السيرفر مش شغّال أو العنوان غلط 🔌"
            error is java.net.UnknownHostException ->
                "عنوان غير معروف: $ip 📶"
            else -> "تعذّر الاتصال بـ $ip ($reason) 🔌"
        }
    }

    companion object {
        const val DEFAULT_CONNECT_TIMEOUT_MS = 6_000
        const val DEFAULT_RESPONSE_TIMEOUT_MS = 6_000

        /** خطوة انتظار تغيّر الحالة داخل [queryState]. */
        const val STATE_POLL_STEP_MS = 50L

        /** سعة طابور الردود. */
        const val RESPONSE_QUEUE_CAPACITY = 4
    }
}

/** رسائل أخطاء الاتصال — معزولة عشان تُختبر وتُعاد استخدامها. */
object GameRoomRepositoryErrors {
    const val NOT_CONNECTED = "مش متصل بأي سيرفر شبكة محلية 📴"
    const val CONNECTION_LOST = "انقطع الاتصال بسيرفر الشبكة المحلية 📶"
}

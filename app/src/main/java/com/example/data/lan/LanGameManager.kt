package com.example.data.lan

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * مدير جلسة الشبكة المحلية / الهوتسبوت (نقطة الدخول الوحيدة للواجهة).
 *
 * بيجمع الأجزاء التلاتة في مكان واحد:
 *  - [LanHostServer] — الجهاز المضيف (اللي فاتح الهوتسبوت).
 *  - [LanClient] — الجهاز الضيف المتصل بمضيف.
 *  - [LanDiscoveryListener] — الاكتشاف التلقائي ببثّ UDP.
 *
 * وكمان بيوفّر [lanDataSource] اللي بيربط النقل ده بـ
 * [com.example.data.room.GameRoomRepository] عبر [MultiTransportRoomsDataSource]،
 * فالمستودع بيدعم السحابة والهوتسبوت بنفس الكود.
 *
 * **الطريقتان اللي المستخدم طلبهم موجودتان**:
 *  1. تلقائي: [startDiscovery] + قائمة [discoveredHosts] في الواجهة.
 *  2. يدوي (احتياطي): [connectToHost] بعنوان IP مكتوب — لأن بعض الراوترات
 *     والأجهزة بتمنع بثّ UDP، فلازم يكون فيه طريق بديل دايماً.
 */
object LanGameManager {

    /** وضع النقل الحالي: سحابة أم شبكة محلية. */
    enum class TransportMode(val arabicLabel: String) {
        SUPABASE("السحابة ☁️"),
        LAN("الشبكة المحلية 📶")
    }

    // ------------------------------------------------------------------
    // حالة تُعرض في الواجهة
    // ------------------------------------------------------------------

    private val _transportMode = MutableStateFlow(TransportMode.SUPABASE)
    val transportMode: StateFlow<TransportMode> = _transportMode.asStateFlow()

    private val _isHosting = MutableStateFlow(false)
    val isHosting: StateFlow<Boolean> = _isHosting.asStateFlow()

    /** عنوان المضيف اللي الأجهزة التانية تكتبه لو الاكتشاف التلقائي فشل. */
    private val _hostAddress = MutableStateFlow<String?>(null)
    val hostAddress: StateFlow<String?> = _hostAddress.asStateFlow()

    /** البورت اللي السيرفر شغّال عليه. */
    private val _hostPort = MutableStateFlow(LanProtocol.DEFAULT_GAME_PORT)
    val hostPort: StateFlow<Int> = _hostPort.asStateFlow()

    private val _discoveredHosts = MutableStateFlow<List<DiscoveredHost>>(emptyList())
    val discoveredHosts: StateFlow<List<DiscoveredHost>> = _discoveredHosts.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    /** عنوان السيرفر المتصل بيه كضيف. */
    private val _joinedHost = MutableStateFlow<String?>(null)
    val joinedHost: StateFlow<String?> = _joinedHost.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _lanMessage = MutableStateFlow<String?>(null)
    val lanMessage: StateFlow<String?> = _lanMessage.asStateFlow()

    /** عدد اللاعبين المتصلين بالسيرفر (يظهر للمضيف). */
    private val _connectedClientCount = MutableStateFlow(0)
    val connectedClientCount: StateFlow<Int> = _connectedClientCount.asStateFlow()

    // ------------------------------------------------------------------
    // الأجزاء الداخلية
    // ------------------------------------------------------------------

    private var hostServer: LanHostServer? = null
    private var client: LanClient? = null
    private var discoveryListener: LanDiscoveryListener? = null
    private var multicastLock: Any? = null
    private var appContext: Context? = null
    private var dataSource: LanRoomsDataSource? = null

    @Volatile
    private var initialized = false

    /** اسم اللاعب الحالي — يُحقن من الـViewModel عند التهيئة. */
    @Volatile
    var currentNameProvider: () -> String = { "لاعب" }

    @Volatile
    var currentAvatarProvider: () -> String = { "⚽" }

    /**
     * تهيئة المدير بسياق التطبيق (لقفل الـMulticast وعنوان الـIP المحلي).
     * آمنة للاستدعاء المتكرر.
     */
    fun initialize(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            appContext = context.applicationContext
            acquireMulticastLock()
            initialized = true
        }
    }

    /**
     * مصدر بيانات الغرف فوق الشبكة المحلية.
     *
     * كسول: يُنشأ مرة واحدة ويُعاد استخدامها، فالمستودع يحتفظ بمرجع ثابت.
     */
    fun lanDataSource(): LanRoomsDataSource =
        dataSource ?: synchronized(this) {
            dataSource ?: LanRoomsDataSource(
                hostServerProvider = { hostServer },
                clientProvider = { client }
            ).also { dataSource = it }
        }

    /**
     * هل النقل المحلي نشط؟ (مضيف شغّال أو ضيف متصل)
     *
     * ده اللي بيخلي [com.example.data.room.GameRoomRepository] يعتبر نفسه
     * "متصل" في وضع الهوتسبوت رغم غياب الإنترنت تماماً.
     */
    fun isLanActive(): Boolean =
        hostServer?.isRunning == true || client?.isConnected == true

    /** عنوان IP لهذا الجهاز على الشبكة المحلية. */
    fun myIpAddress(): String? = LanNetworkUtils.localIpAddress()

    // ------------------------------------------------------------------
    // الاستضافة (الجهاز اللي فاتح الهوتسبوت)
    // ------------------------------------------------------------------

    /**
     * تشغيل سيرفر اللعبة على الجهاز ده + بدء البثّ التلقائي.
     *
     * [port] = 0 يعني استخدم البورت الافتراضي. يعيد رسالة نجاح/فشل عربية.
     */
    suspend fun startHosting(port: Int = LanProtocol.DEFAULT_GAME_PORT): String =
        withContext(Dispatchers.IO) {
            _isBusy.value = true
            try {
                val targetPort = if (port <= 0) LanProtocol.DEFAULT_GAME_PORT else port
                val portError = LanProtocol.validatePort(targetPort)
                if (portError != null) {
                    setMessage(portError)
                    return@withContext portError
                }

                stopClient()

                val server = hostServer ?: LanHostServer(
                    myIpProvider = { LanNetworkUtils.localIpAddress() }
                ).also { hostServer = it }

                val started = server.start(currentNameProvider())
                if (!started) {
                    val error = server.lastError ?: "تعذّر تشغيل سيرفر الشبكة المحلية 🔌"
                    _isHosting.value = false
                    _hostAddress.value = null
                    setMessage(error)
                    return@withContext error
                }

                _isHosting.value = true
                _hostPort.value = targetPort
                _hostAddress.value = server.boundAddress ?: LanNetworkUtils.localIpAddress()
                _transportMode.value = TransportMode.LAN
                updateClientCount()

                val address = _hostAddress.value
                val message = if (address.isNullOrBlank()) {
                    "السيرفر شغّال 📶 لكن مش قادرين نحدد عنوان الـIP — تأكد إن الهوتسبوت مفتوح"
                } else {
                    "السيرفر شغّال على $address:$targetPort 📶\nشارك العنوان مع أصحابك أو خلّيهم يلاقوك تلقائياً"
                }
                setMessage(message)
                message
            } finally {
                _isBusy.value = false
            }
        }

    /** إيقاف السيرفر والرجوع لوضع السحابة. */
    fun stopHosting() {
        hostServer?.stop()
        _isHosting.value = false
        _hostAddress.value = null
        _connectedClientCount.value = 0
        if (!isLanActive()) _transportMode.value = TransportMode.SUPABASE
        setMessage("تم إيقاف سيرفر الشبكة المحلية 📴")
    }

    // ------------------------------------------------------------------
    // الانضمام (الضيف)
    // ------------------------------------------------------------------

    /**
     * الاتصال بسيرفر مضيف بعنوان IP مباشر (الطريقة اليدوية الاحتياطية).
     */
    suspend fun connectToHost(
        hostIp: String,
        port: Int = LanProtocol.DEFAULT_GAME_PORT
    ): String = withContext(Dispatchers.IO) {
        _isBusy.value = true
        try {
            val addressError = LanProtocol.validateHostAddress(hostIp)
            if (addressError != null) {
                setMessage(addressError)
                return@withContext addressError
            }
            val portError = LanProtocol.validatePort(port)
            if (portError != null) {
                setMessage(portError)
                return@withContext portError
            }

            stopHosting()

            val lanClient = client ?: LanClient().also { client = it }
            lanClient.onStateReceived = { state ->
                // الحالة بتوصلنا مدفوعة مع كل أمر من أي لاعب ⇒ تحديث لحظي.
                // نسجّل كود الغرفة بس؛ ما نمسحش الرسائل لأن السيرفر بيبعت
                // حالات باستمرار فكانت أي رسالة هتختفي قبل ما يقرأها اللاعب.
                state.roomCode?.let { code -> _joinedRoomCode = code }
            }

            val connected = lanClient.connect(hostIp.trim(), port)
            if (!connected) {
                val error = lanClient.lastError ?: "تعذّر الاتصال بالسيرفر 🔌"
                _joinedHost.value = null
                setMessage(error)
                return@withContext error
            }

            _joinedHost.value = hostIp.trim()
            _transportMode.value = TransportMode.LAN

            val message = "اتصلت بالسيرفر ${hostIp.trim()}:$port ✅\nاقبل الغرفة من قائمة الغرف وابدأ اللعب"
            setMessage(message)
            message
        } finally {
            _isBusy.value = false
        }
    }

    /** آخر كود غرفة وصل من السيرفر (يُستخدم للاقتراح في واجهة الانضمام). */
    @Volatile
    private var _joinedRoomCode: String? = null

    val joinedRoomCode: String? get() = _joinedRoomCode

    /** قطع الاتصال بالسيرفر. */
    fun disconnectFromHost() {
        stopClient()
        if (!isLanActive()) _transportMode.value = TransportMode.SUPABASE
        setMessage("انقطعت عن سيرفر الشبكة المحلية 📴")
    }

    private fun stopClient() {
        client?.disconnect()
        _joinedHost.value = null
        _joinedRoomCode = null
    }

    // ------------------------------------------------------------------
    // الاكتشاف التلقائي (UDP)
    // ------------------------------------------------------------------

    /** بدء البحث التلقائي عن سيرفرات على نفس الشبكة. */
    fun startDiscovery() {
        if (discoveryListener?.isListening == true) {
            discoveryListener?.sendDiscoverRequest()
            return
        }

        val listener = LanDiscoveryListener(
            onHostDiscovered = { host ->
                val current = _discoveredHosts.value
                val updated = current.filterNot {
                    it.hostIp == host.hostIp && it.port == host.port
                } + host
                _discoveredHosts.value = updated.sortedBy { it.hostIp }
            }
        )
        discoveryListener = listener
        listener.start()
        _isDiscovering.value = listener.isListening

        if (listener.isListening) {
            // نطلب ردّاً فورياً بدل استثناء دورة البثّ الكاملة
            listener.sendDiscoverRequest()
        } else {
            setMessage("البحث التلقائي مش متاح على الجهاز ده — اكتب عنوان الـIP يدوياً 🔌")
        }
    }

    /** إعادة البحث (يمسح القائمة القديمة ويبعت طلب اكتشاف جديد). */
    fun refreshDiscovery() {
        _discoveredHosts.value = emptyList()
        discoveryListener?.sendDiscoverRequest()
        if (discoveryListener?.isListening != true) startDiscovery()
    }

    /** إيقاف البحث التلقائي. */
    fun stopDiscovery() {
        discoveryListener?.stop()
        discoveryListener = null
        _isDiscovering.value = false
        _discoveredHosts.value = emptyList()
    }

    // ------------------------------------------------------------------
    // حالة عامة
    // ------------------------------------------------------------------

    /** تحديث عدد العملاء المتصلين (يُستدعى دورياً من الواجهة). */
    fun updateClientCount() {
        _connectedClientCount.value = hostServer?.connectedClientCount ?: 0
    }

    /** وصف عربي كامل للحالة الحالية — يُعرض في واجهة الهوتسبوت. */
    fun describeStatus(): String {
        val ip = myIpAddress()
        val network = if (ip.isNullOrBlank()) {
            "مش متصل بأي شبكة محلية — افتح الهوتسبوت أو الواي فاي 📴"
        } else {
            "عنوان جهازك على الشبكة: $ip 📶"
        }

        return when {
            _isHosting.value -> {
                val address = _hostAddress.value ?: "غير معروف"
                val portValue = _hostPort.value
                val clients = _connectedClientCount.value
                "$network\nالوضع: سيرفر شغّال على $address:$portValue 🎮\nالمتصلون: $clients"
            }
            _joinedHost.value != null -> {
                "$network\nالوضع: متصل بسيرفر ${_joinedHost.value} ✅"
            }
            else -> "$network\nالوضع: مفيش جلسة شبكة محلية"
        }
    }

    /** هل العنوان ده على نفس شبكة الجهاز؟ (تلميح للمستخدم قبل المحاولة) */
    fun isSameSubnetAsDevice(remoteIp: String?): Boolean =
        LanNetworkUtils.isSameSubnet(myIpAddress(), remoteIp)

    fun dismissMessage() {
        _lanMessage.value = null
    }

    private fun setMessage(message: String?) {
        _lanMessage.value = message
    }

    /** إيقاف كل شيء (يُستدعى عند تدمير الـViewModel). */
    fun shutdown() {
        stopDiscovery()
        stopClient()
        hostServer?.stop()
        _isHosting.value = false
        _hostAddress.value = null
        _connectedClientCount.value = 0
        _transportMode.value = TransportMode.SUPABASE
        releaseMulticastLock()
    }

    // ------------------------------------------------------------------
    // قفل الـMulticast
    // ------------------------------------------------------------------

    /**
     * بعض أجهزة أندرويد بتمنع استقبال البثّ/الـmulticast لما يكون فيه
     * توفير طاقة، فقفل الـMulticast بيخلّي الاكتشاف التلقائي يعمل بثبات.
     * بيحتاج صلاحية `CHANGE_WIFI_MULTICAST_STATE`.
     */
    private fun acquireMulticastLock() {
        if (multicastLock != null) return
        runCatching {
            val context = appContext ?: return
            val wifiManager =
                context.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return
            val lock = wifiManager.createMulticastLock(MULTICAST_LOCK_TAG)
            lock.setReferenceCounted(false)
            if (!lock.isHeld) lock.acquire()
            multicastLock = lock
        }
    }

    private fun releaseMulticastLock() {
        runCatching {
            val lock = multicastLock as? WifiManager.MulticastLock ?: return
            if (lock.isHeld) lock.release()
        }
        multicastLock = null
    }

    /** مسح الحالة للاختبارات. */
    fun resetForTesting() {
        shutdown()
        dataSource = null
        appContext = null
        initialized = false
        _lanMessage.value = null
        _discoveredHosts.value = emptyList()
    }

    private const val MULTICAST_LOCK_TAG = "stadium-lan-discovery"
}

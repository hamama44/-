package com.example.data.lan

import com.example.data.room.GameRoom
import com.example.data.room.RoomParticipant
import com.example.data.room.RoomStatus
import com.example.data.room.RoomsRemoteDataSource

/**
 * موجّه النقل: بيقرّر هل نداءات الغرف تروح للسحابة (Supabase) ولا
 * للشبكة المحلية (هوتسبوت).
 *
 * بينفّذ نفس عقد [RoomsRemoteDataSource] فبيتحط مكان أي مصدر بيانات تاني
 * بدون ما [com.example.data.room.GameRoomRepository] يحسّ بفرق — وده هو
 * السبب إن نفس منطق الكاش والتحقق وموازنة الفرق بيشتغل على الاتنين.
 *
 * الوضع الافتراضي [TransportMode.AUTO]: لو فيه جلسة شبكة محلية نشطة
 * (مضيف شغّال أو ضيف متصل) نستخدم الهوتسبوت، وإلا السحابة. ده بيخلّي
 * التبديل تلقائي وآمن: المستخدم ما يحتاجش يفتكر يغيّر إعداد.
 */
class MultiTransportRoomsDataSource(
    private val supabase: RoomsRemoteDataSource,
    private val lan: RoomsRemoteDataSource,
    private val lanAvailable: () -> Boolean = { false }
) : RoomsRemoteDataSource {

    enum class TransportMode(val arabicLabel: String) {
        /** سحابة دائماً. */
        SUPABASE("السحابة ☁️"),

        /** شبكة محلية دائماً. */
        LAN("الشبكة المحلية 📶"),

        /** شبكة محلية لو متاحة، وإلا السحابة. */
        AUTO("تلقائي ⚡")
    }

    @Volatile
    var mode: TransportMode = TransportMode.AUTO

    /** النقل اللي بيُستخدم فعلياً دلوقتي — يُعرض في الواجهة للتشخيص. */
    val activeMode: TransportMode
        get() = when (mode) {
            TransportMode.SUPABASE -> TransportMode.SUPABASE
            TransportMode.LAN -> TransportMode.LAN
            TransportMode.AUTO -> if (lanAvailable()) TransportMode.LAN else TransportMode.SUPABASE
        }

    /** وصف عربي للنقل النشط. */
    val arabicActiveTransport: String get() = activeMode.arabicLabel

    private fun route(): RoomsRemoteDataSource =
        if (activeMode == TransportMode.LAN) lan else supabase

    override suspend fun fetchRooms(limit: Int): List<GameRoom>? = route().fetchRooms(limit)

    override suspend fun fetchRoomByCode(roomCode: String): GameRoom? =
        route().fetchRoomByCode(roomCode)

    override suspend fun createRoom(room: GameRoom): GameRoom? = route().createRoom(room)

    override suspend fun fetchParticipants(roomCode: String): List<RoomParticipant>? =
        route().fetchParticipants(roomCode)

    override suspend fun addParticipant(participant: RoomParticipant): Boolean =
        route().addParticipant(participant)

    override suspend fun updateParticipant(participant: RoomParticipant): Boolean =
        route().updateParticipant(participant)

    override suspend fun updateRoomState(
        roomCode: String,
        status: RoomStatus,
        currentRound: Int
    ): Boolean = route().updateRoomState(roomCode, status, currentRound)
}

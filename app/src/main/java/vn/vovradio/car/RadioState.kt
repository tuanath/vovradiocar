package vn.vovradio.car

import java.util.concurrent.CopyOnWriteArraySet

/** Trạng thái phát chia sẻ giữa Service và màn hình (cùng tiến trình). */
object RadioState {
    data class Snapshot(
        val stationId: String? = null,
        val status: String = "Sẵn sàng · bấm ▶ để nghe",
        val isPlaying: Boolean = false,
        val playWhenReady: Boolean = false,
        val isError: Boolean = false
    )

    @Volatile var current = Snapshot()
        private set
    private val listeners = CopyOnWriteArraySet<(Snapshot) -> Unit>()

    fun set(s: Snapshot) { current = s; listeners.forEach { it(s) } }
    fun addListener(l: (Snapshot) -> Unit) = listeners.add(l)
    fun removeListener(l: (Snapshot) -> Unit) = listeners.remove(l)
}

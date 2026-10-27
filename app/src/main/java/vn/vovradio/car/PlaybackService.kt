package vn.vovradio.car

import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlin.math.min

/**
 * Service phát nhạc nền: giữ phát khi tắt màn hình / chuyển app,
 * hiện điều khiển trên thông báo, nhận nút Next/Prev/Play trên vô-lăng.
 * Tự thử link dự phòng và tự kết nối lại khi mất sóng (vào hầm, vùng yếu sóng...).
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private lateinit var player: ExoPlayer
    private val handler = Handler(Looper.getMainLooper())

    private var urlIndex = 0          // đang dùng link thứ mấy của kênh hiện tại
    private var round = 0             // số vòng thử lại liên tiếp
    private var retryStatus: String? = null
    private var currentId: String? = null
    private val loadedSig = HashMap<String, String>()
    private val retryRunnable = Runnable { loadUrl(0) }

    private val repoListener: (StationRepository.Change) -> Unit = {
        if (it == StationRepository.Change.STATIONS) syncPlaylist()
    }

    override fun onCreate() {
        super.onCreate()
        StationRepository.init(this)

        val http = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 10) VOVRadioCar/1.0")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(12_000)
            .setReadTimeoutMs(15_000)

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(this, http)))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                true // tự nhỏ tiếng khi bản đồ đọc chỉ đường
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        player.repeatMode = Player.REPEAT_MODE_ALL // Next ở kênh cuối -> quay về kênh đầu
        player.addListener(listener)

        val launch = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            this, 0, launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        session = MediaSession.Builder(this, player).setSessionActivity(pi).build()

        StationRepository.addListener(repoListener)
        fullRebuild(StationRepository.lastStationId)
        publish()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        StationRepository.removeListener(repoListener)
        session?.run {
            player.release()
            release()
        }
        session = null
        RadioState.set(RadioState.current.copy(isPlaying = false, playWhenReady = false,
            isError = false, status = "Đã dừng · bấm ▶ để nghe"))
        super.onDestroy()
    }

    // ---------------------------------------------------------------- playlist

    private fun sig(st: Station) = listOf(st.name, st.short, st.desc, st.urls.joinToString(",")).joinToString("|")

    private fun buildItem(st: Station, i: Int): MediaItem {
        val url = st.urls.getOrElse(i) { st.urls.firstOrNull() ?: "" }
        val b = MediaItem.Builder()
            .setMediaId(st.id)
            .setUri(url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(st.name)
                    .setDisplayTitle(st.name)
                    .setArtist(st.desc)
                    .build()
            )
        if (url.contains(".m3u8", ignoreCase = true)) b.setMimeType(MimeTypes.APPLICATION_M3U8)
        return b.build()
    }

    private fun fullRebuild(preferId: String?) {
        val list = StationRepository.stations
        handler.removeCallbacks(retryRunnable)
        if (list.isEmpty()) {
            player.clearMediaItems(); loadedSig.clear(); currentId = null
            return
        }
        val idx = list.indexOfFirst { it.id == preferId }.coerceAtLeast(0)
        val wasPlaying = player.playWhenReady
        currentId = list[idx].id
        urlIndex = 0; round = 0; retryStatus = null
        player.setMediaItems(list.map { buildItem(it, 0) }, idx, C.TIME_UNSET)
        loadedSig.clear(); list.forEach { loadedSig[it.id] = sig(it) }
        if (wasPlaying) player.prepare()
    }

    /** Đồng bộ khi người dùng sửa/thêm/xoá kênh, cố gắng không làm ngắt kênh đang nghe. */
    private fun syncPlaylist() {
        val list = StationRepository.stations
        val sameOrder = player.mediaItemCount == list.size &&
            list.indices.all { player.getMediaItemAt(it).mediaId == list[it].id }
        if (!sameOrder) {
            fullRebuild(player.currentMediaItem?.mediaId ?: currentId)
            publish(); return
        }
        val cur = player.currentMediaItemIndex
        list.forEachIndexed { i, st ->
            val s = sig(st)
            if (loadedSig[st.id] == s) return@forEachIndexed
            loadedSig[st.id] = s
            if (i == cur) {
                val loaded = player.currentMediaItem?.localConfiguration?.uri?.toString()
                if (player.isPlaying && loaded != null && loaded in st.urls) {
                    urlIndex = st.urls.indexOf(loaded) // đang nghe tốt -> giữ nguyên
                    return@forEachIndexed
                }
                handler.removeCallbacks(retryRunnable)
                urlIndex = 0; round = 0; retryStatus = null
                player.replaceMediaItem(i, buildItem(st, 0))
                if (player.playWhenReady && player.playbackState == Player.STATE_IDLE) player.prepare()
            } else {
                player.replaceMediaItem(i, buildItem(st, 0))
            }
        }
        publish()
    }

    private fun currentStation(): Station? = StationRepository.find(player.currentMediaItem?.mediaId)

    private fun loadUrl(i: Int) {
        val idx = player.currentMediaItemIndex
        val st = StationRepository.stations.getOrNull(idx) ?: return
        if (st.urls.isEmpty() || st.id != player.currentMediaItem?.mediaId) return
        urlIndex = i.coerceIn(0, st.urls.size - 1)
        player.replaceMediaItem(idx, buildItem(st, urlIndex))
        player.prepare()
    }

    // ---------------------------------------------------------------- events

    private val listener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                player.seekToDefaultPosition(); player.prepare(); return
            }
            if (!player.playWhenReady) { retryStatus = null; publish(); return }
            val st = currentStation() ?: return
            val n = st.urls.size
            if (urlIndex + 1 < n) {
                retryStatus = "Chưa kết nối được, đang thử link ${urlIndex + 2}/$n..."
                publish()
                loadUrl(urlIndex + 1)
            } else {
                round++
                val delaySec = min(30, 3 * round)
                retryStatus = "Mất kết nối, tự thử lại sau ${delaySec}s (lần $round)..."
                publish()
                handler.removeCallbacks(retryRunnable)
                handler.postDelayed(retryRunnable, delaySec * 1000L)
            }
        }

        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_READY) { round = 0; retryStatus = null }
            if (state == Player.STATE_ENDED && player.playWhenReady) {
                // luồng trực tiếp bị đóng -> nối lại
                player.seekToDefaultPosition(); player.prepare()
            }
            publish()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) = publish()

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady) { handler.removeCallbacks(retryRunnable); retryStatus = null }
            publish()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val id = mediaItem?.mediaId
            if (id != null && id != currentId) {
                currentId = id
                val st = StationRepository.find(id)
                val uri = mediaItem.localConfiguration?.uri?.toString()
                urlIndex = if (st != null && uri != null) st.urls.indexOf(uri).coerceAtLeast(0) else 0
                round = 0; retryStatus = null
                handler.removeCallbacks(retryRunnable)
                StationRepository.lastStationId = id
                // đổi kênh bằng nút vô-lăng khi đang lỗi -> tự kết nối lại
                handler.post {
                    if (player.playWhenReady && player.playbackState == Player.STATE_IDLE) player.prepare()
                }
            }
            publish()
        }
    }

    private fun publish() {
        val st = currentStation()
        val n = st?.urls?.size ?: 0
        val retrying = retryStatus != null && player.playWhenReady
        val status = when {
            st == null -> "Chọn một kênh để nghe"
            retrying -> retryStatus!!
            player.isPlaying -> if (n > 1) "Đang phát trực tiếp · link ${urlIndex + 1}/$n" else "Đang phát trực tiếp"
            player.playWhenReady -> "Đang kết nối..."
            else -> "Đã dừng · bấm ▶ để nghe"
        }
        RadioState.set(RadioState.Snapshot(st?.id, status, player.isPlaying, player.playWhenReady, retrying))
    }
}

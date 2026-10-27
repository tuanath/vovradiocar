package vn.vovradio.car

import android.content.ComponentName
import android.content.Context
import android.database.ContentObserver
import android.graphics.Color
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max

class MainActivity : AppCompatActivity() {

    companion object {
        private var autoplayDone = false
        val linkStatus = HashMap<String, Int>()
    }

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val controller: MediaController?
        get() = controllerFuture?.let { f ->
            if (f.isDone && !f.isCancelled) runCatching { f.get() }.getOrNull() else null
        }

    private lateinit var adapter: StationAdapter
    private lateinit var audio: AudioManager
    private var filter = "all"

    private lateinit var nowShort: TextView
    private lateinit var nowName: TextView
    private lateinit var nowStatus: TextView
    private lateinit var eq: EqualizerView
    private lateinit var btnPlay: ImageButton
    private lateinit var btnFav: ImageButton
    private lateinit var btnMenu: ImageButton
    private lateinit var volume: SeekBar
    private lateinit var volText: TextView
    private lateinit var chipRow: LinearLayout
    private lateinit var grid: RecyclerView
    private lateinit var emptyText: TextView

    private val stateListener: (RadioState.Snapshot) -> Unit = { runOnUiThread { render() } }
    private val repoListener: (StationRepository.Change) -> Unit = { buildChips(); refreshList(); render() }
    private val volumeObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = refreshVolume()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        StationRepository.init(this)
        audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        volumeControlStream = AudioManager.STREAM_MUSIC

        nowShort = findViewById(R.id.nowShort)
        nowName = findViewById(R.id.nowName)
        nowStatus = findViewById(R.id.nowStatus)
        eq = findViewById(R.id.eq)
        btnPlay = findViewById(R.id.btnPlay)
        btnFav = findViewById(R.id.btnFav)
        btnMenu = findViewById(R.id.btnMenu)
        volume = findViewById(R.id.volume)
        volText = findViewById(R.id.volText)
        chipRow = findViewById(R.id.chipRow)
        grid = findViewById(R.id.grid)
        emptyText = findViewById(R.id.emptyText)

        adapter = StationAdapter({ playStation(it) }, { showEditDialog(it) })
        val glm = GridLayoutManager(this, 3)
        grid.layoutManager = glm
        grid.adapter = adapter
        grid.addOnLayoutChangeListener { v, l, _, r, _, ol, _, or2, _ ->
            if (r - l != or2 - ol) {
                val span = max(2, ((r - l) / resources.displayMetrics.density / 150f).toInt())
                v.post { if (glm.spanCount != span) glm.spanCount = span }
            }
        }

        btnPlay.setOnClickListener { withController { togglePlay(it) } }
        findViewById<View>(R.id.btnPrev).setOnClickListener {
            withController { c -> c.seekToPreviousMediaItem(); ensurePlaying(c) }
        }
        findViewById<View>(R.id.btnNext).setOnClickListener {
            withController { c -> c.seekToNextMediaItem(); ensurePlaying(c) }
        }
        btnFav.setOnClickListener { currentStation()?.let { StationRepository.toggleFavorite(it.id) } }
        btnMenu.setOnClickListener { showMenu() }
        nowShort.setOnLongClickListener { currentStation()?.let { showEditDialog(it) }; true }

        volume.max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        volume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                if (fromUser) runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, p, 0) }
                volText.text = p.toString()
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        buildChips()
        refreshList()
    }

    override fun onStart() {
        super.onStart()
        RadioState.addListener(stateListener)
        StationRepository.addListener(repoListener)
        contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, volumeObserver)

        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val f = MediaController.Builder(this, token).buildAsync()
        controllerFuture = f
        f.addListener({ onControllerReady() }, ContextCompat.getMainExecutor(this))

        render()
        refreshVolume()
    }

    override fun onStop() {
        RadioState.removeListener(stateListener)
        StationRepository.removeListener(repoListener)
        contentResolver.unregisterContentObserver(volumeObserver)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        super.onStop()
    }

    // ------------------------------------------------------------ playback

    private fun onControllerReady() {
        val c = controller ?: return
        if (!autoplayDone && StationRepository.autoplay && !c.playWhenReady) startPlay(c)
        autoplayDone = true
    }

    private fun withController(action: (MediaController) -> Unit) {
        val c = controller
        if (c != null) { action(c); return }
        controllerFuture?.addListener({ controller?.let(action) }, ContextCompat.getMainExecutor(this))
    }

    private fun ensurePlaying(c: Player) {
        if (c.playbackState == Player.STATE_IDLE) c.prepare()
        c.play()
    }

    private fun startPlay(c: Player) {
        c.seekToDefaultPosition() // nhảy về thời điểm trực tiếp mới nhất
        ensurePlaying(c)
    }

    private fun togglePlay(c: Player) {
        if (c.playWhenReady) c.pause() else startPlay(c)
    }

    private fun playStation(st: Station) = withController { c ->
        val idx = (0 until c.mediaItemCount).firstOrNull { c.getMediaItemAt(it).mediaId == st.id } ?: return@withController
        if (idx == c.currentMediaItemIndex && c.playWhenReady && c.playbackState != Player.STATE_IDLE) return@withController
        c.seekToDefaultPosition(idx)
        ensurePlaying(c)
    }

    private fun currentStation(): Station? =
        StationRepository.find(RadioState.current.stationId)
            ?: StationRepository.find(StationRepository.lastStationId)
            ?: StationRepository.stations.firstOrNull()

    // ------------------------------------------------------------ UI

    private fun render() {
        val s = RadioState.current
        val st = currentStation()
        nowShort.text = st?.short ?: "VOV"
        nowName.text = st?.name ?: "VOV Radio"
        nowStatus.text = s.status
        nowStatus.setTextColor(
            if (s.isError) 0xFFFFB86B.toInt() else ContextCompat.getColor(this, R.color.text_secondary)
        )
        btnPlay.setImageResource(if (s.playWhenReady) R.drawable.ic_pause else R.drawable.ic_play)
        btnFav.setImageResource(
            if (StationRepository.isFavorite(st?.id)) R.drawable.ic_heart else R.drawable.ic_heart_outline
        )
        eq.active = s.isPlaying
        if (adapter.activeId != st?.id) {
            adapter.activeId = st?.id
            adapter.notifyDataSetChanged()
        }
    }

    private fun refreshVolume() {
        val v = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        volume.progress = v
        volText.text = v.toString()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun buildChips() {
        chipRow.removeAllViews()
        val chips = mutableListOf("all" to "Tất cả", "fav" to "Yêu thích")
        Categories.all.filter { (k, _) -> StationRepository.stations.any { it.category == k } }.forEach { chips += it }
        if (chips.none { it.first == filter }) filter = "all"
        chips.forEach { (key, label) ->
            chipRow.addView(TextView(this).apply {
                text = label
                textSize = 17f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(dp(24), 0, dp(24), 0)
                setBackgroundResource(if (key == filter) R.drawable.bg_chip_active else R.drawable.bg_chip)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dp(52)
                ).apply { marginEnd = dp(10) }
                setOnClickListener { filter = key; buildChips(); refreshList() }
            })
        }
    }

    private fun refreshList() {
        val all = StationRepository.stations
        adapter.items = when (filter) {
            "all" -> all
            "fav" -> all.filter { it.id in StationRepository.favorites }
            else -> all.filter { it.category == filter }
        }
        adapter.favorites = StationRepository.favorites.toSet()
        adapter.linkStatus = HashMap(linkStatus)
        adapter.notifyDataSetChanged()
        emptyText.visibility = if (adapter.items.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    private fun showMenu() {
        val pm = PopupMenu(this, btnMenu)
        pm.menu.add(0, 1, 0, "Kiểm tra tất cả link")
        pm.menu.add(0, 2, 1, "Thêm kênh mới")
        pm.menu.add(0, 3, 2, "Sửa kênh đang chọn")
        pm.menu.add(0, 4, 3, "Tự phát khi mở app").apply {
            isCheckable = true; isChecked = StationRepository.autoplay
        }
        pm.menu.add(0, 5, 4, "Khôi phục danh sách mặc định")
        pm.setOnMenuItemClickListener {
            when (it.itemId) {
                1 -> checkAllLinks()
                2 -> showEditDialog(null)
                3 -> currentStation()?.let { st -> showEditDialog(st) }
                4 -> {
                    StationRepository.autoplay = !StationRepository.autoplay
                    toast(if (StationRepository.autoplay) "Đã bật tự phát khi mở app" else "Đã tắt tự phát")
                }
                5 -> AlertDialog.Builder(this)
                    .setTitle("Khôi phục danh sách mặc định?")
                    .setMessage("Các kênh và link bạn đã sửa sẽ bị thay bằng danh sách gốc.")
                    .setPositiveButton("Khôi phục") { _, _ -> linkStatus.clear(); StationRepository.resetDefaults() }
                    .setNegativeButton("Hủy", null)
                    .show()
            }
            true
        }
        pm.show()
    }

    /** Thử từng link của mọi kênh; link sống được đưa lên đầu để lần sau phát nhanh hơn. */
    private fun checkAllLinks() {
        val stations = StationRepository.stations
        if (stations.isEmpty()) return
        stations.forEach { linkStatus[it.id] = 1 }
        refreshList()
        toast("Đang kiểm tra ${stations.size} kênh...")
        val exec = Executors.newFixedThreadPool(6)
        val remaining = AtomicInteger(stations.size)
        val okCount = AtomicInteger(0)
        stations.forEach { st ->
            exec.execute {
                val results = st.urls.map { it to LinkChecker.check(it) }
                val good = results.filter { it.second }.map { it.first }
                val bad = results.filterNot { it.second }.map { it.first }
                if (good.isNotEmpty()) okCount.incrementAndGet()
                runOnUiThread {
                    linkStatus[st.id] = if (good.isNotEmpty()) 2 else 3
                    if (good.isNotEmpty()) StationRepository.setUrls(st.id, good + bad) else refreshList()
                    if (remaining.decrementAndGet() == 0) {
                        refreshList()
                        exec.shutdown()
                        toast("Xong: ${okCount.get()}/${stations.size} kênh nghe được. Chấm đỏ = cần sửa link (nhấn giữ ô kênh).")
                    }
                }
            }
        }
    }

    private fun showEditDialog(existing: Station?) {
        val v = layoutInflater.inflate(R.layout.dialog_edit_station, null)
        val etName = v.findViewById<EditText>(R.id.etName)
        val etShort = v.findViewById<EditText>(R.id.etShort)
        val etDesc = v.findViewById<EditText>(R.id.etDesc)
        val etUrls = v.findViewById<EditText>(R.id.etUrls)
        val spCat = v.findViewById<Spinner>(R.id.spCat)
        spCat.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, Categories.all.map { it.second })

        existing?.let {
            etName.setText(it.name); etShort.setText(it.short); etDesc.setText(it.desc)
            etUrls.setText(it.urls.joinToString("\n"))
            spCat.setSelection(Categories.all.indexOfFirst { c -> c.first == it.category }.coerceAtLeast(0))
        }

        val b = AlertDialog.Builder(this)
            .setTitle(if (existing == null) "Thêm kênh" else "Sửa kênh")
            .setView(v)
            .setPositiveButton("Lưu") { _, _ ->
                val name = etName.text.toString().trim()
                val urls = etUrls.text.toString().lines().map { it.trim() }
                    .filter { it.startsWith("http://") || it.startsWith("https://") }
                if (name.isEmpty() || urls.isEmpty()) {
                    toast("Cần có tên kênh và ít nhất 1 link bắt đầu bằng http:// hoặc https://")
                    return@setPositiveButton
                }
                val st = Station(
                    id = existing?.id ?: "custom_${System.currentTimeMillis()}",
                    name = name,
                    short = etShort.text.toString().trim().ifEmpty { name.take(10) },
                    desc = etDesc.text.toString().trim(),
                    category = Categories.all[spCat.selectedItemPosition.coerceAtLeast(0)].first,
                    urls = urls
                )
                linkStatus.remove(st.id)
                StationRepository.upsert(st)
            }
            .setNegativeButton("Hủy", null)
        if (existing != null) {
            b.setNeutralButton("Xóa kênh") { _, _ ->
                AlertDialog.Builder(this)
                    .setTitle("Xóa ${existing.name}?")
                    .setPositiveButton("Xóa") { _, _ -> StationRepository.remove(existing.id) }
                    .setNegativeButton("Hủy", null)
                    .show()
            }
        }
        b.show()
    }
}

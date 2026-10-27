package vn.vovradio.car

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArraySet

object StationRepository {
    enum class Change { STATIONS, FAVORITES }

    private lateinit var prefs: SharedPreferences
    private val listeners = CopyOnWriteArraySet<(Change) -> Unit>()

    var stations: List<Station> = emptyList()
        private set
    val favorites = LinkedHashSet<String>()

    fun init(ctx: Context) {
        if (::prefs.isInitialized) return
        prefs = ctx.applicationContext.getSharedPreferences("vov_radio", Context.MODE_PRIVATE)
        stations = prefs.getString("stations", null)
            ?.let { runCatching { parse(it) }.getOrNull() }
            ?.takeIf { it.isNotEmpty() }
            ?: DefaultStations.list
        prefs.getString("favorites", "")!!.split(",").filter { it.isNotBlank() }.forEach { favorites.add(it) }
    }

    var lastStationId: String?
        get() = prefs.getString("last", null)
        set(v) { prefs.edit().putString("last", v).apply() }

    var autoplay: Boolean
        get() = prefs.getBoolean("autoplay", true)
        set(v) { prefs.edit().putBoolean("autoplay", v).apply() }

    fun find(id: String?): Station? = id?.let { i -> stations.firstOrNull { it.id == i } }
    fun isFavorite(id: String?) = id != null && id in favorites

    fun toggleFavorite(id: String) {
        if (!favorites.remove(id)) favorites.add(id)
        prefs.edit().putString("favorites", favorites.joinToString(",")).apply()
        notifyAll(Change.FAVORITES)
    }

    fun upsert(st: Station) {
        stations = if (stations.any { it.id == st.id }) stations.map { if (it.id == st.id) st else it }
                   else stations + st
        persist()
    }

    fun remove(id: String) {
        stations = stations.filter { it.id != id }
        favorites.remove(id)
        prefs.edit().putString("favorites", favorites.joinToString(",")).apply()
        persist()
    }

    fun setUrls(id: String, urls: List<String>) {
        val st = find(id) ?: return
        if (st.urls != urls) upsert(st.copy(urls = urls))
    }

    fun resetDefaults() {
        stations = DefaultStations.list
        prefs.edit().remove("stations").apply()
        notifyAll(Change.STATIONS)
    }

    fun addListener(l: (Change) -> Unit) = listeners.add(l)
    fun removeListener(l: (Change) -> Unit) = listeners.remove(l)

    private fun persist() {
        prefs.edit().putString("stations", toJson(stations)).apply()
        notifyAll(Change.STATIONS)
    }

    private fun notifyAll(c: Change) = listeners.forEach { it(c) }

    private fun toJson(list: List<Station>): String {
        val arr = JSONArray()
        list.forEach { s ->
            arr.put(JSONObject().apply {
                put("id", s.id); put("name", s.name); put("short", s.short)
                put("desc", s.desc); put("category", s.category)
                put("urls", JSONArray(s.urls))
            })
        }
        return arr.toString()
    }

    private fun parse(json: String): List<Station> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val u = o.getJSONArray("urls")
            Station(
                o.getString("id"), o.getString("name"), o.optString("short", o.getString("name")),
                o.optString("desc", ""), o.optString("category", "other"),
                (0 until u.length()).map { u.getString(it) }
            )
        }
    }
}

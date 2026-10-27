package vn.vovradio.car

import java.net.HttpURLConnection
import java.net.URL

/** Kiểm tra nhanh 1 link có phát được không (chạy trên luồng nền). */
object LinkChecker {
    private const val UA = "Mozilla/5.0 (Linux; Android 10) VOVRadioCar/1.0"

    fun check(url: String): Boolean {
        var current = url
        repeat(5) {
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(current).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                    instanceFollowRedirects = false
                    setRequestProperty("User-Agent", UA)
                }
                val code = conn.responseCode
                if (code in 300..399) {
                    val loc = conn.getHeaderField("Location") ?: return false
                    current = URL(URL(current), loc).toString()
                    return@repeat
                }
                if (code !in 200..299) return false
                val buf = ByteArray(2048)
                val n = conn.inputStream.use { it.read(buf) }
                if (n <= 0) return false
                return if (url.contains(".m3u8", true) || current.contains(".m3u8", true))
                    String(buf, 0, n).contains("#EXTM3U")
                else true
            } catch (e: Exception) {
                return false
            } finally {
                conn?.disconnect()
            }
        }
        return false
    }
}

package vn.vovradio.car

data class Station(
    val id: String,
    val name: String,       // tên đầy đủ
    val short: String,      // chữ to trên ô
    val desc: String,       // dòng mô tả
    val category: String,   // news / traffic / music / culture / other
    val urls: List<String>  // link chính + link dự phòng (thử lần lượt)
)

object Categories {
    val all = listOf(
        "news" to "Thời sự",
        "traffic" to "Giao thông",
        "music" to "Âm nhạc",
        "culture" to "Văn hóa · Đối ngoại",
        "other" to "Khác"
    )
    fun label(key: String) = all.firstOrNull { it.first == key }?.second ?: "Khác"
}

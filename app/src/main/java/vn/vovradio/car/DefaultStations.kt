package vn.vovradio.car

/**
 * Danh sách kênh VOV mặc định.
 * Mỗi kênh có nhiều link: app thử lần lượt, link hỏng thì tự chuyển link kế tiếp.
 * Link có thể bị VOV thay đổi -> sửa ngay trong app (nhấn giữ ô kênh) hoặc sửa file này rồi build lại.
 */
object DefaultStations {
    private const val S = "https://str.vov.gov.vn/vovlive/"
    private const val L = "https://5a6872aace0ce.streamlock.net/"

    val list = listOf(
        Station("vov1", "VOV1 - Thời sự", "VOV1", "Thời sự · FM 100 MHz", "news", listOf(
            S + "vov1vov5Vietnamese.sdp_aac/playlist.m3u8",
            L + "nghevov1/vov1.stream_aac/playlist.m3u8",
            S + "vov1.sdp_aac/playlist.m3u8"
        )),
        Station("vov2", "VOV2 - Văn hóa Đời sống", "VOV2", "Văn hóa · Đời sống · Khoa giáo", "culture", listOf(
            S + "vov2.sdp_aac/playlist.m3u8",
            L + "nghevov2/vov2.stream_aac/playlist.m3u8"
        )),
        Station("vov3", "VOV3 - Âm nhạc", "VOV3", "Âm nhạc · FM 102.7 MHz", "music", listOf(
            S + "vov3.sdp_aac/playlist.m3u8",
            L + "nghevov3/vov3.stream_aac/playlist.m3u8"
        )),
        Station("vov4", "VOV4 - Dân tộc", "VOV4", "Tiếng dân tộc", "culture", listOf(
            S + "vov4.sdp_aac/playlist.m3u8",
            L + "nghevov4/vov4.stream_aac/playlist.m3u8"
        )),
        Station("vov5", "VOV5 - Đối ngoại", "VOV5", "Đối ngoại · FM 105.5 MHz", "culture", listOf(
            S + "vov5.sdp_aac/playlist.m3u8",
            L + "nghevov5/vov5.stream_aac/playlist.m3u8"
        )),
        Station("vovgt_hn", "VOV Giao thông Hà Nội", "VOV GT", "Giao thông Hà Nội · FM 91", "traffic", listOf(
            S + "vovgthn.sdp_aac/playlist.m3u8",
            L + "nghevovgthn/vovgthn.stream_aac/playlist.m3u8"
        )),
        Station("vovgt_hcm", "VOV Giao thông TP.HCM", "VOV GT", "Giao thông TP.HCM · FM 91", "traffic", listOf(
            S + "vovgthcm.sdp_aac/playlist.m3u8",
            L + "nghevovgthcm/vovgthcm.stream_aac/playlist.m3u8"
        )),
        Station("vov_mekong", "VOV Mekong FM 90", "Mekong", "VOV Mekong · FM 90", "traffic", listOf(
            S + "vovmekong.sdp_aac/playlist.m3u8",
            L + "nghevovmekong/vovmekong.stream_aac/playlist.m3u8"
        )),
        Station("vov_english", "VOV English 24/7", "English", "VOV24/7 tiếng Anh", "culture", listOf(
            S + "vov247.sdp_aac/playlist.m3u8",
            L + "nghevov247/vov247.stream_aac/playlist.m3u8"
        ))
    )
}

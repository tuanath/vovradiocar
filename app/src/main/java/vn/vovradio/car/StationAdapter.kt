package vn.vovradio.car

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class StationAdapter(
    private val onClick: (Station) -> Unit,
    private val onLongClick: (Station) -> Unit
) : RecyclerView.Adapter<StationAdapter.VH>() {

    var items: List<Station> = emptyList()
    var activeId: String? = null
    var favorites: Set<String> = emptySet()
    var linkStatus: Map<String, Int> = emptyMap() // 1 đang kiểm tra, 2 OK, 3 hỏng

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tile: View = v
        val short: TextView = v.findViewById(R.id.tileShort)
        val desc: TextView = v.findViewById(R.id.tileDesc)
        val dot: View = v.findViewById(R.id.tileDot)
        val fav: ImageView = v.findViewById(R.id.tileFav)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_station, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val st = items[position]
        val active = st.id == activeId
        val ctx = h.tile.context
        h.short.text = st.short
        h.desc.text = st.desc
        h.tile.setBackgroundResource(if (active) R.drawable.bg_glass_active else R.drawable.bg_glass)
        h.short.setTextColor(if (active) Color.WHITE else ContextCompat.getColor(ctx, R.color.accent_text))
        h.fav.visibility = if (st.id in favorites) View.VISIBLE else View.GONE
        val c = when (linkStatus[st.id]) {
            1 -> 0xFFFFC857.toInt()
            2 -> 0xFF4CD964.toInt()
            3 -> 0xFFFF5A5A.toInt()
            else -> 0x55FFFFFF
        }
        (h.dot.background.mutate() as GradientDrawable).setColor(c)
        h.tile.setOnClickListener { onClick(st) }
        h.tile.setOnLongClickListener { onLongClick(st); true }
    }
}

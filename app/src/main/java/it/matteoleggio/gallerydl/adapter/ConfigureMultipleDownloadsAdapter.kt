package it.matteoleggio.gallerydl.adapter

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import it.matteoleggio.gallerydl.R
import it.matteoleggio.gallerydl.database.models.DownloadItem
import it.matteoleggio.gallerydl.database.viewmodel.DownloadViewModel
import it.matteoleggio.gallerydl.util.FileUtil
import com.google.android.material.button.MaterialButton
import com.squareup.picasso.Picasso
import java.util.*

class ConfigureMultipleDownloadsAdapter(onItemClickListener: OnItemClickListener, activity: Activity) : ListAdapter<DownloadItem?, ConfigureMultipleDownloadsAdapter.ViewHolder>(AsyncDifferConfig.Builder(DIFF_CALLBACK).build()) {
    private val onItemClickListener: OnItemClickListener
    private val activity: Activity
    private var fileUtil: FileUtil

    init {
        this.onItemClickListener = onItemClickListener
        this.activity = activity
        fileUtil = FileUtil()
    }

    class ViewHolder(itemView: View, onItemClickListener: OnItemClickListener?) : RecyclerView.ViewHolder(itemView) {
        val cardView: FrameLayout

        init {
            cardView = itemView.findViewById(R.id.download_card_view)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val cardView = LayoutInflater.from(parent.context)
            .inflate(R.layout.download_card, parent, false)
        return ViewHolder(cardView, onItemClickListener)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val card = holder.cardView
        // THUMBNAIL ----------------------------------
        val thumbnail = card.findViewById<ImageView>(R.id.downloads_image_view)
        val imageURL = item!!.thumb
        val uiHandler = Handler(Looper.getMainLooper())
        if (imageURL.isNotEmpty()) {
            uiHandler.post { Picasso.get().load(imageURL).into(thumbnail) }
        } else {
            uiHandler.post { Picasso.get().load(R.color.black).into(thumbnail) }
        }

        val duration = card.findViewById<TextView>(R.id.duration)
        duration.text = item.duration

        // TITLE  ----------------------------------
        val itemTitle = card.findViewById<TextView>(R.id.title)
        var title = item.title
        if (title.length > 100) {
            title = title.substring(0, 40) + "..."
        }
        itemTitle.text = title

        // Format Note ----------------------------------
        val formatNote = card.findViewById<TextView>(R.id.format_note)
        if (item.format.format_note.isNotEmpty()){
            formatNote.text = item.format.format_note.uppercase(Locale.getDefault())
            formatNote.visibility = View.VISIBLE
        }else{
            formatNote.visibility = View.GONE
        }

        val codec = card.findViewById<TextView>(R.id.codec)
        val codecText =
            if (item.format.encoding != "") {
                item.format.encoding.uppercase()
            }else if (item.format.vcodec != "none" && item.format.vcodec != ""){
                item.format.vcodec.uppercase()
            } else {
                item.format.acodec.uppercase()
            }
        if (codecText == "" || codecText == "none"){
            codec.visibility = View.GONE
        }else{
            codec.visibility = View.VISIBLE
            codec.text = codecText
        }

        val fileSize = card.findViewById<TextView>(R.id.file_size)
        val fileSizeReadable = fileUtil.convertFileSize(item.format.filesize)
        if (fileSizeReadable == "?") fileSize.visibility = View.GONE
        else {
            fileSize.text = fileSizeReadable
            fileSize.visibility = View.VISIBLE
        }

        // Type Icon Button
        val btn = card.findViewById<MaterialButton>(R.id.action_button)
        if (btn.hasOnClickListeners()) btn.setOnClickListener(null)

        btn.setOnClickListener {
            onItemClickListener.onButtonClick(item.url)
        }

        when(item.type) {
            DownloadViewModel.Type.image -> {
                btn.setIconResource(R.drawable.ic_video)
            }
            else -> {
                btn.setIconResource(R.drawable.ic_terminal)
            }
        }

        card.setOnClickListener {
            onItemClickListener.onCardClick(item.url)
        }
    }

    interface OnItemClickListener {
        fun onButtonClick(itemURL: String)
        fun onCardClick(itemURL: String)
    }

    companion object {
        private val DIFF_CALLBACK: DiffUtil.ItemCallback<DownloadItem> = object : DiffUtil.ItemCallback<DownloadItem>() {
            override fun areItemsTheSame(oldItem: DownloadItem, newItem: DownloadItem): Boolean {
                return oldItem.url == newItem.url
            }

            override fun areContentsTheSame(oldItem: DownloadItem, newItem: DownloadItem): Boolean {
                return oldItem.title == newItem.title && oldItem.author == newItem.author && oldItem.type == newItem.type && oldItem.format == newItem.format
            }
        }
    }
}
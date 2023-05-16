package it.matteoleggio.gallerydl.adapter

import android.app.Activity
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import it.matteoleggio.gallerydl.R
import it.matteoleggio.gallerydl.database.models.ResultItem
import it.matteoleggio.gallerydl.database.viewmodel.DownloadViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.squareup.picasso.Picasso

class HomeAdapter(onItemClickListener: OnItemClickListener, activity: Activity) : ListAdapter<ResultItem?, HomeAdapter.ViewHolder>(AsyncDifferConfig.Builder(DIFF_CALLBACK).build()) {
    private val checkedVideos: ArrayList<String>
    private val onItemClickListener: OnItemClickListener
    private val activity: Activity

    init {
        checkedVideos = ArrayList()
        this.onItemClickListener = onItemClickListener
        this.activity = activity
    }

    class ViewHolder(itemView: View, onItemClickListener: OnItemClickListener?) : RecyclerView.ViewHolder(itemView) {
        val cardView: MaterialCardView

        init {
            cardView = itemView.findViewById(R.id.result_card_view)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val cardView = LayoutInflater.from(parent.context)
                .inflate(R.layout.result_card, parent, false)
        return ViewHolder(cardView, onItemClickListener)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val video = getItem(position)
        val card = holder.cardView
        // THUMBNAIL ----------------------------------
        val thumbnail = card.findViewById<ImageView>(R.id.result_image_view)
        val imageURL = video!!.thumb
        if (imageURL.isNotEmpty()) {
            val uiHandler = Handler(Looper.getMainLooper())
            uiHandler.post { Picasso.get().load(imageURL).into(thumbnail) }
            thumbnail.setColorFilter(Color.argb(20, 0, 0, 0))
        } else {
            val uiHandler = Handler(Looper.getMainLooper())
            uiHandler.post { Picasso.get().load(R.color.black).into(thumbnail) }
            thumbnail.setColorFilter(Color.argb(20, 0, 0, 0))
        }

        // TITLE  ----------------------------------
        val videoTitle = card.findViewById<TextView>(R.id.result_title)
        var title = video.title
        if (title.length > 100) {
            title = title.substring(0, 40) + "..."
        }
        videoTitle.text = title

        // Bottom Info ----------------------------------
        val author = card.findViewById<TextView>(R.id.author)
        author.text = video.author
        val duration = card.findViewById<TextView>(R.id.duration)
        if (video.duration.isNotEmpty()) {
            duration.text = video.duration
        }

        // BUTTONS ----------------------------------
        val videoURL = video.url
        val buttonLayout = card.findViewById<LinearLayout>(R.id.download_button_layout)
        val musicBtn = buttonLayout.findViewById<MaterialButton>(R.id.download_music)
        musicBtn.tag = "$videoURL##audio"
        musicBtn.setTag(R.id.cancelDownload, "false")
        val videoBtn = buttonLayout.findViewById<MaterialButton>(R.id.download_video)
        videoBtn.tag = "$videoURL##video"
        videoBtn.setTag(R.id.cancelDownload, "false")
        videoBtn.setOnClickListener { onItemClickListener.onButtonClick(videoURL, DownloadViewModel.Type.image) }
        videoBtn.setOnLongClickListener{ onItemClickListener.onLongButtonClick(videoURL, DownloadViewModel.Type.image); true}


        // PROGRESS BAR ----------------------------------------------------
        val progressBar = card.findViewById<LinearProgressIndicator>(R.id.download_progress)
        progressBar.tag = "$videoURL##progress"
        progressBar.progress = 0
        progressBar.isIndeterminate = true
        progressBar.visibility = View.GONE

//        if (video.isDownloading()){
//            progressBar.setVisibility(View.VISIBLE);
//        }else {
//            progressBar.setProgress(0);
//            progressBar.setIndeterminate(true);
//            progressBar.setVisibility(View.GONE);
//        }
//
//        if (video.isDownloadingAudio()) {
//            musicBtn.setIcon(ContextCompat.getDrawable(activity, R.drawable.ic_cancel));
//            musicBtn.setTag(R.id.cancelDownload, "true");
//        }else{
//            if(video.isAudioDownloaded() == 1){
//                musicBtn.setIcon(ContextCompat.getDrawable(activity, R.drawable.ic_music_downloaded));
//            }else{
//                musicBtn.setIcon(ContextCompat.getDrawable(activity, R.drawable.ic_music));
//            }
//        }
//
//        if (video.isDownloadingVideo()){
//            videoBtn.setIcon(ContextCompat.getDrawable(activity, R.drawable.ic_cancel));
//            videoBtn.setTag(R.id.cancelDownload, "true");
//        }else{
//            if(video.isVideoDownloaded() == 1){
//                videoBtn.setIcon(ContextCompat.getDrawable(activity, R.drawable.ic_video_downloaded));
//            }else{
//                videoBtn.setIcon(ContextCompat.getDrawable(activity, R.drawable.ic_video));
//            }
//        }
        if (checkedVideos.contains(videoURL)) {
            card.isChecked = true
            card.strokeWidth = 5
        } else {
            card.isChecked = false
            card.strokeWidth = 0
        }
        card.tag = "$videoURL##card"
        card.setOnLongClickListener {
            checkCard(card, videoURL)
            true
        }
        card.setOnClickListener {
            if (checkedVideos.size > 0) {
                checkCard(card, videoURL)
            }
        }
    }

    private fun checkCard(card: MaterialCardView, videoURL: String) {
        if (card.isChecked) {
            card.strokeWidth = 0
            checkedVideos.remove(videoURL)
        } else {
            card.strokeWidth = 5
            checkedVideos.add(videoURL)
        }
        card.isChecked = !card.isChecked
        onItemClickListener.onCardClick(videoURL, card.isChecked)
    }

    interface OnItemClickListener {
        fun onButtonClick(videoURL: String, type: DownloadViewModel.Type?)
        fun onLongButtonClick(videoURL  : String, type: DownloadViewModel.Type?)
        fun onCardClick(videoURL: String, add: Boolean)
    }

    fun checkAll(items: List<ResultItem?>?){
        checkedVideos.clear()
        checkedVideos.addAll(items!!.map { it!!.url })
        notifyDataSetChanged()
    }

    fun invertSelected(items: List<ResultItem?>?){
        val invertedList = mutableListOf<String>()
        items?.forEach {
            if (!checkedVideos.contains(it!!.url)) invertedList.add(it.url)
        }
        checkedVideos.clear()
        checkedVideos.addAll(invertedList)
        notifyDataSetChanged()
    }

    fun clearCheckedItems(){
        checkedVideos.clear()
    }

    companion object {
        private val DIFF_CALLBACK: DiffUtil.ItemCallback<ResultItem> = object : DiffUtil.ItemCallback<ResultItem>() {
            override fun areItemsTheSame(oldItem: ResultItem, newItem: ResultItem): Boolean {
                return oldItem.id === newItem.id
            }

            override fun areContentsTheSame(oldItem: ResultItem, newItem: ResultItem): Boolean {
                return oldItem.url == newItem.url && oldItem.title == newItem.title && oldItem.author == newItem.author
            }
        }
    }
}
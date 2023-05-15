package it.matteoleggio.gallerydl.adapter

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import it.matteoleggio.gallerydl.R
import it.matteoleggio.gallerydl.database.models.DownloadItem
import it.matteoleggio.gallerydl.database.models.ResultItem
import com.squareup.picasso.Picasso
import java.util.*


class PlaylistAdapter(onItemClickListener: OnItemClickListener, activity: Activity) : ListAdapter<ResultItem?, PlaylistAdapter.ViewHolder>(AsyncDifferConfig.Builder(DIFF_CALLBACK).build()) {
    private val checkedItems: ArrayList<String>
    private val onItemClickListener: OnItemClickListener
    private val activity: Activity

    init {
        checkedItems = ArrayList()
        this.onItemClickListener = onItemClickListener
        this.activity = activity
    }

    class ViewHolder(itemView: View, onItemClickListener: OnItemClickListener?) : RecyclerView.ViewHolder(itemView) {
        val cardView: ConstraintLayout

        init {
            cardView = itemView.findViewById(R.id.playlist_card_constraintLayout)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val cardView = LayoutInflater.from(parent.context)
                .inflate(R.layout.playlist_item, parent, false)
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
        thumbnail.setColorFilter(Color.argb(95, 0, 0, 0))

        // TITLE  ----------------------------------
        val itemTitle = card.findViewById<TextView>(R.id.title)
        var title = item.title
        if (title.length > 100) {
            title = title.substring(0, 40) + "..."
        }
        itemTitle.text = title


        val duration = card.findViewById<TextView>(R.id.duration)
        duration.text = item.duration

        // CHECKBOX ----------------------------------
        val check = card.findViewById<CheckBox>(R.id.checkBox)
        check.isChecked = checkedItems.contains(item.url)
        check.setOnClickListener {
            checkCard(check.isChecked, item.url)
        }

        card.setOnClickListener {
            check.performClick()
        }
    }

    private fun checkCard(isChecked: Boolean, itemURL: String) {
        if (isChecked) {
            checkedItems.add(itemURL)
        } else {
            checkedItems.remove(itemURL)
        }
        onItemClickListener.onCardSelect(itemURL, isChecked, checkedItems)
    }

    interface OnItemClickListener {
        fun onCardSelect(itemURL: String, isChecked: Boolean, checkedItems: List<String>)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun clearCheckeditems() {
        for (i in 0 until itemCount){
            val item = getItem(i)
            if (checkedItems.find { it == item?.url } != null){
                checkedItems.remove(item?.url)
                notifyItemChanged(i)
            }
        }
        checkedItems.clear()
    }

    fun checkAll(){
        checkedItems.clear()
        for (i in 0 until itemCount){
            val item = getItem(i)
            checkedItems.add(item!!.url)
            notifyItemChanged(i)
        }
    }

    fun invertSelected(items: List<ResultItem?>?){
        val invertedList = mutableListOf<String>()
        items?.forEach {
            if (!checkedItems.contains(it!!.url)) invertedList.add(it.url)
        }
        checkedItems.clear()
        checkedItems.addAll(invertedList)
        notifyDataSetChanged()
    }

    fun checkRange(start: Int, end: Int){
        checkedItems.clear()
        if (start == end ){
            val item = getItem(start)
            checkedItems.add(item!!.url)
            notifyItemChanged(start)
        }else{
            for (i in start..end){
                val item = getItem(i)
                checkedItems.add(item!!.url)
                notifyItemChanged(i)
            }
        }

    }

    fun getCheckedItems() : List<String>{
        return checkedItems
    }

    companion object {
        private val DIFF_CALLBACK: DiffUtil.ItemCallback<ResultItem> = object : DiffUtil.ItemCallback<ResultItem>() {
            override fun areItemsTheSame(oldItem: ResultItem, newItem: ResultItem): Boolean {
                val ranged = arrayListOf(oldItem.id, newItem.id)
                return ranged[0] == ranged[1]
            }

            override fun areContentsTheSame(oldItem: ResultItem, newItem: ResultItem): Boolean {
                return oldItem.id == newItem.id
            }
        }
    }
}
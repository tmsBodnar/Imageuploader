package hu.pedicure.image_uploader

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Adapter
import com.squareup.picasso.Picasso
import hu.pedicure.image_uploader.ImageAdapter.*

class ImageAdapter(private val dataSet: MutableList<Image>): Adapter<ViewHolder>(){

    var picasso = Picasso.get()

    class ViewHolder(view: View): RecyclerView.ViewHolder(view){
        val domain: String = "http://pedicure.hu"
        val alt: TextView
        val title: TextView
        val pic: ImageView
        init {
            alt = view.findViewById(R.id.alt)
            title = view.findViewById(R.id.title)
            pic = view.findViewById(R.id.pic)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.image_item_view, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int {
        return dataSet.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
       holder.alt.text = dataSet[position].alt
        holder.title.text = dataSet[position].title
        Picasso.get().load(holder.domain + dataSet[position].source).placeholder(R.drawable.logo).into(holder.pic)
    }

}
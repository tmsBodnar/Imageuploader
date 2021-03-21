package hu.pedicure.image_uploader.utils.imageUtils

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Adapter
import com.squareup.picasso.Picasso
import hu.pedicure.image_uploader.R
import hu.pedicure.image_uploader.utils.imageUtils.ImageAdapter.ViewHolder
import hu.pedicure.image_uploader.model.Image
import hu.pedicure.image_uploader.utils.propertiesUtil.PropertiesInitializer

class ImageAdapter(private val dataSet: MutableList<Image>): Adapter<ViewHolder>(){

    private var picasso: Picasso = Picasso.get()
    var onItemClickDelete: ((Image) -> Unit)? = null
    var onItemClickEdit: ((Image) -> Unit)? = null

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view){
        private val propHelper = PropertiesInitializer(view.context)
        val domain: String = propHelper.domain
        val alt: TextView = view.findViewById(R.id.alt)
        val title: TextView = view.findViewById(R.id.title)
        val pic: ImageView = view.findViewById(R.id.pic)
        private val editButton: Button = view.findViewById(R.id.edit_btn)
        private val deleteButton : Button = view.findViewById(R.id.delete_btn)

        init {
            editButton.setOnClickListener { onItemClickEdit?.invoke(dataSet[adapterPosition])}
            deleteButton.setOnClickListener { onItemClickDelete?.invoke(dataSet[adapterPosition])}
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
        picasso.load(holder.domain + dataSet[position].source).placeholder(R.drawable.logo).into(holder.pic)
    }

}

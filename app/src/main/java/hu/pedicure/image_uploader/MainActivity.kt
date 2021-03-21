package hu.pedicure.image_uploader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.squareup.picasso.Picasso
import hu.pedicure.image_uploader.utils.ftpUtil.FTPUtil
import hu.pedicure.image_uploader.utils.imageUtils.ImageAdapter
import hu.pedicure.image_uploader.utils.imageUtils.JSONImageDetailsUtil
import hu.pedicure.image_uploader.model.Image
import hu.pedicure.image_uploader.model.Type
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File


class MainActivity : AppCompatActivity() {

    private lateinit var ftpUtil: FTPUtil

    private lateinit var recView: RecyclerView
    private lateinit var fab: FloatingActionButton
    private lateinit var etTitle: EditText
    private lateinit var etAlt : EditText
    private lateinit var dialogImg: ImageView
    private lateinit var imgName: EditText
    private lateinit var  loadButton: Button
    private lateinit var progressCircle: ProgressBar


    private lateinit var selectedPhotoUri: Uri

    private lateinit var imageList: MutableList<Image>
    private lateinit var  imageAdapter: ImageAdapter
    private lateinit var linearLayoutManager: LinearLayoutManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        progressCircle = findViewById(R.id.loading_spinner)
        progressCircle.visibility =View.GONE

        recView = findViewById(R.id.rec_view)
        fab = findViewById(R.id.fab)
        loadButton = findViewById(R.id.btn_load)
        linearLayoutManager = LinearLayoutManager(this)
        recView.layoutManager = linearLayoutManager
        imageList = mutableListOf(Image())
        imageAdapter = ImageAdapter(imageList)
        imageAdapter.onItemClickDelete = { image ->
            deleteImage(image)
        }
        imageAdapter.onItemClickEdit = { image ->
            editImage(image)
        }
        recView.adapter = imageAdapter
        recView.visibility = View.GONE
        fab.visibility = View.GONE
        loadButton.visibility = View.VISIBLE

        ftpUtil =FTPUtil(this)
    }


    fun start(view: View) {
        loadButton.visibility = View.GONE
        loadImages()
        recView.visibility = View.VISIBLE
        fab.visibility = View.VISIBLE
    }

    fun addNewImage(view: View) {
        val photoPickerIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        photoPickerIntent.type = "image/*"
        photoPickerIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivityForResult(photoPickerIntent, PHOTO_SELECT)
    }

    private fun editImage(image: Image) {
        createUpdateDialog(image, Type.UPDATE)
    }

    private fun deleteImage(image: Image) {
        val dialogBuilder = AlertDialog.Builder(this)
        dialogBuilder.setTitle(R.string.delete)
        dialogBuilder.setMessage(R.string.confirm)
        dialogBuilder.setPositiveButton(R.string.OK) { _, _ ->
            updateOrDeleteImage(Type.DELETE, image)
        }
        dialogBuilder.setNegativeButton(R.string.cancel) { _, _->

        }
        dialogBuilder.show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        when (requestCode) {
            PHOTO_SELECT -> {
                if (data != null) {
                    selectedPhotoUri = data.data!!
                    val image = Image()
                    createUpdateDialog(image, Type.NEW)
                }
            }
        }
    }

    private fun createUpdateDialog(image: Image, type: Type) {
        val dialogBuilder = AlertDialog.Builder(this)
        val dialog = layoutInflater.inflate(R.layout.custom_dialog, null)
        dialogBuilder.setView(dialog)
        imgName = dialog.findViewById(R.id.et_name)
        etTitle = dialog.findViewById(R.id.et_title)
        etAlt = dialog.findViewById(R.id.et_alt)
        dialogImg = dialog.findViewById(R.id.dialog_img)
        when (type ) {
            Type.UPDATE -> {
                Picasso.get().load("http://pedicure.hu" + image.source).into(dialogImg)
                imgName.setText(
                        image.source.replace(PRE_SOURCE_TEXT, ""),
                        TextView.BufferType.EDITABLE
                )
                if (image.title.isNotEmpty()) etTitle.setText(image.title, TextView.BufferType.EDITABLE)
                if (image.alt.isNotEmpty()) etAlt.setText(image.alt, TextView.BufferType.EDITABLE)
                imgName.focusable = View.NOT_FOCUSABLE
            }
            Type.NEW -> {
                Picasso.get().load(selectedPhotoUri).into(dialogImg)
                imgName.focusable = View.FOCUSABLE
            }
            else -> {
                throw Exception("No type!")
            }
        }
        dialogBuilder.setPositiveButton(R.string.OK) { _, _ ->
            updateOrDeleteImage(type, image)
            }
        dialogBuilder.setNegativeButton(R.string.cancel) { _, _->

        }
        dialogBuilder.show()
    }

    private fun updateOrDeleteImage(type: Type, image: Image) {
        imageList.remove(image)
        val done: Boolean
        when (type){
            Type.UPDATE, Type.NEW -> {

                image.alt = etAlt.text.toString()
                image.title = etTitle.text.toString()
                image.seq = imageList.size + 1
                val mime = MimeTypeMap.getSingleton()
                if (type == Type.NEW) {
                    val ext = mime.getExtensionFromMimeType(contentResolver.getType(selectedPhotoUri))
                    val lastItemSource = imageList[imageList.size - 1]
                    val count = if (lastItemSource.seq > 0) imageList.size else lastItemSource.seq + 1
                    image.source = PRE_SOURCE_TEXT + count + "_" + imgName.text.toString() + "." + ext
                }
                imageList.add(image)
                val updatedJson = Json.encodeToString(imageList)
                val localFile = File(filesDir.path + "/images.json")
                localFile.writeText(updatedJson)
                done = ftpUtil.saveOrUpdateFtp(type, image, selectedPhotoUri)
                if (done) {
                    loadImages()
                } else {
                    Toast.makeText(this, "FTP hiba", Toast.LENGTH_SHORT).show()
                }
            }
            Type.DELETE -> {
                val updatedJson = Json.encodeToString(imageList)
                val localFile = File(filesDir.path + "/images.json")
                localFile.writeText(updatedJson)
                done = ftpUtil.deleteFromFtp(image)
                if (done) {
                    loadImages()
                } else {
                    Toast.makeText(this, "FTP hiba", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }



    private fun loadImages() {
        val imageDetailsListHelper = JSONImageDetailsUtil()
        imageList.clear()
        imageList.addAll(imageDetailsListHelper.loadImages(ftpUtil.getJSONFile()))
        imageList.reverse()
        imageAdapter.notifyDataSetChanged()
    }

    companion object {
        const val PHOTO_SELECT = 1234
        const val PRE_SOURCE_TEXT = "/assets/images/gallery/"
    }

}


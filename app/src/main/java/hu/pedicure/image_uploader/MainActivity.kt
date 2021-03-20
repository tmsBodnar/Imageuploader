package hu.pedicure.image_uploader

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.scale
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.squareup.picasso.Picasso
import hu.pedicure.image_uploader.imageDetailsListHelper.ImageDetailsListHelper
import hu.pedicure.image_uploader.ftpHelper.FTPHelper
import hu.pedicure.image_uploader.imageAdapter.ImageAdapter
import hu.pedicure.image_uploader.model.Image
import hu.pedicure.image_uploader.model.Type
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.*


class MainActivity : AppCompatActivity() {

    private lateinit var ftpHelper: FTPHelper

    private lateinit var recView: RecyclerView
    private lateinit var pBar: ProgressBar
    private lateinit var fab: FloatingActionButton
    private lateinit var etTitle: EditText
    private lateinit var etAlt : EditText
    private lateinit var dialogImg: ImageView
    private lateinit var imgName: EditText
    private lateinit var  loadButton: Button

    private lateinit var selectedPhotoUri: Uri

    private lateinit var imageList: MutableList<Image>
    private lateinit var  imageAdapter: ImageAdapter
    private lateinit var linearLayoutManager: LinearLayoutManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recView = findViewById(R.id.rec_view)
        pBar = findViewById(R.id.progress_circular)
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

        ftpHelper =FTPHelper(this)
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
        photoPickerIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(photoPickerIntent, Companion.PHOTO_SELECT)
    }

    private fun editImage(image: Image) {
        createUpdateDialog(image, Type.UPDATE)
    }

    private fun deleteImage(image: Image) {
        var dialogBuilder = AlertDialog.Builder(this)
        dialogBuilder.setTitle(R.string.delete)
        dialogBuilder.setMessage(R.string.confirm)
        dialogBuilder.setPositiveButton(R.string.OK) { dialog, which ->
            updateOrDeleteImage(Type.DELETE, image)
        }
        dialogBuilder.setNegativeButton(R.string.cancel) { dialog, which->

        }
        dialogBuilder.show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        when (requestCode) {
            Companion.PHOTO_SELECT -> {
                if (data != null) {
                    selectedPhotoUri = data.data!!
                    var imageStream: InputStream? = null
                    try {
                        imageStream = contentResolver.openInputStream(
                                selectedPhotoUri)
                    } catch (e: FileNotFoundException) {
                        e.printStackTrace()
                    }

                    val bmp = BitmapFactory.decodeStream(imageStream)
                    var stream: ByteArrayOutputStream? = ByteArrayOutputStream()
                    bmp.scale(1184, 666)
                    val byteArray = stream!!.toByteArray()
                    try {
                        stream!!.close()
                        stream = null
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                    var image = Image()
                    createUpdateDialog(image, Type.NEW)
                }
            }
        }
    }

    private fun createUpdateDialog(image: Image, type: Type) {
        var dialogBuilder = AlertDialog.Builder(this)
        var dialog = layoutInflater.inflate(R.layout.custom_dialog, null)
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
        }
        dialogBuilder.setPositiveButton(R.string.OK) { dialog, which ->
            updateOrDeleteImage(type, image)
        }
        dialogBuilder.setNegativeButton(R.string.cancel) { dialog, which->

        }
        dialogBuilder.show()
    }

    private fun updateOrDeleteImage(type: Type, image: Image) {
        imageList.remove(image)
        var done = false
        when (type){
            Type.UPDATE, Type.NEW -> {

                image.alt = etAlt.text.toString()
                image.title = etTitle.text.toString()
                image.seq = imageList.size + 1
                var mime = MimeTypeMap.getSingleton()
                if (type == Type.NEW) {
                    var ext = mime.getExtensionFromMimeType(contentResolver.getType(selectedPhotoUri))
                    var lastItemSource = imageList.get(imageList.size - 1)
                    var count = lastItemSource.seq + 1
                    image.source = PRE_SOURCE_TEXT + count + "_" + imgName.text.toString() + "." + ext
                }
                imageList.add(image)
                val updatedJson = Json.encodeToString(imageList)
                val localFile: File = File(filesDir.path + "/images.json")
                localFile.writeText(updatedJson)
                val job = GlobalScope.launch {
                    done = withContext(Dispatchers.Default) { ftpHelper.saveOrUpdateFtp(type, image, selectedPhotoUri) }
                }
                runBlocking {
                    job.join()
                }
                if (done) {
                    loadImages()
                } else {
                    Toast.makeText(this, "FTP hiba", Toast.LENGTH_SHORT)
                }
            }
            Type.DELETE -> {

                val updatedJson = Json.encodeToString(imageList)
                val localFile: File = File(filesDir.path + "/images.json")
                localFile.writeText(updatedJson)
                val job = GlobalScope.launch {
                    done = withContext(Dispatchers.Default) { ftpHelper.deleteFromFtp(image) }
                }
                runBlocking {
                    job.join()
                }
                if (done) {
                    loadImages()
                } else {
                    Toast.makeText(this, "FTP hiba", Toast.LENGTH_SHORT)
                }
            }
        }
    }



    private fun loadImages() {
        val imageDetailsListHelper = ImageDetailsListHelper()
        imageList.clear()
        imageList.addAll(imageDetailsListHelper.loadImages(ftpHelper.getJSONFile()))
        imageList.reverse()
        imageAdapter.notifyDataSetChanged()
    }

    companion object {
        const val PHOTO_SELECT = 1234
        const val PRE_SOURCE_TEXT = "/assets/images/gallery/"
    }

}


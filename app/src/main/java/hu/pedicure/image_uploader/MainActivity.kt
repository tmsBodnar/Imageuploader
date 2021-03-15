package hu.pedicure.image_uploader

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.scale
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.*
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.squareup.picasso.Picasso
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply
import java.io.*
import java.nio.charset.Charset
import java.util.*


class MainActivity : AppCompatActivity() {

    private lateinit var server: String
    private lateinit var user: String
    private lateinit var pass: String
    private lateinit var folder : String

    private lateinit var recView: RecyclerView
    private lateinit var pBar: ProgressBar
    private lateinit var fab: FloatingActionButton
    private lateinit var etTitle: EditText
    private lateinit var etAlt : EditText
    private lateinit var dialogImg: ImageView
    private lateinit var imgName: EditText
    private lateinit var  loadButton: Button

    private lateinit var imageList: MutableList<Image>
    private lateinit var  imageAdapter: ImageAdapter
    private lateinit var linearLayoutManager: LinearLayoutManager
    private lateinit var mapper: JsonMapper
    private var photoSelect = 1
    private lateinit var resizedUri: Uri

    private val isUpdate = "update"
    private val isDelete = "delete"
    private val isNew = "new"
    private val preSourceText = "/assets/images/gallery/"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recView = findViewById(R.id.rec_view)
        pBar = findViewById(R.id.progress_circular)
        fab = findViewById(R.id.fab)
        loadButton = findViewById(R.id.btn_load)

        mapper = JsonMapper()
        mapper.registerKotlinModule()

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
        Log.d("xxx", "init")
        initProperties()
    }


    fun start(view: View) {
        loadButton.visibility = View.GONE
        loadImages()
        recView.visibility = View.VISIBLE
        fab.visibility = View.VISIBLE
    }

    fun addNewImage(view: View) {
        val photoPickerIntent = Intent(Intent.ACTION_PICK)
        photoPickerIntent.type = "image/*"
        startActivityForResult(photoPickerIntent, photoSelect)
    }

    private fun editImage(image: Image) {
        createUpdateDialog(image, isUpdate)
    }

    private fun deleteImage(image: Image) {
        var dialogBuilder = AlertDialog.Builder(this)
        dialogBuilder.setTitle(R.string.delete)
        dialogBuilder.setMessage(R.string.confirm)
        dialogBuilder.setPositiveButton(R.string.OK) { dialog, which ->
            updateOrDeleteImage(isDelete, image)
        }
        dialogBuilder.setNegativeButton(R.string.cancel) { dialog, which->

        }
        dialogBuilder.show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            photoSelect -> {
                if (data != null) {
                    val selectedPhotoUri = data.data!!
                    var imageStream: InputStream? = null
                    try {
                        imageStream = contentResolver.openInputStream(
                                selectedPhotoUri)
                    } catch (e: FileNotFoundException) {
                        e.printStackTrace()
                    }

                    val bmp = BitmapFactory.decodeStream(imageStream)
                    bmp.scale(1184, 666)
                    var stream: ByteArrayOutputStream? = ByteArrayOutputStream()
                    bmp.compress(Bitmap.CompressFormat.JPEG, 100, stream)
                    resizedUri = getBmpUri(bmp)
                    var image = Image()
                    createUpdateDialog(image, isNew)
                }
            }
        }
    }

    private fun getBmpUri(bmp: Bitmap): Uri{
        val bytes = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 100, bytes)
        val path: String = MediaStore.Images.Media.insertImage(contentResolver, bmp, "Title", null)
        return Uri.parse(path)
    }

    private fun createUpdateDialog(image: Image, type: String) {
        var dialogBuilder = AlertDialog.Builder(this)
        var dialog = layoutInflater.inflate(R.layout.custom_dialog, null)
        dialogBuilder.setView(dialog)
        imgName = dialog.findViewById(R.id.et_name)
        etTitle = dialog.findViewById(R.id.et_title)
        etAlt = dialog.findViewById(R.id.et_alt)
        dialogImg = dialog.findViewById(R.id.dialog_img)
        when (type ) {
            isUpdate -> {
                Picasso.get().load("http://pedicure.hu" + image.source).into(dialogImg)
                imgName.setText(
                        image.source.replace(preSourceText, ""),
                        TextView.BufferType.EDITABLE
                )
                if (image.title.isNotEmpty()) etTitle.setText(image.title, TextView.BufferType.EDITABLE)
                if (image.alt.isNotEmpty()) etAlt.setText(image.alt, TextView.BufferType.EDITABLE)
                imgName.focusable = View.NOT_FOCUSABLE
            }
            isNew -> {
                Picasso.get().load(resizedUri).into(dialogImg)
                imgName.focusable = View.FOCUSABLE
            }
        }
        dialogBuilder.setPositiveButton(R.string.OK) { dialog, which ->
            updateOrDeleteImage(type, image)
        }
        dialogBuilder.setNegativeButton(R.string.cancel) { dialog, which ->

        }
        dialogBuilder.show()
    }

    private fun updateOrDeleteImage(type: String, image: Image) {
        imageList.remove(image)
        var done: Boolean = false
        when (type){
            "update", "new" -> {

                image.alt = etAlt.text.toString()
                image.title = etTitle.text.toString()
                image.seq = imageList.size + 1
                var mime = MimeTypeMap.getSingleton()
                if (type == "new") {
                    var ext = mime.getExtensionFromMimeType(contentResolver.getType(resizedUri))
                    var lastItemSource = imageList.get(imageList.size - 1)
                    var count = lastItemSource.seq + 1
                    image.source = preSourceText + count + "_" + imgName.text.toString() + "." + ext
                }
                imageList.add(image)
                val updatedJson = Json.encodeToString(imageList)
                val localFile: File = File(filesDir.path + "/images.json")
                localFile.writeText(updatedJson)
                val job = GlobalScope.launch {
                    done = withContext(Dispatchers.Default) { saveOrUpdateFtp(type, image) }
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
            "delete" -> {

                val updatedJson = Json.encodeToString(imageList)
                val localFile: File = File(filesDir.path + "/images.json")
                localFile.writeText(updatedJson)
                val job = GlobalScope.launch {
                    done = withContext(Dispatchers.Default) { deleteFromFtp(image) }
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

    private fun saveOrUpdateFtp(type: String, image: Image): Boolean {
        val client : FTPClient = getFtpClient()
        val inputStream = FileInputStream(filesDir.path + "/images.json")
        var res =  client.storeFile("$folder/images.json", inputStream)
        inputStream.close()
        if (type == isNew) {
            val imageInputStream = contentResolver.openInputStream(resizedUri)
            res = client.storeFile("pedicure_hu" + image.source, imageInputStream)
            imageInputStream?.close()
        }
        return res
    }
    private fun deleteFromFtp(image: Image): Boolean {
        val client : FTPClient = getFtpClient()
        val inputStream = FileInputStream(filesDir.path + "/images.json")
        client.storeFile("$folder/images.json", inputStream)
        val res = client.deleteFile("pedicure_hu" + image.source)
        return res
    }

    private fun initProperties() {
        val resources = this.resources
        val inputStream: InputStream = resources.openRawResource(
                resources.getIdentifier(
                        "config_properties",
                        "raw",
                        packageName
                )
        )
        val prop = Properties()
        prop.load(inputStream)
        server = prop.getProperty("server")
        folder = prop.getProperty("folder")
        user = prop.getProperty("user")
        pass = prop.getProperty("pwd")

    }

    private fun loadImages() {

        lateinit var jsonFile: File
        val job1 = GlobalScope.launch(Dispatchers.IO) {

            jsonFile = withContext(Dispatchers.IO) {
                getFTPFile()
            }

        }
        runBlocking {
            job1.join()
        }
        imageList.clear()
        imageList.addAll(parseListFromJson(jsonFile))
        imageList.reverse()
        imageAdapter.notifyDataSetChanged()
    }

    private fun getFTPFile(): File {

        val client : FTPClient = getFtpClient()

        val localFile: File = File(filesDir.path + "/images.json")
        if(!localFile.exists()){
            localFile.createNewFile();
        }
        var remoteFilePath = "$folder/images.json"
        var outputStream: OutputStream = BufferedOutputStream(FileOutputStream(localFile));
        var success = client.retrieveFile(remoteFilePath, outputStream);
        if (success) {
             outputStream.close();
             client.disconnect()
        }
        return localFile
    }

    private fun getFtpClient(): FTPClient {
        val client = FTPClient()
        client.connect(server)

        val reply = client.replyCode
        if (!FTPReply.isPositiveCompletion(reply)) {
            client.disconnect()
        }
        if (!client.login(user, pass)) {
            client.disconnect()
        }
        client.enterLocalPassiveMode()
        client.setFileType(FTP.BINARY_FILE_TYPE);
        return client;
    }

    private fun parseListFromJson(jsonFile: File) : MutableList<Image> {

        var jsons =  jsonFile.readText(Charset.forName("UTF-8")).replace("\r\n", "")
        return mapper.readValue(jsons)
    }

}


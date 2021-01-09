package hu.pedicure.image_uploader

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
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
import org.json.JSONArray
import java.io.*
import java.net.URI
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

    private lateinit var imageList: MutableList<Image>
    private lateinit var  imageAdapter: ImageAdapter
    private lateinit var linearLayoutManager: LinearLayoutManager
    private lateinit var mapper: JsonMapper
    private var photoSelect = 1
    private lateinit var selectedPhotoUri: Uri

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

        mapper = JsonMapper()
        mapper.registerKotlinModule()

        fab.visibility = View.INVISIBLE

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

        initProperties()

        loadImages()

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
        dialogBuilder.setNegativeButton(R.string.cancel) { dialog, which ->
            Log.d("xxx", "cancelled")
        }
        dialogBuilder.show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            photoSelect -> {
                if (data != null) {
                    selectedPhotoUri = data.data!!
                    var image = Image()
                    createUpdateDialog(image, isNew)
                }
            }
        }
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
                Picasso.get().load(selectedPhotoUri).into(dialogImg)
                imgName.focusable = View.FOCUSABLE
            }
        }
        dialogBuilder.setPositiveButton(R.string.OK) { dialog, which ->
            updateOrDeleteImage(type, image)
        }
        dialogBuilder.setNegativeButton(R.string.cancel) { dialog, which ->
            Log.d("xxx", "cancelled")
        }
        dialogBuilder.show()
    }

    private fun updateOrDeleteImage(type: String, image: Image) {
        imageList.remove(image)
        var done: Boolean = false
        when (type){
            "update", "new" -> {
                Log.d("xxx", "update")
                image.alt = etAlt.text.toString()
                image.title = etTitle.text.toString()
                var mime = MimeTypeMap.getSingleton()
                if (type == "new") {
                    var ext = mime.getExtensionFromMimeType(contentResolver.getType(selectedPhotoUri))
                    var lastItemSource = imageList.get(imageList.size - 1).source
                    var count = lastItemSource.substring(lastItemSource.lastIndexOf("/") + 1, lastItemSource.indexOf("_")).toInt() +1
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
                Log.d("xxx", "delete")
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
            val imageInputStream = contentResolver.openInputStream(selectedPhotoUri)
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
        val job1 = GlobalScope.launch {
                jsonFile = withContext(Dispatchers.Default) { getFTPFile() }
        }
        runBlocking {
            job1.join()
        }
        imageList.clear()
        imageList.addAll(parseListFromJson(jsonFile))
        imageList.reverse()
        imageAdapter.notifyDataSetChanged()
        pBar.visibility = View.INVISIBLE
        fab.visibility = View.VISIBLE
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
        var images: MutableList<Image> = mapper.readValue(jsons)
        return images
    }
}


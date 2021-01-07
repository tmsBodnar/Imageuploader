package hu.pedicure.image_uploader

import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.ContentLoadingProgressBar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fasterxml.jackson.databind.json.JsonMapper
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply
import java.nio.charset.Charset
import com.fasterxml.jackson.module.kotlin.*
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import org.json.JSONArray
import kotlinx.serialization.json.Json
import java.io.*
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

    private lateinit var imageList: MutableList<Image>
    private lateinit var  imageAdapter: ImageAdapter
    private lateinit var linearLayoutManager: LinearLayoutManager
    private lateinit var job1: Job
    private lateinit var mapper: JsonMapper

    private val isUpdate = "update"
    private val isDelete = "delete"

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
        imageAdapter.onItemClickDelete = {
            image ->
            deleteImage(image)
        }
        imageAdapter.onItemClickEdit = {
            image ->
            editImage(image)
        }
        recView.adapter = imageAdapter

        initProperties()

        loadImages()

    }

    fun addNewImage(view: View) {
      //  createUpdateDialog(null)
    }

    private fun editImage(image: Image) {
        createUpdateDialog(image)

    }

    private fun createUpdateDialog(image: Image) {
        var dialogBuilder = AlertDialog.Builder(this)
        var dialog = layoutInflater.inflate(R.layout.custom_dialog, null)
        dialogBuilder.setView(dialog)
        etTitle = dialog.findViewById(R.id.et_title)
        etAlt = dialog.findViewById(R.id.et_alt)
        etTitle.setText(image.title,TextView.BufferType.EDITABLE)
        etAlt.setText(image.alt,TextView.BufferType.EDITABLE)
        dialogBuilder.setPositiveButton(R.string.OK) { dialog, which ->
            updateOrDeleteImage(isUpdate, image)

        }
        dialogBuilder.setNegativeButton(R.string.cancel) { dialog, which ->
            Log.d("xxx", "cancelled")

        }
        dialogBuilder.show()
    }

    private fun updateOrDeleteImage(type: String, image: Image) {
        imageList.remove(image)
        when (type){
            "update" -> {
                Log.d("xxx", "update")
                image.alt = etAlt.text.toString()
                image.title = etTitle.text.toString()
                imageList.add(0, image)
                val updatedJson = Json.encodeToString(imageList)
                val localFile: File = File( filesDir.path + "/images.json")
                localFile.writeText(updatedJson)
                val job = GlobalScope.launch {
                    withContext(Dispatchers.Default) {
                        val client : FTPClient = getFtpClient()
                        client.storeFile("$folder/images.json", openFileInput(filesDir.path + "/images.json"))
                    }

                }
                runBlocking {
                    job.join()
                }
                loadImages()
            }
            "delete" -> {
                Log.d("xxx", "delete")
                val updatedJson = JSONArray(imageList)
                val localFile: File = File( filesDir.path + "/images.json")
                localFile.writeText(updatedJson.toString())
                val client : FTPClient = getFtpClient()
                client.storeFile("$folder/images.json", openFileInput(filesDir.path + "/images.json"))
                client.deleteFile(folder + "/" +image.source)
            }
        }
    }

    private fun deleteImage(image: Image) {

        var dialogBuilder = AlertDialog.Builder(this)
        dialogBuilder.setTitle(R.string.delete )
        dialogBuilder.setMessage(R.string.confirm)
        dialogBuilder.setPositiveButton(R.string.OK) { dialog, which ->
            updateOrDeleteImage(isDelete, image)
        }
        dialogBuilder.setNegativeButton(R.string.cancel) { dialog, which ->
            Log.d("xxx", "cancelled")

        }
        dialogBuilder.show()
    }

    private fun initProperties() {
        val resources = this.resources
        val inputStream: InputStream = resources.openRawResource(resources.getIdentifier("config_properties", "raw", packageName))
        val prop = Properties()
        prop.load(inputStream)
        server = prop.getProperty("server")
        user = prop.getProperty("user")
        pass = prop.getProperty("pwd")
        folder = prop.getProperty("folder")

    }

    private fun loadImages() {
        lateinit var jsonFile: File
        job1 = GlobalScope.launch {
                jsonFile = withContext(Dispatchers.Default) { getFTPFile() }
        }
        runBlocking {
            job1.join()
        }
        imageList.clear()
        imageList.addAll(parseListFromJson(jsonFile))
        imageAdapter.notifyDataSetChanged()
        pBar.visibility = View.INVISIBLE
        fab.visibility = View.VISIBLE
    }

    private fun getFTPFile(): File {
       val client : FTPClient = getFtpClient()

        val localFile: File = File( filesDir.path + "/images.json")
        if(!localFile.exists()){
            localFile.createNewFile();
        }
        val ftpFiles = client.listFiles(folder).toList()
        if (ftpFiles != null && ftpFiles.isNotEmpty()) {
            for (file in ftpFiles) {
                if (file.isFile && file.name.endsWith(".json", true)) {
                    var remoteFile = folder + "/" + file.name
                    var outputStream1: OutputStream = BufferedOutputStream(FileOutputStream(localFile));
                    var success = client.retrieveFile(remoteFile, outputStream1);

                    if (success) {
                        outputStream1.close();
                        client.disconnect()
                    }
                }
            }
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


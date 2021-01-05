package hu.pedicure.image_uploader

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.ContentLoadingProgressBar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fasterxml.jackson.databind.json.JsonMapper
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.charset.Charset
import com.fasterxml.jackson.module.kotlin.*
import kotlinx.coroutines.*
import org.apache.commons.net.ftp.FTPFile

class MainActivity : AppCompatActivity() {

    private val server = "ftp.aaa.hu"
    private val user = "bbb@ccc.hu"
    private val pass = "ddd"
    private val folder = "/eee"

    private lateinit var linearLayoutManager: LinearLayoutManager
    private lateinit var recView: RecyclerView
    private lateinit var pBar: ContentLoadingProgressBar
    private lateinit var imageList: MutableList<Image>
    private lateinit var  imageAdapter: ImageAdapter
    private lateinit var job1: Job

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        recView = findViewById(R.id.rec_view)
        pBar = findViewById(R.id.progress_circular)
        linearLayoutManager = LinearLayoutManager(this)
        recView.layoutManager = linearLayoutManager
        imageList = mutableListOf(Image())
        imageAdapter = ImageAdapter(imageList)
        recView.adapter = imageAdapter
    }

    fun galleryOnClick(view: View) {
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
    }

    fun addNewImage(view: View) {

    }

    private fun getFTPFile(): File {
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

    private fun parseListFromJson(jsonFile: File) : MutableList<Image> {
        val mapper = JsonMapper()
        mapper.registerKotlinModule()
        var jsons =  jsonFile.readText(Charset.forName("UTF-8")).replace("\r\n", "")
        var images: MutableList<Image> = mapper.readValue(jsons)
        return images
    }
}


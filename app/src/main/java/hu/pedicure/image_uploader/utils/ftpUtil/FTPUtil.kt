package hu.pedicure.image_uploader.utils.ftpUtil

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.core.app.ActivityCompat
import hu.pedicure.image_uploader.utils.imageUtils.ImageResizing
import hu.pedicure.image_uploader.model.Image
import hu.pedicure.image_uploader.model.Type
import hu.pedicure.image_uploader.utils.propertiesUtil.PropertiesInitializer
import kotlinx.coroutines.*
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply
import java.io.*


class FTPUtil(private var ctx: Context) {

    private var propInitializer: PropertiesInitializer
    private var imageFormatter = ImageResizing()
    private var client: FTPClient
    private var folder: String
    private var server: String
    private var user: String
    private var pass: String

    private lateinit var jsonFile: File
    private var asyncResult: Boolean = false


    init {
        this.client = FTPClient()
        this.propInitializer = PropertiesInitializer(this.ctx)

        this.folder = this.propInitializer.folder
        this.server = this.propInitializer.server
        this.user = this.propInitializer.user
        this.pass = this.propInitializer.pass
    }

    fun getJSONFile(): File{
        val job = GlobalScope.launch(Dispatchers.IO) {
            val jsonFileAsync = async(Dispatchers.IO) {
                asyncGetFTPFile()
            }
            jsonFile = jsonFileAsync.await()
        }
        runBlocking {
          job.join()
      }
        return jsonFile
    }

    fun saveOrUpdateFtp(type: Type, image: Image, selectedPhotoUri: Uri): Boolean {
        asyncResult = false

        val job1 = GlobalScope.launch(Dispatchers.IO) {
            asyncResult = withContext(Dispatchers.IO) {
                asyncSaveOrUpdate(type, image, selectedPhotoUri)
            }
        }
        runBlocking {
            job1.join()
        }
        return asyncResult
    }


    fun deleteFromFtp(image: Image): Boolean {
        asyncResult = false
        val job1 = GlobalScope.launch(Dispatchers.IO) {
            asyncResult = withContext(Dispatchers.IO) {
                asyncDeleteFromFTP(image)
            }
        }
        runBlocking {
            job1.join()
        }
        return asyncResult
    }

    private fun asyncDeleteFromFTP(image: Image): Boolean{
        client = getFtpClient()
        val inputStream = FileInputStream(this.ctx.filesDir.path + "/images.json")
        client.storeFile("$folder/images.json", inputStream)
        val res = client.deleteFile("pedicure_hu" + image.source)
        if (res) {
            inputStream.close()
            client.disconnect()
        }
        return res
    }

    private fun asyncGetFTPFile(): File {
        client = getFtpClient()
        val localFile = File(this.ctx.filesDir.path + "/images.json")
        if(!localFile.exists()){
            localFile.createNewFile()
        }
        val remoteFilePath = "$folder/images.json"
        val outputStream: OutputStream = BufferedOutputStream(FileOutputStream(localFile))
        val success = client.retrieveFile(remoteFilePath, outputStream)
        if (success) {
            outputStream.close()
            client.disconnect()
        }
        return localFile
    }

    private fun asyncSaveOrUpdate(type: Type, image: Image, selectedPhotoUri: Uri): Boolean {
        this.client = getFtpClient()
        val inputStream = FileInputStream(this.ctx.filesDir.path + "/images.json")
        var res =  client.storeFile("$folder/images.json", inputStream)
        inputStream.close()
        val file = imageFormatter.resizeImageToWeb(selectedPhotoUri, this.ctx)
        val isExist = file.exists()
        if (type == Type.NEW && isExist) {
            val permission = ActivityCompat.checkSelfPermission(ctx, Manifest.permission.READ_EXTERNAL_STORAGE)

            if (permission != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(ctx as Activity, Array<String>(1) { Manifest.permission.READ_EXTERNAL_STORAGE }, 100)
            }
            val fis = FileInputStream(file)
            res = client.storeFile("pedicure_hu" + image.source, fis)
            fis.close()
        }
        if (res) {
            client.disconnect()
        }
        file.delete()
        return res
    }

    private fun getFtpClient(): FTPClient {

        client.connect(server)

        val reply = client.replyCode
        if (!FTPReply.isPositiveCompletion(reply)) {
            client.disconnect()
        }
        if (!client.login(user, pass)) {
            client.disconnect()
        }
        client.enterLocalPassiveMode()
        client.setFileType(FTP.BINARY_FILE_TYPE)
        return client
    }
}
package hu.pedicure.image_uploader.ftpHelper

import android.content.Context
import android.net.Uri
import hu.pedicure.image_uploader.model.Image
import hu.pedicure.image_uploader.model.Type
import hu.pedicure.image_uploader.propertiesHelper.PropertiesHelper
import kotlinx.coroutines.*
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply
import java.io.*

class FTPHelper(private var ctx: Context) {

    private var propHelper: PropertiesHelper
    private var client: FTPClient
    private var folder: String
    private var server: String
    private var user: String
    private var pass: String

    private lateinit var jsonFile: File


    init {
        this.client = FTPClient()
        this.propHelper = PropertiesHelper(this.ctx)
        this.folder = this.propHelper.folder
        this.server = this.propHelper.server
        this.user = this.propHelper.user
        this.pass = this.propHelper.pass
    }


    fun saveOrUpdateFtp(type: Type, image: Image, selectedPhotoUri: Uri): Boolean {
        this.client = getFtpClient()
        val inputStream = FileInputStream(this.ctx.filesDir.path + "/images.json")
        var res =  client.storeFile("$folder/images.json", inputStream)
        inputStream.close()
        if (type == Type.NEW) {
            val imageInputStream = ctx.contentResolver.openInputStream(selectedPhotoUri)
            res = client.storeFile("pedicure_hu" + image.source, imageInputStream)
            imageInputStream?.close()
        }
        if (res) {
            client.disconnect()
        }
        return res
    }
    fun deleteFromFtp(image: Image): Boolean {
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

    private fun getFTPFile(): File {
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

    fun getJSONFile(): File{
        val job1 = GlobalScope.launch(Dispatchers.IO) {
            jsonFile = withContext(Dispatchers.IO) {
                getFTPFile()
            }
        }
        runBlocking {
            job1.join()
        }
        return jsonFile
    }


}
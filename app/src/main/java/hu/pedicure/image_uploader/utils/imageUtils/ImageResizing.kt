package hu.pedicure.image_uploader.utils.imageUtils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.MediaStore
import androidx.core.app.ActivityCompat
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.*

class ImageResizing {

    fun resizeImageToWeb(origImageUri: Uri, ctx: Context): File {

        val source = ImageDecoder.createSource(ctx.contentResolver, origImageUri)
        val origBitmap = ImageDecoder.decodeBitmap(source)
        val resized = Bitmap.createScaledBitmap(origBitmap, 1185, 665, true)
        val path = ctx.filesDir.path
        val fileName = "${UUID.randomUUID()}.jpg"
        val file = File("$path/$fileName")
            try {
                val permission = ActivityCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                if (permission != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(ctx as Activity,Array<String>(1){Manifest.permission.WRITE_EXTERNAL_STORAGE}, 100)
                }
                file.createNewFile()
                val stream: OutputStream = FileOutputStream(file)
                resized.compress(Bitmap.CompressFormat.JPEG, 100, stream)

                stream.flush()
                stream.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
            return file
        }
    }



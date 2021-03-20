package hu.pedicure.image_uploader.propertiesHelper

import android.content.Context
import java.io.InputStream
import java.util.*

class PropertiesHelper {

    private lateinit var server: String
    private lateinit var folder: String
    private lateinit var user: String
    private lateinit var pass: String
    private lateinit var domain: String

    constructor(ctx: Context) {
        initProperties(ctx)
    }

    private fun initProperties(ctx: Context) {
        val resources = ctx.resources
        val inputStream: InputStream = resources.openRawResource(
            resources.getIdentifier(
                "config_properties",
                "raw",
                ctx.packageName
            )
        )
        val prop = Properties()
        prop.load(inputStream)
        server = prop.getProperty("server")
        folder = prop.getProperty("folder")
        user = prop.getProperty("user")
        pass = prop.getProperty("pwd")
        domain = prop.getProperty("domain")
    }

    fun getServer(): String {
        return server
    }

    fun getFolder(): String{
        return folder
    }

    fun getUser(): String{
        return user
    }

    fun getPass(): String{
        return pass
    }

    fun getDomain(): String{
        return domain
    }
}
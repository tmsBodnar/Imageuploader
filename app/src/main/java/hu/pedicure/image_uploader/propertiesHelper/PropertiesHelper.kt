package hu.pedicure.image_uploader.propertiesHelper

import android.content.Context
import java.io.InputStream
import java.util.*

class PropertiesHelper(ctx: Context) {

    lateinit var server: String
    lateinit var folder: String
    lateinit var user: String
    lateinit var pass: String
    lateinit var domain: String

    init {
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
}
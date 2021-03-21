package hu.pedicure.image_uploader.utils.imageUtils

import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import hu.pedicure.image_uploader.model.Image
import java.io.File
import java.nio.charset.Charset

class JSONImageDetailsUtil() {

    private var mapper: JsonMapper = JsonMapper()

    init {
        mapper.registerKotlinModule()
    }

    fun loadImages(jsonFile: File): Collection<Image> {
        val json =  jsonFile.readText(Charset.forName("UTF-8")).replace("\r\n", "")
        return mapper.readValue(json)
    }
}
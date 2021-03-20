package hu.pedicure.image_uploader.imageDetailsListHelper

import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import hu.pedicure.image_uploader.model.Image
import java.io.File
import java.nio.charset.Charset

class ImageDetailsListHelper {

    private var mapper: JsonMapper = JsonMapper()

    constructor(){
        mapper.registerKotlinModule()
    }

    fun loadImages(jsonFile: File): Collection<Image> {
        var jsons =  jsonFile.readText(Charset.forName("UTF-8")).replace("\r\n", "")
        return mapper.readValue(jsons)
    }
}
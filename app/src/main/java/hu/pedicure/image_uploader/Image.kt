package hu.pedicure.image_uploader

import kotlinx.serialization.*

@Serializable
data class Image(
    var source: String,
    var alt: String,
    var title : String
) {
    constructor() : this("","","")
}
package hu.pedicure.image_uploader

import kotlinx.serialization.*

@Serializable
data class Image(
    var seq: Int = -1,
    var source: String = "",
    var alt: String = "",
    var title : String = ""
)
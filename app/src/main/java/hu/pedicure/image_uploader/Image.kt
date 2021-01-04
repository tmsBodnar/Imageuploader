package hu.pedicure.image_uploader

data class Image(
    var source: String,
    var alt: String,
    var title : String
) {
    constructor() : this("","","")
}
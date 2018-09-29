package com.lielu.testmapappandroid1.feature

class AddCommentRequestClass {
    var latitude: String = ""
    var longitude: String = ""
    var comment: String = ""

    constructor(latitude: String, longitude: String, comment: String) {
        this.latitude = latitude
        this.longitude = longitude
        this.comment = comment
    }

    constructor() {}
}
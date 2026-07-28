package com.nextbench.data.firebase

import com.nextbench.data.firebase.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.DataOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

data class CloudinaryResult(
    val url: String,
    val publicId: String,
    val width: Int,
    val height: Int,
    val format: String,
)

/**
 * Unsigned Cloudinary upload — no server secret required. The upload preset is read from
 * BuildConfig (injected from local.properties at build time).
 */
@Singleton
class CloudinaryUploader @Inject constructor() {

    private val cloudName = BuildConfig.CLOUDINARY_CLOUD_NAME
    private val uploadPreset = BuildConfig.CLOUDINARY_UPLOAD_PRESET
    private val uploadUrl get() = "https://api.cloudinary.com/v1_1/$cloudName/image/upload"

    suspend fun uploadImage(file: File, folder: String = "nextbench"): CloudinaryResult =
        withContext(Dispatchers.IO) {
            val boundary = "----FormBoundary${System.currentTimeMillis()}"
            val conn = (URL(uploadUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            }
            DataOutputStream(conn.outputStream).use { out ->
                fun field(name: String, value: String) {
                    out.writeBytes("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n")
                }
                field("upload_preset", uploadPreset)
                field("folder", folder)
                out.writeBytes("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"${file.name}\"\r\nContent-Type: image/*\r\n\r\n")
                file.inputStream().copyTo(out)
                out.writeBytes("\r\n--$boundary--\r\n")
            }
            check(conn.responseCode == 200) { "Cloudinary upload failed: ${conn.responseCode}" }
            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            CloudinaryResult(
                url = json.getString("secure_url"),
                publicId = json.getString("public_id"),
                width = json.optInt("width"),
                height = json.optInt("height"),
                format = json.optString("format"),
            )
        }
}

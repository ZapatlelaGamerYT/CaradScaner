package com.example.cardscanner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

object ClaudeClient {
    const val MODEL = "claude-sonnet-5-5"
    private const val KEYS = "\"Name\",\"Designation\",\"Organisation\",\"Phone\",\"Email\",\"Website\",\"Address\""
    private const val ONE = "This image contains one or more visiting/business cards. Read every card carefully and return ONLY a JSON array, one object per card, with exactly these string keys: $KEYS. Copy text exactly as printed; join several phone numbers with \", \" (keep country codes and +); use \"\" for anything missing; never guess or invent values. If there is no visiting card return []."
    private const val TWO = "These two images are the FRONT and BACK of the SAME single visiting card. Combine both sides into ONE object with exactly these string keys: $KEYS. Merge details from both sides (the back may hold a second language, extra phones, address or website). Copy text exactly as printed; join several phone numbers with \", \"; use \"\" for anything missing; never guess. Return ONLY a JSON array containing that one object."

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS).build()

    suspend fun extract(key: String, images: List<ByteArray>): List<Contact> = withContext(Dispatchers.IO) {
        val content = JSONArray()
        images.forEach {
            content.put(JSONObject().put("type", "image").put("source", JSONObject()
                .put("type", "base64").put("media_type", "image/jpeg")
                .put("data", Base64.encodeToString(it, Base64.NO_WRAP))))
        }
        content.put(JSONObject().put("type", "text").put("text", if (images.size > 1) TWO else ONE))
        val body = JSONObject().put("model", MODEL).put("max_tokens", 1500)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
        val req = Request.Builder().url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", key.trim()).header("anthropic-version", "2023-06-01")
            .post(body.toString().toRequestBody("application/json".toMediaType())).build()
        http.newCall(req).execute().use { r ->
            val s = r.body?.string().orEmpty()
            if (!r.isSuccessful) {
                val m = try { JSONObject(s).getJSONObject("error").getString("message") } catch (e: Exception) { "" }
                throw IOException("HTTP ${r.code} $m")
            }
            val blocks = JSONObject(s).getJSONArray("content")
            var text = ""
            for (i in 0 until blocks.length()) {
                val b = blocks.getJSONObject(i)
                if (b.optString("type") == "text") text += b.getString("text")
            }
            val a = text.indexOf('['); val z = text.lastIndexOf(']')
            if (a < 0 || z < a) throw IOException("Unreadable reply")
            val arr = JSONArray(text.substring(a, z + 1))
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Contact.of(Contact.LABELS.map { o.optString(it, "").trim() })
            }
        }
    }
}

object Img {
    fun encode(bmp: Bitmap, rot: Int): ByteArray {
        var b = bmp
        if (rot != 0) {
            val m = Matrix(); m.postRotate(rot.toFloat())
            b = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        }
        val s = minOf(1f, 1600f / maxOf(b.width, b.height))
        if (s < 1f) b = Bitmap.createScaledBitmap(b, (b.width * s).toInt(), (b.height * s).toInt(), true)
        val o = ByteArrayOutputStream()
        b.compress(Bitmap.CompressFormat.JPEG, 88, o)
        return o.toByteArray()
    }

    fun fromUri(ctx: Context, uri: Uri): ByteArray? {
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val opts = BitmapFactory.Options().apply { inSampleSize = if (bytes.size > 3_000_000) 2 else 1 }
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
        val ori = try { ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) } catch (e: Exception) { 1 }
        val rot = when (ori) { 6 -> 90; 3 -> 180; 8 -> 270; else -> 0 }
        return encode(bmp, rot)
    }
}

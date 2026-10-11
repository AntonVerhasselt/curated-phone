package be.anton.curatedphone.network

import be.anton.curatedphone.BuildConfig
import be.anton.curatedphone.data.AppJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.util.concurrent.TimeUnit

class BackendClient(private val vault: CredentialVault) {
    private val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(45, TimeUnit.SECONDS).followRedirects(false).build()
    suspend fun call(path: String, body: JsonObject? = null): String = withContext(Dispatchers.IO) {
        val base = BuildConfig.BACKEND_URL.trimEnd('/')
        require(base.startsWith("https://")) { "Configure an HTTPS backend URL" }
        val builder = Request.Builder().url(base + path)
        vault.token()?.let { builder.header("Authorization", "Bearer $it") }
        if (body != null) builder.post(body.toString().toRequestBody("application/json".toMediaType()))
        client.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string() ?: error("Empty backend response")
            if (!response.isSuccessful) error(runCatching { AppJson.parseToJsonElement(text).jsonObject["error"]?.jsonPrimitive?.content }.getOrNull() ?: "Connection failed (${response.code})")
            text
        }
    }
}

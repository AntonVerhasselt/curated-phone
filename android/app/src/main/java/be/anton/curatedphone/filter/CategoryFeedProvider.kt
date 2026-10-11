package be.anton.curatedphone.filter

import android.content.Context
import be.anton.curatedphone.approval.PolicyValidator
import be.anton.curatedphone.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest

@Serializable data class CachedFeed(val source: FeedSource, val fetchedAt: Long, val checksum: String, val domains: List<String>)
interface CategoryFeedProvider { suspend fun load(source: FeedSource): CachedFeed }
class HttpsCategoryFeedProvider(context: Context, private val onLoaded: suspend (CachedFeed) -> Unit = {}) : CategoryFeedProvider {
    private val directory = File(context.filesDir, "feeds").apply { mkdirs() }
    private val client = OkHttpClient.Builder().followRedirects(false).build()
    override suspend fun load(source: FeedSource): CachedFeed = withContext(Dispatchers.IO) {
        val sourceDigest = MessageDigest.getInstance("SHA-256").digest(AppJson.encodeToString(FeedSource.serializer(), source).toByteArray()).joinToString("") { "%02x".format(it) }
        val file = File(directory, "$sourceDigest.json")
        val cached = runCatching { AppJson.decodeFromString<CachedFeed>(file.readText()) }.getOrNull()?.takeIf { it.source == source && it.domains.isNotEmpty() }
        if (cached != null && System.currentTimeMillis() - cached.fetchedAt < 86_400_000) { runCatching { onLoaded(cached) }; return@withContext cached }
        try {
            require(source.url.startsWith("https://"))
            val content = client.newCall(Request.Builder().url(source.url).build()).execute().use { response ->
                check(response.isSuccessful) { "Feed download failed" }
                val body = response.body ?: error("Empty feed")
                require(body.contentLength() <= 5_000_000) { "Feed is too large" }
                body.byteStream().use { stream ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) { val count = stream.read(buffer); if (count < 0) break; require(output.size() + count <= 5_000_000) { "Feed is too large" }; output.write(buffer, 0, count) }
                    output.toString("UTF-8")
                }
            }
            val hosts = content.lineSequence().map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() }.map(PolicyValidator::domain).distinct().sorted().toList()
            require(hosts.isNotEmpty()) { "Feed update cannot erase the last-known-good list" }
            val feed = CachedFeed(source, System.currentTimeMillis(), MessageDigest.getInstance("SHA-256").digest(content.toByteArray()).joinToString("") { "%02x".format(it) }, hosts)
            val temporary = File(directory, "$sourceDigest.tmp")
            temporary.writeText(AppJson.encodeToString(CachedFeed.serializer(), feed)); check(temporary.renameTo(file))
            runCatching { onLoaded(feed) }
            feed
        } catch (e: Exception) { cached ?: throw IllegalStateException("No usable feed for ${source.category}. Configure a tested licensed feed.", e) }
    }
}

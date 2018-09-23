package voodoo.util

import com.github.kittinunf.fuel.core.*
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.result.Result
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.features.defaultRequest
import io.ktor.client.request.header
import mu.KLogger
import mu.KLogging
import voodoo.util.UtilConstants.VERSION
import java.io.File
import kotlinx.io.IOException
import voodoo.util.redirect.HttpRedirectFixed

/**
 * Created by nikky on 30/03/18.
 * @author Nikky
 */
object Downloader : KLogging() {
    val client = HttpClient(OkHttp) {
        engine {
//            maxConnectionsCount = 1000 // Maximum number of socket connections.
//            endpoint.apply {
//                maxConnectionsPerRoute = 100 // Maximum number of requests for a specific endpoint route.
//                pipelineMaxSize = 20 // Max number of opened endpoints.
//                keepAliveTime = 5000 // Max number of milliseconds to keep each connection alive.
//                connectTimeout = 5000 // Number of milliseconds to wait trying to connect to the server.
//                connectRetryAttempts = 5 // Maximum number of attempts for retrying a connection.
//            }
            config {
                followRedirects(true)
            }
        }
        defaultRequest {
            header("User-Agent", useragent)
        }
        install(HttpRedirectFixed) {
            applyUrl { it.encoded }
        }
    }

    val useragent = "voodoo/$VERSION (https://github.com/elytra/Voodoo)"
}

fun File.download(
    url: String,
    cacheDir: File,
    useragent: String = Downloader.useragent,
    logger: KLogger = Downloader.logger
) {
    val cacheFile = cacheDir.resolve(this.name)
//    withContext(Dispatchers.IO) {
        val fixedUrl = url.encoded
        logger.info("downloading $url -> ${this@download}")
        logger.debug("cacheFile $cacheFile")
        if (cacheFile.exists() && !cacheFile.isFile) cacheFile.deleteRecursively()

        if (!cacheFile.exists() || !cacheFile.isFile) {
//        try {
//            val handler = CoroutineExceptionHandler { context, exception ->
//                exception.printStackTrace()
//                println("Caught $exception")
//            }
//
//            withContext(context = handler) {
//                val result2 = downloader.client.get<HttpResponse>(url.encoded)
//                val bytes = result2.readBytes()
//
//                cacheFile.parentFile.mkdirs()
//                cacheFile.writeBytes(bytes)
//            }
//        } catch (cancellation: JobCancellationException) {
//            logger.error("error on $url")
//            cancellation.printStackTrace()
//            logger.error(cancellation.message)
////            throw p
//            logger.error("fallback to fuel")
//            withContext(NonCancellable) {
            var nextUrl = url
            do {
                nextUrl = nextUrl.encoded
                logger.info { nextUrl }
                val (request, response, result) = nextUrl //.encode()
                    .httpGet().header("User-Agent" to useragent)
                    .allowRedirects(false)
                    .response()
                val isRedirect = when (result) {
                    is Result.Success -> {
                        cacheDir.mkdirs()
                        cacheFile.parentFile.mkdirs()
                        cacheFile.writeBytes(result.value)
                        false
                    }
                    is Result.Failure -> {
                        if (response.isStatusRedirection) {
                            nextUrl = response.headers["Location"]?.firstOrNull() ?:
                                throw IllegalStateException("missing Location header")
                            true
                        } else {
                            logger.error("invalid statusCode {} from {}", response.statusCode, fixedUrl)
                            logger.error("connection url: {}", request.url)
                            logger.error("content: {}", result.component1())
                            logger.error("error: {}", result.error.toString())
                            throw IOException(result.error.toString())
                        }
                    }
                }
            } while (isRedirect)
        }
//        }
//    }
//    }

    logger.debug("saving $url -> $this")
    try {
        this.parentFile.mkdirs()
        cacheFile.copyTo(this, overwrite = true)
    } catch (e: FileAlreadyExistsException) {
        val fileIsLocked = !this.renameTo(this)
        logger.error("failed to copy file $cacheFile to $this .. file is locked ? $fileIsLocked")
        if (!fileIsLocked)
            cacheFile.copyTo(this, overwrite = true)
    }

    logger.debug("done downloading $url -> $this")
}

val String.encoded: String
    get() = this
        .replace(" ", "%20")
        .replace("[", "%5B")
        .replace("]", "%5D")

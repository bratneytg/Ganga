package com.bratneytg.ganga.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class HttpError(val status: Int?, message: String) : Exception(message)

/**
 * Peticiones HTTP sencillas (sin librerías extra) con timeout y un reintento. Los errores solo
 * mencionan el host (nunca la ruta ni la query) para que no se cuelen claves en la interfaz.
 */
object Http {
    private const val MAX_BYTES = 3_000_000

    suspend fun request(
        url: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
        timeoutMs: Int = 20_000,
        retries: Int = 1,
    ): String {
        val host = try {
            URL(url).host
        } catch (e: Exception) {
            throw HttpError(null, "URL no válida")
        }
        var last: HttpError? = null
        for (attempt in 0..retries) {
            try {
                return withContext(Dispatchers.IO) { once(url, method, headers, body, timeoutMs) }
            } catch (e: HttpError) {
                last = e
                // Errores del cliente (clave mala, etc.) no se reintentan, salvo 429.
                val permanent = e.status != null && e.status < 500 && e.status != 429
                if (permanent || attempt == retries) break
            } catch (e: java.net.SocketTimeoutException) {
                last = HttpError(null, "$host: tiempo de espera agotado")
                if (attempt == retries) break
            } catch (e: java.io.IOException) {
                last = HttpError(null, "$host: sin conexión o no responde")
                if (attempt == retries) break
            }
            delay(1500L * (attempt + 1))
        }
        throw last ?: HttpError(null, "$host: error desconocido")
    }

    private fun once(url: String, method: String, headers: Map<String, String>, body: String?, timeoutMs: Int): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.instanceFollowRedirects = true
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            if (body != null) {
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            if (code !in 200..299) throw HttpError(code, "${conn.url.host} respondió HTTP $code")
            val declared = Regex("charset=([\\w-]+)", RegexOption.IGNORE_CASE)
                .find(conn.contentType ?: "")?.groupValues?.get(1)
            val bytes = conn.inputStream.use { it.readBytes() }.let { if (it.size > MAX_BYTES) it.copyOf(MAX_BYTES) else it }
            val cs = try {
                java.nio.charset.Charset.forName(declared ?: "UTF-8")
            } catch (e: Exception) {
                Charsets.UTF_8
            }
            return String(bytes, cs)
        } finally {
            conn.disconnect()
        }
    }
}

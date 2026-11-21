package com.zhaoguanghao.earbugmusic.net

import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 轻量 HTTP 封装（基于 OkHttp）。
 * 所有方法均为阻塞式，调用方需在 IO 协程中执行。
 */
object Http {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private fun applyHeaders(builder: Request.Builder, headers: Map<String, String>) {
        headers.forEach { (k, v) -> builder.addHeader(k, v) }
    }

    fun get(url: String, headers: Map<String, String> = emptyMap()): String? {
        val req = Request.Builder().url(url).get().also { applyHeaders(it, headers) }.build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            return resp.body?.string()
        }
    }

    fun getBytes(url: String, headers: Map<String, String> = emptyMap()): ByteArray? {
        val req = Request.Builder().url(url).get().also { applyHeaders(it, headers) }.build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            return resp.body?.bytes()
        }
    }

    fun postForm(url: String, form: Map<String, String>, headers: Map<String, String> = emptyMap()): String? {
        val body = FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build()
        val req = Request.Builder().url(url).post(body).also { applyHeaders(it, headers) }.build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            return resp.body?.string()
        }
    }

    fun postBytes(url: String, bytes: ByteArray, headers: Map<String, String> = emptyMap()): ByteArray? {
        val body = bytes.toRequestBody("application/json;charset=utf-8".toMediaType())
        val req = Request.Builder().url(url).post(body).also { applyHeaders(it, headers) }.build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            return resp.body?.bytes()
        }
    }

    fun postJson(url: String, json: String, headers: Map<String, String> = emptyMap()): String? {
        val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
        val req = Request.Builder().url(url).post(body).also { applyHeaders(it, headers) }.build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            return resp.body?.string()
        }
    }

    /** 流式下载到文件，返回是否成功；进度回调为 (已下载字节, 总字节)。https 失败自动尝试 http */
    fun download(url: String, file: File, onProgress: (Long, Long) -> Unit = { _, _ -> }): Boolean {
        if (downloadOnce(url, file, onProgress)) return true
        if (url.startsWith("https://")) {
            return downloadOnce(url.replaceFirst("https://", "http://"), file, onProgress)
        }
        return false
    }

    private fun downloadOnce(url: String, file: File, onProgress: (Long, Long) -> Unit): Boolean {
        return try {
            val req = Request.Builder().url(url).get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return false
                val body = resp.body ?: return false
                val total = body.contentLength()
                body.byteStream().use { input ->
                    file.outputStream().use { output ->
                        val buf = ByteArray(8192)
                        var read: Int
                        var done = 0L
                        while (input.read(buf).also { read = it } != -1) {
                            output.write(buf, 0, read)
                            done += read
                            onProgress(done, total)
                        }
                    }
                }
                return true
            }
        } catch (_: Exception) {
            false
        }
    }
}

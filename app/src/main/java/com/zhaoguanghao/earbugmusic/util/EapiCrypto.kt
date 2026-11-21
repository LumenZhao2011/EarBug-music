package com.zhaoguanghao.earbugmusic.util

import org.json.JSONObject
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * 网易云 eapi 接口加密。
 * 移植自 Suxiaoqinx/Netease_url 的 music_api.py::CryptoUtils：
 *  AES-128-ECB / PKCS7，密钥 e82ckenh8dichen8，
 *  明文格式 = "{api路径}-36cd479b6b5-{json}-36cd479b6b5-{md5(nobody{api路径}use{json}md5forencrypt)}"
 */
object EapiCrypto {
    private const val AES_KEY = "e82ckenh8dichen8"

    fun encrypt(url: String, payload: JSONObject): String {
        val path = java.net.URI(url).path.replace("/eapi/", "/api/")
        val json = payload.toString()
        val digest = md5Hex("nobody" + path + "use" + json + "md5forencrypt")
        val text = path + "-36cd479b6b5-" + json + "-36cd479b6b5-" + digest
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(AES_KEY.toByteArray(Charsets.UTF_8), "AES"))
        return cipher.doFinal(text.toByteArray(Charsets.UTF_8)).toHex()
    }

    fun md5Hex(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        return md.digest(input.toByteArray(Charsets.UTF_8)).toHex()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}

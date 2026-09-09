package com.jesse.finly.utils

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.jesse.finly.database.FirebaseManager
import com.jesse.finly.database.MinhaBaseDados
import com.jesse.finly.models.Transacao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object BackupManager {

    private const val SECRET_KEY_32 = "FinlyAppBackupSecretKey2026Safe"
    private const val IV_16 = "FinlyBackupVector"

    suspend fun exportarBackupParaUri(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val sharedPref = context.getSharedPreferences("FinlyAppPrefs", Context.MODE_PRIVATE)
            val email = sharedPref.getString("EMAIL", "") ?: ""
            val db = MinhaBaseDados.getDatabase(context)
            val transacoes = db.utilizadorDao().obterTransacoesPorDono(email)

            val rootObj = JSONObject()
            rootObj.put("version", 1)
            rootObj.put("email", email)
            rootObj.put("timestamp", System.currentTimeMillis())

            val jsonArray = JSONArray()
            transacoes.forEach { t ->
                val obj = JSONObject()
                obj.put("id", t.id)
                obj.put("item", t.item)
                obj.put("valor", t.valor)
                obj.put("vencimento", t.vencimento)
                obj.put("status", t.status)
                obj.put("tipo", t.tipo)
                obj.put("categoria", t.categoria)
                obj.put("mes", t.mes)
                obj.put("ano", t.ano)
                obj.put("recorrente", t.recorrente)
                obj.put("parcelasRestantes", t.parcelasRestantes)
                obj.put("parcelasTotais", t.parcelasTotais)
                jsonArray.put(obj)
            }
            rootObj.put("transacoes", jsonArray)

            val jsonString = rootObj.toString()
            val encriptadoBytes = encriptarAES(jsonString)

            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(encriptadoBytes)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun importarBackupDeUri(context: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
        try {
            val sharedPref = context.getSharedPreferences("FinlyAppPrefs", Context.MODE_PRIVATE)
            val email = sharedPref.getString("EMAIL", "") ?: ""

            val bytes = context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.readBytes()
            } ?: return@withContext 0

            val jsonString = desencriptarAES(bytes)
            val rootObj = JSONObject(jsonString)
            val jsonArray = rootObj.getJSONArray("transacoes")

            val db = MinhaBaseDados.getDatabase(context)
            var restauradas = 0

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val trans = Transacao(
                    id = obj.optInt("id", 0),
                    item = obj.optString("item", ""),
                    valor = obj.optDouble("valor", 0.0),
                    vencimento = obj.optString("vencimento", ""),
                    status = obj.optBoolean("status", false),
                    tipo = obj.optString("tipo", "DESPESA"),
                    categoria = obj.optString("categoria", "Geral"),
                    mes = obj.optString("mes", ""),
                    ano = obj.optInt("ano", 2026),
                    donoEmail = email,
                    recorrente = obj.optBoolean("recorrente", false),
                    parcelasRestantes = obj.optInt("parcelasRestantes", 0),
                    parcelasTotais = obj.optInt("parcelasTotais", 0)
                )

                val existe = db.utilizadorDao().obterTransacaoPorId(trans.id)
                if (existe == null) {
                    val novoId = db.utilizadorDao().inserirTransacao(trans).toInt()
                    FirebaseManager.salvarTransacaoNoFirestore(trans.copy(id = novoId))
                } else {
                    db.utilizadorDao().atualizarTransacao(trans)
                    FirebaseManager.salvarTransacaoNoFirestore(trans)
                }
                restauradas++
            }
            restauradas
        } catch (_: Exception) {
            0
        }
    }

    private fun encriptarAES(plainText: String): ByteArray {
        val keySpec = SecretKeySpec(SECRET_KEY_32.toByteArray(Charsets.UTF_8), "AES")
        val ivSpec = IvParameterSpec(IV_16.toByteArray(Charsets.UTF_8))
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return Base64.encode(encrypted, Base64.DEFAULT)
    }

    private fun desencriptarAES(bytes: ByteArray): String {
        val decoded = Base64.decode(bytes, Base64.DEFAULT)
        val keySpec = SecretKeySpec(SECRET_KEY_32.toByteArray(Charsets.UTF_8), "AES")
        val ivSpec = IvParameterSpec(IV_16.toByteArray(Charsets.UTF_8))
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
        val decrypted = cipher.doFinal(decoded)
        return String(decrypted, Charsets.UTF_8)
    }
}

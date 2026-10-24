package br.com.mercadofacil.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object ProductLookup {
    private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
    suspend fun name(barcode: String): String? = withContext(Dispatchers.IO) {
        require(Regex("[0-9]{8,14}").matches(barcode)) { "Código de barras inválido" }
        val request = Request.Builder().url("https://world.openfoodfacts.org/api/v2/product/$barcode?fields=product_name,product_name_pt").header("User-Agent", "MercadoFacil/1.0 Android").build()
        client.newCall(request).execute().use { response ->
            require(response.isSuccessful) { "Consulta indisponível" }
            val json = JSONObject(requireNotNull(response.body).string())
            val product = json.optJSONObject("product") ?: return@use null
            product.optString("product_name_pt").ifBlank { product.optString("product_name") }.takeIf { it.isNotBlank() }
        }
    }
}

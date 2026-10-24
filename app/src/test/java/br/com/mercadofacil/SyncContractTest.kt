package br.com.mercadofacil

import br.com.mercadofacil.repository.mutation
import br.com.mercadofacil.updater.Updater
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SyncContractTest {
    @Test fun mutationIncludesOnlyChangedFieldAndItsBase() {
        val before = JSONObject("""{"name":"Arroz","quantity":"1","price":100}""")
        val after = JSONObject("""{"name":"Arroz","quantity":"2","price":100}""")
        val result = JSONObject(mutation(after, before))
        assertEquals(1, result.getJSONObject("data").length())
        assertEquals("2", result.getJSONObject("data").getString("quantity"))
        assertEquals("1", result.getJSONObject("base").getString("quantity"))
    }
    @Test fun newEntityHasEmptyBase() { assertEquals(0, JSONObject(mutation(JSONObject().put("name", "Arroz"), null)).getJSONObject("base").length()) }
    @Test fun nullValueIsPreserved() {
        val result = JSONObject(mutation(JSONObject().put("actual", JSONObject.NULL), JSONObject().put("actual", 100)))
        assertTrue(result.getJSONObject("data").isNull("actual"))
    }
    @Test fun correctAssetAccepted() { assertTrue(valid("https://github.com/test/app/releases/download/v1.0.1/app.apk")) }
    @Test fun foreignRepositoryRejected() { assertFalse(valid("https://github.com/evil/app/releases/download/v1.0.1/app.apk")) }
    @Test fun userInfoRejected() { assertFalse(valid("https://evil@github.com/test/app/releases/download/v1.0.1/app.apk")) }
    @Test fun plaintextRejected() { assertFalse(valid("http://github.com/test/app/releases/download/v1.0.1/app.apk")) }
    @Test fun queryRejected() { assertFalse(valid("https://github.com/test/app/releases/download/v1.0.1/app.apk?redirect=evil")) }
    private fun valid(url: String) = Updater.validAssetUrl(url, "test/app", "v1.0.1", "app.apk")
}

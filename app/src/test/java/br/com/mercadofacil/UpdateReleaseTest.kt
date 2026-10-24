package br.com.mercadofacil

import br.com.mercadofacil.updater.Updater
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class UpdateReleaseTest {
    private fun release() = JSONObject().put("tag_name", "v1.0.2").put("draft", false).put("prerelease", false).put("body", "Correção das atualizações").put("assets", JSONArray()
        .put(JSONObject().put("name", "mercado-facil-1.0.2.apk").put("content_type", "application/vnd.android.package-archive").put("browser_download_url", "https://github.com/test/app/releases/download/v1.0.2/mercado-facil-1.0.2.apk"))
        .put(JSONObject().put("name", "mercado-facil-1.0.2.apk.sha256").put("browser_download_url", "https://github.com/test/app/releases/download/v1.0.2/mercado-facil-1.0.2.apk.sha256")))
    @Test fun normalReleaseIsOfferedToInstalledVersion() { assertEquals("1.0.2", Updater.parseRelease(release(), "test/app", "1.0.1")!!.version) }
    @Test fun currentVersionHasNothingToInstall() { assertNull(Updater.parseRelease(release(), "test/app", "1.0.2")) }
    @Test fun olderVersionIsNeverOffered() { assertNull(Updater.parseRelease(release(), "test/app", "1.0.3")) }
    @Test fun prereleaseCannotEnterNormalUpdateChannel() { assertNull(Updater.parseRelease(release().put("prerelease", true), "test/app", "1.0.1")) }
    @Test fun draftCannotEnterNormalUpdateChannel() { assertNull(Updater.parseRelease(release().put("draft", true), "test/app", "1.0.1")) }
    @Test(expected = IllegalStateException::class) fun missingChecksumIsRejected() {
        val json = release(); json.getJSONArray("assets").remove(1)
        Updater.parseRelease(json, "test/app", "1.0.1")
    }
    @Test(expected = IllegalArgumentException::class) fun foreignAssetIsRejected() {
        val json = release(); json.getJSONArray("assets").getJSONObject(0).put("browser_download_url", "https://github.com/evil/app/releases/download/v1.0.2/mercado-facil-1.0.2.apk")
        Updater.parseRelease(json, "test/app", "1.0.1")
    }
}

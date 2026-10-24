package br.com.mercadofacil

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import br.com.mercadofacil.data.AppSettings
import br.com.mercadofacil.data.Settings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = android.app.Application::class)
class SettingsDefaultsTest {
    @Test fun oldEmptyConnectionUsesProjectDefaultsAfterUpgrade() = runBlocking {
        val settings = Settings(ApplicationProvider.getApplicationContext<Context>())
        settings.save(AppSettings(supabaseUrl = "", supabaseKey = ""))
        val actual = settings.get()
        assertEquals(BuildConfig.SUPABASE_URL, actual.supabaseUrl)
        assertEquals(BuildConfig.SUPABASE_PUBLIC_KEY, actual.supabaseKey)
        assertTrue(actual.supabaseKey.startsWith("sb_publishable_"))
    }
}

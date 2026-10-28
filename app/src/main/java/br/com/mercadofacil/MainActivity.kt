package br.com.mercadofacil

import android.os.Bundle
import android.content.Intent
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.mercadofacil.ui.MercadoScreen

class MainActivity : ComponentActivity() {
    override fun onResume() {
        super.onResume()
        br.com.mercadofacil.notifications.PushNotifications.initialize(this)
    }
    @Deprecated("Android permission callback")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 42) br.com.mercadofacil.notifications.PushNotifications.schedule(this)
    }
    private fun openSharedList(intent: Intent?) {
        val id = intent?.getStringExtra("shared_list_id") ?: intent?.getStringExtra("list_id") ?: return
        if (runCatching { java.util.UUID.fromString(id) }.isFailure) return
        lifecycleScope.launch {
            val app = application as MercadoApp
            if (app.repository.dao.list(id)?.let { it.shared && !it.deleted } == true) app.settings.selectList(id)
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openSharedList(intent)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openSharedList(intent)
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            val prefs = getSharedPreferences("notification-permission", MODE_PRIVATE)
            if (!prefs.getBoolean("asked", false)) {
                prefs.edit().putBoolean("asked", true).apply()
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 42)
            }
        }
        br.com.mercadofacil.notifications.PushNotifications.initialize(this)
        enableEdgeToEdge()
        setContent { MercadoScreen(viewModel()) }
    }
}

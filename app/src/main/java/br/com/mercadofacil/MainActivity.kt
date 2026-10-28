package br.com.mercadofacil

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.mercadofacil.ui.MercadoScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            val prefs = getSharedPreferences("notification-permission", MODE_PRIVATE)
            if (!prefs.getBoolean("asked", false)) {
                prefs.edit().putBoolean("asked", true).apply()
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 42)
            }
        }
        enableEdgeToEdge()
        setContent { MercadoScreen(viewModel()) }
    }
}

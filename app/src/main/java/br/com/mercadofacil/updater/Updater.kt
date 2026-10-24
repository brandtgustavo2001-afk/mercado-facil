package br.com.mercadofacil.updater

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.work.*
import br.com.mercadofacil.BuildConfig
import br.com.mercadofacil.MercadoApp
import br.com.mercadofacil.MainActivity
import br.com.mercadofacil.domain.SemVersion
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class Release(val version: String, val notes: String, val name: String, val apkUrl: String, val checksumUrl: String, val repository: String, val tag: String)
class Updater(private val context: Context) {
    private val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
    private fun request(url: String): Request = Request.Builder().url(url).header("Accept", "application/vnd.github+json").header("User-Agent", "MercadoFacil/${BuildConfig.VERSION_NAME}").build()
    suspend fun check(force: Boolean = false): Release? = withContext(Dispatchers.IO) {
        val settings = (context.applicationContext as MercadoApp).settings
        val config = settings.get()
        require(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+").matches(config.repository)) { "Configure o repositório GitHub (usuário/repositório)" }
        if (!force && System.currentTimeMillis() - config.lastCheck < config.intervalHours * 3600000L) return@withContext null
        val body = client.newCall(request("https://api.github.com/repos/${config.repository}/releases/latest")).execute().use {
            require(it.isSuccessful) { "GitHub: HTTP ${it.code}" }; requireNotNull(it.body).string()
        }
        val json = JSONObject(body)
        require(!json.optBoolean("draft") && !json.optBoolean("prerelease"))
        val tag = json.getString("tag_name")
        val version = SemVersion.parse(tag)
        settings.checked()
        if (version <= SemVersion.parse(BuildConfig.VERSION_NAME)) return@withContext null
        val assets = json.getJSONArray("assets")
        val expected = "mercado-facil-${tag.removePrefix("v")}.apk"
        fun asset(name: String): JSONObject = (0 until assets.length()).map { assets.getJSONObject(it) }.singleOrNull { it.getString("name") == name } ?: error("Release sem $name")
        val apk = asset(expected)
        require(apk.getString("content_type") in listOf("application/vnd.android.package-archive", "application/octet-stream"))
        val checksum = asset("$expected.sha256")
        val apkUrl = apk.getString("browser_download_url")
        val checksumUrl = checksum.getString("browser_download_url")
        require(validAssetUrl(apkUrl, config.repository, tag, expected) && validAssetUrl(checksumUrl, config.repository, tag, "$expected.sha256")) { "Origem da atualização inválida" }
        Release(tag.removePrefix("v"), json.optString("body"), expected, apkUrl, checksumUrl, config.repository, tag)
    }
    suspend fun download(release: Release, progress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "update.apk")
        try {
            val checksumText = client.newCall(request(release.checksumUrl)).execute().use {
                require(it.isSuccessful); requireNotNull(it.body).string().also { value -> require(value.length < 1024) }
            }
            val expected = checksumText.trim().split(Regex("\\s+"))[0].lowercase()
            require(Regex("[a-f0-9]{64}").matches(expected)) { "Checksum inválido" }
            val call = client.newCall(request(release.apkUrl))
            val cancel = coroutineContext[Job]?.invokeOnCompletion { cause -> if (cause != null) call.cancel() }
            try {
                call.execute().use { response ->
                    require(response.isSuccessful) { "Download: HTTP ${response.code}" }
                    val body = requireNotNull(response.body)
                    val total = body.contentLength()
                    require(total in 1..250_000_000L) { "Tamanho de APK inválido" }
                    body.byteStream().use { input -> file.outputStream().use { output ->
                        val buffer = ByteArray(65536)
                        var done = 0L
                        while (true) {
                            ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            done += count
                            require(done <= total) { "Download excedeu o tamanho esperado" }
                            output.write(buffer, 0, count)
                            progress(done.toFloat() / total)
                        }
                        require(done == total) { "Download incompleto" }
                    } }
                }
            } finally { cancel?.dispose() }
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input -> val buffer = ByteArray(65536); while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) } }
            require(digest.digest().joinToString("") { "%02x".format(it) } == expected) { "SHA-256 divergente" }
            validateApk(file)
            file
        } catch (e: Exception) { file.delete(); throw e }
    }
    @Suppress("DEPRECATION")
    private fun validateApk(file: File) {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val archive = requireNotNull(pm.getPackageArchiveInfo(file.absolutePath, flags)) { "APK inválido" }
        val installed = pm.getPackageInfo(context.packageName, flags)
        require(archive.packageName == context.packageName) { "Aplicativo inesperado" }
        val code = if (Build.VERSION.SDK_INT >= 28) archive.longVersionCode else archive.versionCode.toLong()
        require(code > BuildConfig.VERSION_CODE) { "versionCode deve ser crescente" }
        val old = if (Build.VERSION.SDK_INT >= 28) installed.signingInfo!!.apkContentsSigners else installed.signatures!!
        val new = if (Build.VERSION.SDK_INT >= 28) archive.signingInfo!!.apkContentsSigners else archive.signatures!!
        require(old.map { it.toCharsString() }.toSet() == new.map { it.toCharsString() }.toSet()) { "Assinatura incompatível" }
    }
    fun install(activity: Activity, file: File): Boolean {
        if (!context.packageManager.canRequestPackageInstalls()) {
            activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
            return false
        }
        validateApk(file)
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
        }
        val id = installer.createSession(params)
        try {
            installer.openSession(id).use { session ->
                file.inputStream().use { input -> session.openWrite("base.apk", 0, file.length()).use { out -> input.copyTo(out); session.fsync(out) } }
                val intent = Intent(context, InstallReceiver::class.java)
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                session.commit(PendingIntent.getBroadcast(context, id, intent, flags).intentSender)
            }
        } catch (e: Exception) { installer.abandonSession(id); throw e }
        return true
    }
    companion object {
        fun validAssetUrl(url: String, repo: String, tag: String, file: String): Boolean {
            val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return false
            return uri.scheme == "https" && uri.host == "github.com" && uri.userInfo == null && uri.query == null && uri.fragment == null && uri.path == "/$repo/releases/download/$tag/$file"
        }
    }
}
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE) == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @Suppress("DEPRECATION") val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
            confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let { context.startActivity(it) }
        } else {
            val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
            if (status != PackageInstaller.STATUS_SUCCESS) android.widget.Toast.makeText(context, "Instalação não concluída: ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: status}", android.widget.Toast.LENGTH_LONG).show()
        }
    }
}
fun notifyUser(context: Context, title: String, text: String, id: Int) {
    if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
    val manager = context.getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(NotificationChannel("mercado", "Mercado Fácil", NotificationManager.IMPORTANCE_DEFAULT))
    val open = PendingIntent.getActivity(context, id, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    manager.notify(id, NotificationCompat.Builder(context, "mercado").setSmallIcon(android.R.drawable.ic_menu_agenda).setContentTitle(title).setContentText(text).setContentIntent(open).setAutoCancel(true).build())
}
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        val app = applicationContext as MercadoApp
        if (app.settings.get().repository.isNotBlank()) {
            val release = Updater(app).check()
            if (release != null && app.settings.get().notifications) notifyUser(app, "Atualização disponível", "Mercado Fácil ${release.version}. Abra as configurações para instalar.", 10)
        }
        Result.success()
    } catch (e: Exception) { if (e is CancellationException) throw e; Result.retry() }
}

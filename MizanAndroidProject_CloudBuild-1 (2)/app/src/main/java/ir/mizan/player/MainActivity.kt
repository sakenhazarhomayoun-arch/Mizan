package ir.mizan.player

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private val audioPermission: String
        get() = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
                else Manifest.permission.READ_EXTERNAL_STORAGE
    private val requestCodeAudio = 2201

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = true
        webView.settings.mediaPlaybackRequiresUserGesture = true
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (checkSelfPermissionCompat(audioPermission) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(audioPermission), requestCodeAudio)
                } else {
                    webView.postDelayed({ webView.evaluateJavascript("window.refreshAndroidLibrary && window.refreshAndroidLibrary();", null) }, 250)
                }
            }
        }
        webView.addJavascriptInterface(AndroidMusicBridge(), "AndroidMusic")
        setContentView(webView)
        webView.loadUrl("file:///android_asset/index.html")
    }

    private fun checkSelfPermissionCompat(permission: String): Int {
        return if (Build.VERSION.SDK_INT >= 23) checkSelfPermission(permission) else PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == requestCodeAudio && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            webView.postDelayed({ webView.evaluateJavascript("window.refreshAndroidLibrary && window.refreshAndroidLibrary();", null) }, 250)
        }
    }

    private inner class AndroidMusicBridge {
        @JavascriptInterface
        fun getSongs(): String {
            if (checkSelfPermissionCompat(audioPermission) != PackageManager.PERMISSION_GRANTED) return "[]"
            val result = JSONArray()
            val collection: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.IS_MUSIC
            )
            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
            val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
            var cursor: Cursor? = null
            try {
                cursor = contentResolver.query(collection, projection, selection, null, sortOrder)
                cursor?.use { c ->
                    val idColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val titleColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val nameColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                    while (c.moveToNext()) {
                        val id = c.getLong(idColumn)
                        val itemUri = Uri.withAppendedPath(collection, id.toString())
                        val item = JSONObject()
                        item.put("id", id.toString())
                        item.put("title", c.getString(titleColumn) ?: "")
                        item.put("displayName", c.getString(nameColumn) ?: "")
                        item.put("uri", itemUri.toString())
                        result.put(item)
                    }
                }
            } catch (_: Exception) {
                return "[]"
            } finally {
                cursor?.close()
            }
            return result.toString()
        }
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.removeJavascriptInterface("AndroidMusic")
            webView.destroy()
        }
        super.onDestroy()
    }
}

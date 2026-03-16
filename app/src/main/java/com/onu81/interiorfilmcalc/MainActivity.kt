package com.onu81.interiorfilmcalc

import android.app.DownloadManager
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.annotation.Keep
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        webView = findViewById(R.id.webView)
        setupWebView()
        webView.loadUrl("file:///android_asset/index.html")

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    showExitDialog()
                }
            }
        })
    }

    private fun setupWebView() {
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = WebViewClient()
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            @Suppress("DEPRECATION")
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            textZoom = 100
        }
        // HTML의 window.Android와 연결
        webView.addJavascriptInterface(WebAppInterface(this), "Android")
    }

    private fun showExitDialog() {
        AlertDialog.Builder(this)
            .setTitle("종료 확인")
            .setMessage("작업을 중단하고 앱을 종료하시겠습니까?")
            .setPositiveButton("종료") { _, _ -> finish() }
            .setNegativeButton("취소", null)
            .show()
    }

    @Keep
    inner class WebAppInterface(private val mContext: Context) {

        @JavascriptInterface
        fun shareToKakao(base64Url: String) {
            try {
                val bitmap = decodeBase64(base64Url) ?: return
                val imagesFolder = File(mContext.cacheDir, "images")
                if (!imagesFolder.exists()) imagesFolder.mkdirs()
                val file = File(imagesFolder, "temp_result.png")
                
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }

                val contentUri = FileProvider.getUriForFile(
                    mContext, "${mContext.packageName}.fileprovider", file
                )

                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    clipData = ClipData.newRawUri("", contentUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                mContext.startActivity(Intent.createChooser(intent, "결과 공유하기"))
            } catch (e: Exception) {
                showToast("공유 실패: ${e.localizedMessage}")
            }
        }

        @JavascriptInterface
        fun saveToGallery(base64Url: String) {
            try {
                val bitmap = decodeBase64(base64Url) ?: return
                val fileName = "Film_${System.currentTimeMillis()}.png"
                val outputStream: OutputStream?
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/FilmCalculator")
                    }
                    val uri = mContext.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    outputStream = uri?.let { mContext.contentResolver.openOutputStream(it) }
                } else {
                    val path = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "FilmCalculator")
                    if (!path.exists()) path.mkdirs()
                    outputStream = FileOutputStream(File(path, fileName))
                }

                outputStream?.use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    showToast("갤러리에 저장되었습니다.")
                }
            } catch (e: Exception) {
                showToast("저장 실패: ${e.localizedMessage}")
            }
        }

        private fun decodeBase64(url: String): Bitmap? {
            val data = if (url.contains(",")) url.substring(url.indexOf(",") + 1) else url
            val bytes = Base64.decode(data, Base64.DEFAULT)
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }

        private fun showToast(msg: String) {
            Handler(Looper.getMainLooper()).post { Toast.makeText(mContext, msg, Toast.LENGTH_SHORT).show() }
        }
    }
}

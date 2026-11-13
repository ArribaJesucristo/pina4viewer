package com.bone.android.a4v.oficial.ui.view

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.bone.android.a4v.oficial.databinding.ActivityWebBrowserBinding
import com.bone.android.a4v.oficial.util.StreamLauncher

class WebBrowserActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWebBrowserBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWebBrowserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val rawUrl = intent.getStringExtra(EXTRA_URL)?.trim().orEmpty()
        val safeUrl = if (rawUrl.startsWith("https://", ignoreCase = true) || rawUrl.startsWith("http://", ignoreCase = true)) {
            rawUrl
        } else {
            DEFAULT_URL
        }

        setupBackNavigation()
        setupWebView()
        binding.webView.loadUrl(safeUrl)
        binding.webView.requestFocus()
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.webView.canGoBack()) {
                    binding.webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        binding.webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            allowFileAccess = false
            allowContentAccess = false
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        }

        binding.webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress < 100) {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.progressBar.progress = newProgress
                } else {
                    binding.progressBar.visibility = View.GONE
                }
            }
        }

        binding.webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                return handleCustomSchemeUrl(url)
            }

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                return handleCustomSchemeUrl(url ?: return false)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                binding.progressBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun handleCustomSchemeUrl(url: String): Boolean {
        return when {
            url.startsWith("acestream://", ignoreCase = true) -> {
                StreamLauncher.launchAceStream(this, url)
                true
            }
            url.startsWith("sop://", ignoreCase = true) -> {
                StreamLauncher.launchSopCast(this, url)
                true
            }
            url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true) -> false
            else -> {
                Toast.makeText(this, "Esquema externo bloqueado por seguridad.", Toast.LENGTH_SHORT).show()
                true
            }
        }
    }

    override fun onResume() {
        super.onResume()
        binding.webView.onResume()
    }

    override fun onPause() {
        binding.webView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        try {
            binding.webView.stopLoading()
            binding.webView.loadUrl("about:blank")
            (binding.webView.parent as? ViewGroup)?.removeView(binding.webView)
            binding.webView.removeAllViews()
            binding.webView.destroy()
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "search_url"
        private const val DEFAULT_URL = "https://search-ace.stream/"

        fun start(context: Context, url: String) {
            val intent = Intent(context, WebBrowserActivity::class.java).apply {
                putExtra(EXTRA_URL, url)
            }
            context.startActivity(intent)
        }
    }
}

package com.msegal.tunegame.mobile

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.net.Uri
import android.graphics.Color
import android.view.ViewGroup
import android.webkit.*
import android.widget.*

/** Native installation shell; all quiz rules and screens remain in the existing web app. */
class MainActivity : Activity() {
    private var web: WebView? = null

    private val preferences by lazy { getSharedPreferences("server", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setup()
    }

    private fun setup() {
        web?.destroy(); web = null
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 64, 32, 32); setBackgroundColor(Color.rgb(13, 17, 15)) }
        val title = TextView(this).apply { text = "TuneGame"; textSize = 32f; setTextColor(Color.rgb(29,185,84)) }
        val helpText = TextView(this).apply { text = "Play the main game or join friends in a private room. Enter your TuneGame server address."; textSize = 16f; setPadding(0,24,0,24) }
        val input = EditText(this).apply {
            hint = "https://your-server.example"; contentDescription = "Private server address"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            setText(preferences.getString("url", "")); setSingleLine(true)
        }
        val error = TextView(this).apply { setTextColor(Color.rgb(255,140,140)); accessibilityLiveRegion = android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE }
        fun openGame(path: String) {
            val uri = Uri.parse(input.text.toString().trim())
            val localDebug = BuildConfig.DEBUG && uri.scheme == "http" && uri.host in listOf("10.0.2.2", "127.0.0.1", "localhost")
            if ((uri.scheme != "https" && !localDebug) || uri.host.isNullOrBlank() || uri.userInfo != null || uri.query != null || uri.fragment != null || uri.path !in listOf("", "/")) {
                error.text = "Enter an HTTPS server address without a path. Debug builds also allow the local emulator server."; return
            }
            val base = uri.buildUpon().path("").build()
            preferences.edit().putString("url", base.toString()).apply()
            showGame(base, path)
        }
        val mainGame = Button(this).apply { text = "Main game"; setOnClickListener { openGame("/play/") } }
        val rooms = Button(this).apply { text = "Private rooms"; setOnClickListener { openGame("/rooms/") } }
        listOf(title,helpText,input,mainGame,rooms,error).forEach(panel::addView)
        val scroll = ScrollView(this).apply { addView(panel) }
        setContentView(scroll)
        applyInsets(scroll)
    }

    private fun showGame(base: Uri, path: String) {
        web?.destroy(); web = null
        val shell = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val toolbar = LinearLayout(this)
        val settingsButton = Button(this).apply { text = "Server"; setOnClickListener { setup() } }
        val retry = Button(this).apply { text = "Reload"; setOnClickListener { web?.reload() } }
        val games = Button(this).apply { text = "Games"; setOnClickListener {
            AlertDialog.Builder(this@MainActivity).setTitle("Choose a game")
                .setItems(arrayOf("Main game", "Private rooms")) { _, which ->
                    AlertDialog.Builder(this@MainActivity).setTitle("Switch games?")
                        .setMessage("The current screen will restart. Leave your private room first if you want to leave it.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Switch") { _, _ -> showGame(base, if (which == 0) "/play/" else "/rooms/") }.show()
                }.show()
        } }
        toolbar.addView(settingsButton); toolbar.addView(games); toolbar.addView(retry); shell.addView(toolbar)
        val error = TextView(this).apply { visibility = android.view.View.GONE; setPadding(16,8,16,8); accessibilityLiveRegion = android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE }
        shell.addView(error)
        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val u = request.url
                    return u.scheme != base.scheme || u.host != base.host || u.port != base.port || !(u.path in listOf("/rooms", "/play") || u.path.orEmpty().startsWith("/rooms/") || u.path.orEmpty().startsWith("/play/"))
                }
                override fun onPageFinished(view: WebView, url: String) { CookieManager.getInstance().flush() }
                override fun onReceivedError(view: WebView, request: WebResourceRequest, failure: WebResourceError) {
                    if (request.isForMainFrame) { error.text = "Cannot connect to the game server. Check the address and connection, then reload."; error.visibility = android.view.View.VISIBLE }
                }
                override fun onPageStarted(view: WebView, url: String, icon: android.graphics.Bitmap?) { error.visibility = android.view.View.GONE }
                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                    if(request.isForMainFrame) { error.text = "Server returned ${response.statusCode}. Check the address or reload."; error.visibility = android.view.View.VISIBLE }
                }
            }
            loadUrl(base.buildUpon().path(path).build().toString())
        }
        shell.addView(web, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f))
        setContentView(shell); applyInsets(shell)
    }
    private fun applyInsets(view: android.view.View) {
        view.setOnApplyWindowInsetsListener { v, insets ->
            @Suppress("DEPRECATION")
            v.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            insets
        }
    }
    override fun onPause() { web?.onPause(); super.onPause() }
    override fun onResume() { super.onResume(); web?.onResume() }
    override fun onDestroy() { web?.destroy(); web=null; super.onDestroy() }
}

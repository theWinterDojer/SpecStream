@file:Suppress("SpellCheckingInspection") // Contains brand names and technical terms

package com.dojer.specstream

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.webkit.*
import android.widget.ProgressBar

import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.isGone

import java.io.ByteArrayInputStream

class MainActivity : AppCompatActivity() {
    
    private lateinit var playerWebView: WebView
    private lateinit var guideWebView: WebView
    private lateinit var loadingProgress: ProgressBar
    
    private val guideUrl = "https://watch.spectrum.net/guide"
    private val allowedHosts = setOf("spectrum.net", "spectrum.com")
    private val blockedAnalyticsHosts = setOf(
        "collector.pi.spectrum.net",
        "imrworldwide.com",
        "online-metrix.net",
        "medallia.com"
    )
    
    // Modern desktop user agent for better compatibility
    private val desktopUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:147.0) Gecko/20100101 Firefox/147.0"
    
    // Track guide states for D-pad navigation
    private var guideLoaded = false
    
    // Session-only channel memory - resets when app is fully closed
    private var currentChannelUrl: String? = null
    
    // Track back button state for clean exit behavior
    private var lastBackPressTime = 0L
    private val BACK_PRESS_TIME_INTERVAL = 2000L // 2 seconds
    


    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        // Reset from splash theme to normal theme
        setTheme(R.style.Theme_SpecStream)
        
        super.onCreate(savedInstanceState)
        
        // Configure fullscreen and TV display settings
        setupFullscreenDisplay()
        
        setContentView(R.layout.activity_main)
        
        // Initialize views
        playerWebView = findViewById(R.id.webview_player)
        guideWebView = findViewById(R.id.webview_guide)
        loadingProgress = findViewById(R.id.loading_progress)
        
        // Setup splash screen with fade-out animation
        val splashOverlay: View = findViewById(R.id.splash_overlay)
        val fadeOut = AnimationUtils.loadAnimation(this, R.anim.fade_out)
        
        // Show splash screen for 1 second, then fade out
        splashOverlay.postDelayed({
            splashOverlay.startAnimation(fadeOut)
            fadeOut.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) {}
                override fun onAnimationRepeat(animation: Animation?) {}
                override fun onAnimationEnd(animation: Animation?) {
                    splashOverlay.visibility = View.GONE
                }
            })
        }, 1000) // 1 second delay before fade-out starts
        
        // Views initialized successfully
        
        try {
            setupPlayerWebView()
            setupGuideWebView()
            loadSpectrumSite()
        } catch (e: Exception) {
            Log.e("SpecStream", "Error initializing WebViews: ${e.message}", e)
            // Show error message instead of crashing
            loadingProgress.visibility = View.GONE
        }
        
        // Add JavaScript interface for SpecStream functionality
        playerWebView.addJavascriptInterface(this, "SpecStream")
        guideWebView.addJavascriptInterface(this, "SpecStream")
        
        // Enable cookie persistence for better session handling
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(playerWebView, true)
            setAcceptThirdPartyCookies(guideWebView, true)
        }
        
        Log.d("SpecStream", "Activity setup complete - ready for D-pad events")
    }
    
    private fun setupFullscreenDisplay() {
        Log.d("SpecStream", "Setting up basic fullscreen display")
        
        // Keep screen on during video playback
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        // Basic fullscreen - less aggressive than before
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        )
        
        Log.d("SpecStream", "Fullscreen display setup complete")
    }
    
    @SuppressLint("SetJavaScriptEnabled")
    private fun setupPlayerWebView() {
        Log.d("SpecStream", "Setting up player WebView...")
        
        // Check if WebView is available
        try {
            WebView.getCurrentWebViewPackage()
        } catch (e: Exception) {
            Log.e("SpecStream", "WebView not available: ${e.message}")
            throw Exception("WebView not available on this device")
        }
        
        // Set background to black to prevent white flash during loading
        playerWebView.setBackgroundColor(android.graphics.Color.BLACK)
        
        configureWebViewSettings(playerWebView)
        playerWebView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_BOUND, true)
        Log.d("SpecStream", "Renderer priority policy set for player WebView")
        
        // Set WebView client for handling page navigation
        playerWebView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                loadingProgress.visibility = View.VISIBLE
            }

            override fun onPageCommitVisible(view: WebView?, url: String?) {
                super.onPageCommitVisible(view, url)
                injectUiCleanupScript()
            }
            
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                loadingProgress.visibility = View.GONE
                Log.d("SpecStream", "Player loaded successfully")
                
                // Inject UI cleanup script with built-in login detection delay
                injectUiCleanupScript()
                
                // Guide will be preloaded automatically via JavaScript when video is found
            }

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                return maybeBlockAnalyticsRequest(request) ?: super.shouldInterceptRequest(view, request)
            }
            
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return shouldOverrideNavigation(request?.url)
            }

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                return shouldOverrideNavigation(url?.toUri())
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error)
                
                val url = request?.url.toString()
                if (!url.contains("collector.pi.spectrum.net")
                    && !url.contains("imrworldwide.com")
                    && !url.contains("online-metrix.net")
                    && !url.contains("medallia.com")
                ) {
                    Log.e("SpecStream", "Player error: ${error?.description} for ${request?.url}")
                }
                loadingProgress.visibility = View.GONE
            }
        }
        
        // Set WebChromeClient for handling JavaScript dialogs and media
        playerWebView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                if (newProgress == 100) {
                    loadingProgress.visibility = View.GONE
                }
            }
            
            override fun onPermissionRequest(request: PermissionRequest?) {
                request?.grant(request.resources)
            }
        }
        
        // Performance optimization for TV apps
        playerWebView.setVerticalScrollBarEnabled(false)
        
        // Load initial page
    }
    
    @SuppressLint("SetJavaScriptEnabled")
    private fun setupGuideWebView() {
        Log.d("SpecStream", "Setting up guide WebView...")
        
        configureWebViewSettings(guideWebView)
        
        // Performance optimizations for TV display
        guideWebView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        // Use hardware acceleration for smoother rendering
        guideWebView.setLayerType(WebView.LAYER_TYPE_HARDWARE, null)
        
        // Set WebView client for guide
        guideWebView.webViewClient = object : WebViewClient() {
            override fun onPageCommitVisible(view: WebView?, url: String?) {
                super.onPageCommitVisible(view, url)
                injectGuideCleanupScript()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (!guideLoaded) {
                    guideLoaded = true
                    Log.d("SpecStream", "Guide loaded successfully")
                }
                if (guideLoaded && guideWebView.isGone) {
                    guideWebView.onPause()
                }
                
                // Inject guide-specific UI cleanup
                injectGuideCleanupScript()
            }

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                return maybeBlockAnalyticsRequest(request) ?: super.shouldInterceptRequest(view, request)
            }
 
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return shouldOverrideNavigation(request?.url)
            }


            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                return shouldOverrideNavigation(url?.toUri())
            }
        }
        
        // Set WebChromeClient for guide
        guideWebView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest?) {
                request?.grant(request.resources)
            }
        }
        
        // Guide WebView starts hidden
        guideWebView.visibility = View.GONE
    }
    
    private fun configureWebViewSettings(webView: WebView) {
        webView.settings.apply {
            @Suppress("SetJavaScriptEnabled") // Required for Spectrum TV functionality
            javaScriptEnabled = true
            domStorageEnabled = true
            userAgentString = desktopUserAgent
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            
            // Enable caching to avoid re-downloading resources
            cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
            
            // REMOVED: These zoom/viewport settings can interfere with video streaming
            // setSupportZoom(true)
            // builtInZoomControls = true
            // displayZoomControls = false
            // loadWithOverviewMode = true
            // useWideViewPort = true
            
            // REMOVED: File access not needed for streaming, potential security issue
            // allowFileAccess = true
            // allowContentAccess = true
        }
    }
    
    private fun loadSpectrumSite() {
        if (currentChannelUrl != null) {
            Log.d("SpecStream", "Resuming session channel: $currentChannelUrl")
            playerWebView.loadUrl(currentChannelUrl!!)
        } else {
            Log.d("SpecStream", "Loading fresh Spectrum session")
            playerWebView.loadUrl("https://watch.spectrum.net/")
        }
    }

    private fun isAllowedHost(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        val normalizedHost = host.lowercase()
        return allowedHosts.any { base ->
            normalizedHost == base || normalizedHost.endsWith(".$base")
        }
    }

    private fun isBlockedAnalyticsHost(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        val normalizedHost = host.lowercase()
        return blockedAnalyticsHosts.any { base ->
            normalizedHost == base || normalizedHost.endsWith(".$base")
        }
    }

    private fun isAllowedUrl(uri: Uri?): Boolean {
        if (uri == null) return false
        val scheme = uri.scheme?.lowercase() ?: return false
        return when (scheme) {
            "https" -> isAllowedHost(uri.host)
            "about", "data" -> true
            "blob" -> {
                val inner = uri.schemeSpecificPart ?: return false
                val innerUri = inner.removePrefix("//").toUri()
                innerUri.scheme?.lowercase() == "https" && isAllowedHost(innerUri.host)
            }
            else -> false
        }
    }

    private fun shouldOverrideNavigation(url: Uri?): Boolean {
        if (isAllowedUrl(url)) return false
        Log.w("SpecStream", "Blocked navigation to disallowed URL: $url")
        return true
    }

    private fun maybeBlockAnalyticsRequest(request: WebResourceRequest?): WebResourceResponse? {
        val url = request?.url ?: return null
        if (!isBlockedAnalyticsHost(url.host)) return null
        Log.d("SpecStream", "Blocked analytics request: $url")
        return WebResourceResponse(
            "text/plain",
            "utf-8",
            204,
            "No Content",
            emptyMap(),
            ByteArrayInputStream(ByteArray(0))
        )
    }
    
    private fun saveLastChannel(channelId: String) {
        currentChannelUrl = "https://watch.spectrum.net/livetv?tmsid=$channelId"
        Log.d("SpecStream", "Channel remembered for this session: $currentChannelUrl")
    }

    private fun loadRawScript(resId: Int): String {
        return resources.openRawResource(resId).bufferedReader().use { it.readText() }
    }
    
    private fun injectUiCleanupScript() {
        Log.d("SpecStream", "Injecting UI cleanup for TV interface...")
        
        val uiCleanupScript = loadRawScript(R.raw.player_cleanup)
        
        playerWebView.evaluateJavascript(uiCleanupScript) { result ->
            Log.d("SpecStream", "UI cleanup script injected: $result")
        }
    }
    
    private fun injectGuideCleanupScript() {
        Log.d("SpecStream", "Injecting guide cleanup script...")
        
        val guideCleanupScript = loadRawScript(R.raw.guide_cleanup)
        
        guideWebView.evaluateJavascript(guideCleanupScript) { result ->
            Log.d("SpecStream", "Guide cleanup script injected: $result")
        }
    }

    
    // Override dispatchKeyEvent for comprehensive TV remote/D-pad handling
    // Note: While Android recommends OnBackPressedCallback for back button handling,
    // we use dispatchKeyEvent because:
    // 1. TV apps require complex D-pad navigation intercepted BEFORE WebViews process events
    // 2. Guide overlay control requires intercepting events before WebView processing
    // 3. JavaScript bridge communication requires coordinated key event handling
    // 4. OnBackPressedCallback alone cannot handle D-pad Up/Down/Left/Right events
    // Track if login interface is currently active
    private var isLoginInterfaceActive = false
    
    @SuppressLint("GestureBackNavigation")
    @Suppress("OVERRIDE_DEPRECATION")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Handle D-pad key events for TV navigation
        if (event.action == KeyEvent.ACTION_DOWN) {
            Log.d("SpecStream", "dispatchKeyEvent: ${event.keyCode} (guide visible: ${!guideWebView.isGone}) (login active: $isLoginInterfaceActive)")
            
            // Handle login interface navigation if active
            if (isLoginInterfaceActive) {
                Log.d("SpecStream", "Login interface active, handling D-pad navigation")
                handleLoginKeyEvent(event)
                return true
            }
            
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (guideWebView.isGone) {
                        Log.d("SpecStream", "D-pad Up/Down: Showing guide via JavaScript")
                        // Trigger guide display via JavaScript bridge
                        playerWebView.evaluateJavascript("toggleGuide('SHOWGUIDE');", null)
                        return true
                    }
                }
                
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (!guideWebView.isGone) {
                        Log.d("SpecStream", "D-pad Left: Navigating within guide")
                        // Pass the event to the guide WebView for navigation
                        return guideWebView.dispatchKeyEvent(event)
                    }
                    // When watching video (guide closed), LEFT does nothing
                    Log.d("SpecStream", "D-pad Left: Ignored while watching video")
                    return true
                }
                
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (!guideWebView.isGone) {
                        Log.d("SpecStream", "D-pad Right: Navigating within guide")
                        // Pass the event to the guide WebView for navigation
                        return guideWebView.dispatchKeyEvent(event)
                    }
                    // When watching video (guide closed), RIGHT does nothing
                    Log.d("SpecStream", "D-pad Right: Ignored while watching video")
                    return true
                }
                
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    // Pass to currently active WebView
                    return if (!guideWebView.isGone) {
                        guideWebView.dispatchKeyEvent(event)
                    } else {
                        playerWebView.dispatchKeyEvent(event)
                    }
                }
                
                KeyEvent.KEYCODE_BACK -> {
                    Log.d("SpecStream", "Back button pressed: guide visible=${!guideWebView.isGone}")
                    
                    if (!guideWebView.isGone) {
                        Log.d("SpecStream", "Back: Hiding guide via JavaScript")
                        if (guideWebView.canGoBack()) {
                            guideWebView.evaluateJavascript("history.back();", null)
                        } else {
                            playerWebView.evaluateJavascript("toggleGuide('HIDEGUIDE');", null)
                        }
                        return true
                    }
                    
                    // Double-back to exit when watching video (no channel navigation)
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastBackPressTime < BACK_PRESS_TIME_INTERVAL) {
                        Log.d("SpecStream", "Back: Double-back detected, closing app")
                        finish()
                    } else {
                        Log.d("SpecStream", "Back: First back press, staying in video")
                        lastBackPressTime = currentTime
                    }
                    return true
                }
                
                // Media control keys for direct video control
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                    Log.d("SpecStream", "Media: Play/Pause")
                    playerWebView.evaluateJavascript("""
                        var video = document.querySelector('video');
                        if (video) {
                            if (video.paused) video.play(); else video.pause();
                        }
                    """, null)
                    return true
                }
                
                KeyEvent.KEYCODE_MEDIA_PLAY -> {
                    Log.d("SpecStream", "Media: Play")
                    playerWebView.evaluateJavascript("""
                        var video = document.querySelector('video');
                        if (video && video.paused) video.play();
                    """, null)
                    return true
                }
                
                KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                    Log.d("SpecStream", "Media: Pause")
                    playerWebView.evaluateJavascript("""
                        var video = document.querySelector('video');
                        if (video && !video.paused) video.pause();
                    """, null)
                    return true
                }
            }
        }
        
        return super.dispatchKeyEvent(event)
    }
    
    // JavaScript interface methods for SpecStream functionality
    @JavascriptInterface
    @Suppress("unused") // Called from injected JavaScript code
    fun channelGuide(action: String) {
        when (action) {
            "SHOWGUIDE" -> {
                try {
                    runOnUiThread {
                        Log.d("SpecStream", "JavaScript: Showing guide")
                        guideWebView.visibility = View.VISIBLE
                        guideWebView.onResume()
                        guideWebView.requestFocus()
                        resetBackPressTimer() // Reset back press timer when guide opens
                    }
                } catch (e: Exception) {
                    Log.e("SpecStream", "Error showing guide: $e")
                }
            }
            "HIDEGUIDE" -> {
                try {
                    runOnUiThread {
                        Log.d("SpecStream", "JavaScript: Hiding guide")
                        guideWebView.visibility = View.GONE
                        pauseGuideTimersIfLoaded()
                    }
                } catch (e: Exception) {
                    Log.e("SpecStream", "Error hiding guide: $e")
                }
            }
        }
    }
    
    @JavascriptInterface
    @Suppress("unused") // Called from injected JavaScript code  
    fun navToChannel(channelId: String) {
        try {
            runOnUiThread {
                Log.d("SpecStream", "Channel switch to $channelId")
                Log.d("SpecStream", "Using reliable page reload for channel switching")
                playerWebView.loadUrl("https://watch.spectrum.net/livetv?tmsid=$channelId")
                playerWebView.clearHistory() // Prevent back navigation between channels
                
                // Save the new channel for memory
                saveLastChannel(channelId)
                
                // Hide guide and return to video
                guideWebView.visibility = View.GONE
                pauseGuideTimersIfLoaded()
            }
        } catch (e: Exception) {
            Log.e("SpecStream", "Error in channel navigation: $e")
        }
    }
    
    @JavascriptInterface
    @Suppress("unused") // Called from injected JavaScript code
    fun preloadGuide() {
        try {
            runOnUiThread {
                if (!guideLoaded) {
                    Log.d("SpecStream", "JavaScript: Preloading guide")
                    guideWebView.loadUrl(guideUrl)
                }
            }
        } catch (e: Exception) {
            Log.e("SpecStream", "Error preloading guide: $e")
        }
    }
    
    private fun resetBackPressTimer() {
        lastBackPressTime = 0L
    }

    private fun pauseGuideTimersIfLoaded() {
        if (guideLoaded) {
            guideWebView.onPause()
        }
    }
    
    @SuppressLint("GestureBackNavigation")
    private fun handleLoginKeyEvent(event: KeyEvent) {
        Log.d("SpecStream", "handleLoginKeyEvent: ${event.keyCode} action=${event.action}")
        
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                Log.d("SpecStream", "Login: D-pad UP - moving cursor up")
                playerWebView.evaluateJavascript("handleLoginNavigation('UP');", null)
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                Log.d("SpecStream", "Login: D-pad DOWN - moving cursor down")
                playerWebView.evaluateJavascript("handleLoginNavigation('DOWN');", null)
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                Log.d("SpecStream", "Login: CENTER - selecting current element")
                playerWebView.evaluateJavascript("handleLoginNavigation('SELECT');", null)
            }
            KeyEvent.KEYCODE_BACK -> {
                Log.d("SpecStream", "Login: BACK - canceling login")
                playerWebView.evaluateJavascript("cancelLogin();", null)
            }
            // Let LEFT/RIGHT and other keys pass through to the WebView for native keyboard navigation
        }
    }
    

    
    @JavascriptInterface
    @Suppress("unused") // Called from injected JavaScript code
    fun cancelLogin() {
        try {
            runOnUiThread {
                Log.d("SpecStream", "Login canceled by user")
                isLoginInterfaceActive = false
                
                val cancelScript = """
                    if (window.cancelLogin) {
                        window.cancelLogin();
                    }
                """
                
                playerWebView.evaluateJavascript(cancelScript, null)
            }
        } catch (e: Exception) {
            Log.e("SpecStream", "Error in cancelLogin: $e")
        }
    }
    
    @JavascriptInterface
    @Suppress("unused") // Called from injected JavaScript code
    fun setLoginInterfaceActive(active: Boolean) {
        try {
            runOnUiThread {
                isLoginInterfaceActive = active
                Log.d("SpecStream", "Login interface active state changed to: $active")
                // No automatic keyboard triggering - user controls with cursor
            }
        } catch (e: Exception) {
            Log.e("SpecStream", "Error in setLoginInterfaceActive: $e")
        }
    }
    

    
    override fun onResume() {
        super.onResume()
        playerWebView.onResume()
        if (!guideWebView.isGone) {
            guideWebView.onResume()
        } else {
            pauseGuideTimersIfLoaded()
        }
        
        // Resume video playback if it was playing before pause
        playerWebView.evaluateJavascript("""
            if (window.wasPlayingBeforePause) {
                var video = document.querySelector('video');
                if (video && video.paused) {
                    video.play();
                    console.log('SpecStream: Resumed video playback');
                }
                window.wasPlayingBeforePause = false;
            }
        """.trimIndent(), null)
    }
    
    override fun onPause() {
        super.onPause()
        
        // Pause video playback when app goes to background (Home button)
        playerWebView.evaluateJavascript("""
            var video = document.querySelector('video');
            if (video && !video.paused) {
                video.pause();
                window.wasPlayingBeforePause = true;
                console.log('SpecStream: Paused video for background');
            } else {
                window.wasPlayingBeforePause = false;
            }
        """.trimIndent(), null)
        
        playerWebView.onPause()
        guideWebView.onPause()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        playerWebView.destroy()
        guideWebView.destroy()
    }
}

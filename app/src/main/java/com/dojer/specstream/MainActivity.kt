@file:Suppress("SpellCheckingInspection") // Contains brand names and technical terms

package com.dojer.specstream

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.*
import android.widget.ProgressBar
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isGone

class MainActivity : AppCompatActivity() {
    
    private lateinit var playerWebView: WebView
    private lateinit var guideWebView: WebView
    private lateinit var loadingProgress: ProgressBar
    
    private val guideUrl = "https://watch.spectrum.net/guide"
    
    // Modern desktop Chrome user agent for better compatibility
    private val desktopUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    
    // Track guide states for D-pad navigation
    private var guideLoaded = false
    
    // Track back button state for clean exit behavior
    private var lastBackPressTime = 0L
    private val BACK_PRESS_TIME_INTERVAL = 2000L // 2 seconds

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Configure fullscreen and TV display settings
        setupFullscreenDisplay()
        
        setContentView(R.layout.activity_main)
        
        // Initialize views
        playerWebView = findViewById(R.id.webview_player)
        guideWebView = findViewById(R.id.webview_guide)
        loadingProgress = findViewById(R.id.loading_progress)
        
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
        
        // Modern back gesture handling
        setupBackPressedCallback()
        
        // Add JavaScript interface for SpecStream functionality
        playerWebView.addJavascriptInterface(this, "SpecStream")
        guideWebView.addJavascriptInterface(this, "SpecStream")
        
        Log.d("SpecStream", "Activity setup complete - ready for D-pad events")
    }
    
    private fun setupBackPressedCallback() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Log.d("SpecStream", "Back pressed via modern callback")
                
                when {
                    !guideWebView.isGone -> {
                        // Hide guide overlay
                        Log.d("SpecStream", "Back: Hiding guide overlay")
                        hideGuide()
                    }
                    playerWebView.canGoBack() -> {
                        // Normal web navigation back
                        Log.d("SpecStream", "Back: Web navigation")
                        playerWebView.goBack()
                    }
                    else -> {
                        // Double-back to exit when in clean video state
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastBackPressTime < BACK_PRESS_TIME_INTERVAL) {
                            Log.d("SpecStream", "Back: Double-back detected, closing app")
                            finish()
                        } else {
                            Log.d("SpecStream", "Back: First back press in clean state, staying in video")
                            lastBackPressTime = currentTime
                            // Could show a toast here: "Press back again to exit"
                        }
                    }
                }
            }
        })
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
        
        configureWebViewSettings(playerWebView)
        
        // Set WebView client for handling page navigation
        playerWebView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                loadingProgress.visibility = View.VISIBLE
            }
            
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                loadingProgress.visibility = View.GONE
                Log.d("SpecStream", "Player loaded successfully")
                
                // Inject UI cleanup script for better TV experience
                injectUiCleanupScript()
                
                // Guide will be preloaded automatically via JavaScript when video is found
            }
            
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false
            }
            
            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error)
                
                val url = request?.url.toString()
                if (!url.contains("collector.pi.spectrum.net") && !url.contains("imrworldwide.com")) {
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
        
        // Load initial page
    }
    
    @SuppressLint("SetJavaScriptEnabled")
    private fun setupGuideWebView() {
        Log.d("SpecStream", "Setting up guide WebView...")
        
        configureWebViewSettings(guideWebView)
        
        // Set WebView client for guide
        guideWebView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (!guideLoaded) {
                    guideLoaded = true
                    Log.d("SpecStream", "Guide loaded successfully")
                }
                
                // Inject guide-specific UI cleanup
                injectGuideCleanupScript()
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
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            allowFileAccess = true
            allowContentAccess = true
            loadWithOverviewMode = true
            useWideViewPort = true
        }
    }
    
    private fun loadSpectrumSite() {
        // Always start with a fresh Spectrum session
        Log.d("SpecStream", "Loading fresh Spectrum session")
        playerWebView.loadUrl("https://watch.spectrum.net/")
    }
    


    

    
    private fun injectUiCleanupScript() {
        Log.d("SpecStream", "Injecting UI cleanup for TV interface...")
        
        val uiCleanupScript = """
            (function() {
                console.log('SpecStream: Starting TV UI cleanup...');
                
                // Debug: Check what guide elements exist for troubleshooting
                function debugGuideElements() {
                    var player = document.querySelector('#spectrum-player');
                    console.log('SpecStream: Player element found:', !!player);
                    
                    var channelBrowser = document.querySelector('#channel-browser');
                    console.log('SpecStream: Channel browser found:', !!channelBrowser);
                    
                    console.log('SpecStream: jQuery available:', typeof $ !== 'undefined');
                }
                
                // Create a function that runs repeatedly to handle dynamic content
                var cleanupInterval = setInterval(function() {
                    try {
                        // Accept initial prompts automatically
                        if (document.querySelector('.continue-button')?.childNodes?.length > 0) {
                            document.querySelector('.continue-button')?.childNodes[0].click();
                        }
                        document.querySelector('[aria-label*="Continue and accept"]')?.click();
                        document.querySelector('.btn-success')?.click();
                        
                        // Hide desktop navigation elements
                        var elementsToHide = [
                            '.site-header',
                            '.navbar',
                            '.nav-triangle-pattern', 
                            '.transparent-header',
                            '.site-footer',
                            '.site-footer-wrapper',
                            '.top-level-nav',
                            '.time-nav',
                            '.filter-section',
                            '[role="tablist"]'
                        ];
                        
                        elementsToHide.forEach(function(selector) {
                            var elements = document.querySelectorAll(selector);
                            elements.forEach(function(el) {
                                el.style.display = 'none';
                            });
                        });
                        
                        // Style video player for TV
                        var player = document.querySelector('#spectrum-player');
                        if (player) {
                            player.style.width = '100%';
                            player.style.height = '100%';
                            player.setAttribute('tabindex', '0');
                        }
                        
                        // Style channel browser for full-screen
                        var channelBrowser = document.querySelector('#channel-browser');
                        if (channelBrowser) {
                            channelBrowser.style.height = '100%';
                            channelBrowser.setAttribute('tabindex', '0');
                        }
                        
                        // Style TV guide for full-screen
                        var guide = document.querySelector('.guide');
                        if (guide) {
                            guide.style.width = '100%';
                        }
                        
                        // Set video volume to 75%
                        var video = document.querySelector('video');
                        if (video) {
                            video.volume = 0.75;
                            console.log('SpecStream: Video found, volume set to 75%');
                            
                            // Debug guide elements when video is ready
                            debugGuideElements();
                            
                            // Preload guide for better performance
                            if (typeof SpecStream !== 'undefined') {
                                SpecStream.preloadGuide();
                            }
                            
                            // Stop the cleanup interval once video is found and configured
                            clearInterval(cleanupInterval);
                            console.log('SpecStream: TV UI cleanup completed');
                        }
                        
                    } catch (e) {
                        console.log('SpecStream: Error in UI cleanup:', e);
                    }
                }, 2000); // Run every 2 seconds until video is found
                
                // Stop cleanup after 30 seconds max to prevent infinite running
                setTimeout(function() {
                    clearInterval(cleanupInterval);
                    console.log('SpecStream: UI cleanup timeout reached');
                }, 30000);
                
                // Add global functions for D-pad navigation
                window.toggleGuide = function(action) {
                    console.log('SpecStream: toggleGuide called with:', action);
                    if (typeof SpecStream !== 'undefined') {
                        SpecStream.channelGuide(action);
                    } else {
                        console.log('SpecStream: SpecStream interface not found');
                    }
                };
                
            })();
        """.trimIndent()
        
        playerWebView.evaluateJavascript(uiCleanupScript) { result ->
            Log.d("SpecStream", "UI cleanup script injected: $result")
        }
    }
    
    private fun injectGuideCleanupScript() {
        Log.d("SpecStream", "Injecting guide cleanup script...")
        
        val guideCleanupScript = """
            (function() {
                console.log('SpecStream: Starting guide UI cleanup...');
                
                // Define channel click handler function
                function channelClickHandler(event) {
                    try {
                        event.preventDefault();
                        event.stopImmediatePropagation();
                        
                        console.log('SpecStream: Channel clicked:', event.target);
                        
                        // Extract channel ID from clicked element or its parent
                        var channelLink = event.target.closest('a[href*="tmsGuideServiceId"]');
                        if (channelLink) {
                            var url = new URL(channelLink.href);
                            var channelId = url.searchParams.get('tmsGuideServiceId');
                            if (channelId && typeof SpecStream !== 'undefined') {
                                console.log('SpecStream: Navigating to channel:', channelId);
                                SpecStream.navToChannel(channelId);
                            } else {
                                console.log('SpecStream: No channel ID found or SpecStream interface missing');
                            }
                        } else {
                            console.log('SpecStream: No channel link found');
                        }
                    } catch (e) {
                        console.log('SpecStream: Error handling channel click:', e);
                    }
                }
                
                var cleanupInterval = setInterval(function() {
                    try {
                        // Hide desktop guide navigation elements
                        var elementsToHide = [
                            '.site-footer-wrapper',
                            '.top-level-nav',
                            '.navbar',
                            '.time-nav',
                            '.filter-section',
                            '[role="tablist"]'
                        ];
                        
                        elementsToHide.forEach(function(selector) {
                            var elements = document.querySelectorAll(selector);
                            elements.forEach(function(el) {
                                el.style.display = 'none';
                            });
                        });
                        
                        // Style guide for full-screen TV
                        var guide = document.querySelector('.guide');
                        if (guide) {
                            guide.style.width = '100%';
                        }
                        
                        // Focus management and click handling for guide
                        var channelContent = document.querySelector('.channel-content-list-container');
                        if (channelContent) {
                            channelContent.setAttribute('tabindex', '1');
                            channelContent.focus();
                            
                            // Intercept channel clicks to navigate directly to live streams
                            channelContent.removeEventListener('click', channelClickHandler);
                            channelContent.addEventListener('click', channelClickHandler);
                            
                            clearInterval(cleanupInterval);
                            console.log('SpecStream: Guide UI cleanup completed');
                        }
                        
                    } catch (e) {
                        console.log('SpecStream: Error in guide cleanup:', e);
                    }
                }, 2000);
                
                // Stop cleanup after 15 seconds
                setTimeout(function() {
                    clearInterval(cleanupInterval);
                    console.log('SpecStream: Guide cleanup timeout reached');
                }, 15000);
                
            })();
        """.trimIndent()
        
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
    @Suppress("OVERRIDE_DEPRECATION")
    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        // Handle D-pad key events for TV navigation
        if (event?.action == KeyEvent.ACTION_DOWN) {
            Log.d("SpecStream", "dispatchKeyEvent: ${event.keyCode} (guide visible: ${!guideWebView.isGone})")
            
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
                
                // Temporary keyboard shortcuts for emulator testing
                KeyEvent.KEYCODE_U -> {
                    Log.d("SpecStream", "Test key U: Showing guide via JavaScript")
                    playerWebView.evaluateJavascript("toggleGuide('SHOWGUIDE');", null)
                    return true
                }
                KeyEvent.KEYCODE_H -> {
                    Log.d("SpecStream", "Test key H: Hiding guide via JavaScript")
                    playerWebView.evaluateJavascript("toggleGuide('HIDEGUIDE');", null)
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
                Log.d("SpecStream", "JavaScript: Navigating to channel $channelId")
                playerWebView.loadUrl("https://watch.spectrum.net/livetv?tmsid=$channelId")
                // Clear WebView history so back button doesn't navigate to previous channels
                playerWebView.clearHistory()
                guideWebView.visibility = View.GONE
                guideWebView.evaluateJavascript("history.back();", null)
            }
        } catch (e: Exception) {
            Log.e("SpecStream", "Error navigating to channel: $e")
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
    
    private fun hideGuide() {
        Log.d("SpecStream", "Hiding guide overlay")
        runOnUiThread {
            guideWebView.visibility = View.GONE
        }
    }
    
    private fun resetBackPressTimer() {
        lastBackPressTime = 0L
    }
    
    override fun onResume() {
        super.onResume()
        playerWebView.onResume()
        guideWebView.onResume()
        
        // Restore basic fullscreen mode
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        )
    }
    
    override fun onPause() {
        super.onPause()
        playerWebView.onPause()
        guideWebView.onPause()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        playerWebView.destroy()
        guideWebView.destroy()
    }
}
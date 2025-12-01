@file:Suppress("SpellCheckingInspection") // Contains brand names and technical terms

package com.dojer.specstream

import android.annotation.SuppressLint
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
                
                // Inject UI cleanup script with built-in login detection delay
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
            
            // Enable caching to avoid re-downloading resources
            cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
            databaseEnabled = true
            
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
    
    private fun saveLastChannel(channelId: String) {
        currentChannelUrl = "https://watch.spectrum.net/livetv?tmsid=$channelId"
        Log.d("SpecStream", "Channel remembered for this session: $currentChannelUrl")
    }
    
    private fun injectUiCleanupScript() {
        Log.d("SpecStream", "Injecting UI cleanup for TV interface...")
        
        val uiCleanupScript = """
            (function() {
                console.log('SpecStream: Starting TV UI cleanup...');
                
                // Auto-dismiss Spectrum connectivity modal in player WebView
                var modalCheckInterval;
                var modalDismissed = false;
                
                function checkAndDismissPlayerModal() {
                    if (modalDismissed) return;
                    
                    // Skip modal checking if on login page (password field exists)
                    if (document.querySelector('input[type="password"]') !== null) {
                        console.log('SpecStream: Login page active, skipping modal check');
                        return;
                    }
                    
                    var modalSelectors = [
                        '.kite-modal-accept-btn',
                        '.kite-btn-primary[ng-click*="modalInstance.close"]',
                        'button[ng-click*="modalInstance.close"]'
                    ];
                    
                    for (var i = 0; i < modalSelectors.length; i++) {
                        var btn = document.querySelector(modalSelectors[i]);
                        if (btn && btn.offsetParent !== null && !btn.disabled) {
                            console.log('SpecStream: Auto-dismissing connectivity modal in player:', modalSelectors[i]);
                            btn.click();
                            modalDismissed = true;
                            clearInterval(modalCheckInterval);
                            console.log('SpecStream: Player modal dismissed, checking stopped');
                            return;
                        }
                    }
                }
                
                // Start modal checking immediately, but it will skip while on login page
                setTimeout(checkAndDismissPlayerModal, 100);
                setTimeout(checkAndDismissPlayerModal, 500);
                modalCheckInterval = setInterval(checkAndDismissPlayerModal, 1000);
                
                setTimeout(function() {
                    if (!modalDismissed) {
                        clearInterval(modalCheckInterval);
                        console.log('SpecStream: Player modal checking stopped (timeout)');
                    }
                }, 10000);

                // Preload guide early if user is authenticated (not on login page)
                setTimeout(function() {
                    if (document.querySelector('input[type="password"]') === null) {
                        // Not on login page - user is authenticated
                        console.log('SpecStream: User authenticated, preloading guide for instant access');
                        if (typeof SpecStream !== 'undefined') {
                            SpecStream.preloadGuide();
                        }
                    }
                }, 3000); // Wait 3 seconds after page load to avoid competing with initial page resources

                // Start passive cleanup immediately
                var cleanupInterval = setInterval(function() {
                    try {
                        // Skip auto-clicking if on login page
                        var onLoginPage = document.querySelector('input[type="password"]') !== null;
                        
                        if (!onLoginPage) {
                            // Accept initial prompts automatically
                            var authButtons = [
                                '.continue-button',
                                '[aria-label*="Continue and accept"]',
                                '.btn-success'
                            ];
                            
                            authButtons.forEach(function(selector) {
                                try {
                                    var button = document.querySelector(selector);
                                    if (button && button.offsetParent !== null) {
                                        button.click();
                                        console.log('SpecStream: Auto-clicked auth button:', selector);
                                    }
                                } catch (e) {}
                            });
                            
                            try {
                                if (document.querySelector('.continue-button')?.childNodes?.length > 0) {
                                    document.querySelector('.continue-button').childNodes[0].click();
                                    console.log('SpecStream: Auto-clicked continue-button child node');
                                }
                            } catch (e) {}
                        }
                        
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
                        
                        // Hide video control UI elements (using visibility: hidden to preserve functionality)
                        var videoControlsToHide = [
                            'img[alt="full screen"]',
                            '.si-circle-info-f.info-icon',
                            'img[alt="Closed captions"]',
                            'img[alt="Audio"]',
                            'img[alt="Closed captioning settings"]',
                            'img[alt="mute"]',
                            '#cast_caf_icon_boxfill',
                            '.cast_caf_state_h'
                        ];
                        
                        var hiddenControlsCount = 0;
                        videoControlsToHide.forEach(function(selector) {
                            var elements = document.querySelectorAll(selector);
                            elements.forEach(function(el) {
                                if (el.style.visibility !== 'hidden') {
                                    el.style.visibility = 'hidden';
                                    hiddenControlsCount++;
                                }
                            });
                        });
                        
                        if (hiddenControlsCount > 0) {
                            console.log('SpecStream: Hidden ' + hiddenControlsCount + ' video control elements');
                        }
                        
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
                        
                        // Set video volume to 75% and ensure not muted
                        var video = document.querySelector('video');
                        if (video) {
                            // Check if video is muted and unmute if needed
                            if (video.muted) {
                                video.muted = false;
                                console.log('SpecStream: Video was muted, unmuted automatically');
                            }
                            
                            video.volume = 0.75;
                            console.log('SpecStream: Video found, volume set to 75%');
                            
                            // Check for UI volume control mute button and unmute if needed
                            var volumeButton = document.querySelector('#volume-control-icon');
                            var volumeSlider = document.querySelector('#volume-control-slider');
                            
                            if (volumeButton && volumeSlider) {
                                // Check if aria-pressed="true" indicates muted state
                                var isMuted = volumeButton.getAttribute('aria-pressed') === 'true';
                                var sliderValue = parseInt(volumeSlider.value) || 0;
                                
                                if (isMuted || sliderValue === 0) {
                                    console.log('SpecStream: UI volume controls appear muted, attempting to unmute');
                                    
                                    // Click the mute button to unmute
                                    volumeButton.click();
                                    
                                    // Set slider to 75% (75 out of 100)
                                    setTimeout(function() {
                                        volumeSlider.value = 75;
                                        // Trigger change event to update UI
                                        var changeEvent = new Event('change', { bubbles: true });
                                        volumeSlider.dispatchEvent(changeEvent);
                                        var inputEvent = new Event('input', { bubbles: true });
                                        volumeSlider.dispatchEvent(inputEvent);
                                        console.log('SpecStream: Volume slider set to 75%');
                                    }, 100);
                                }
                            }
                            
                            // Preload guide for better performance
                            if (typeof SpecStream !== 'undefined') {
                                SpecStream.preloadGuide();
                            }
                            
                            // Start periodic volume monitoring (every 30 seconds)
                            setInterval(function() {
                                try {
                                    var video = document.querySelector('video');
                                    var volumeButton = document.querySelector('#volume-control-icon');
                                    var volumeSlider = document.querySelector('#volume-control-slider');
                                    
                                    if (video && volumeButton && volumeSlider) {
                                        var videoMuted = video.muted;
                                        var uiMuted = volumeButton.getAttribute('aria-pressed') === 'true';
                                        var sliderValue = parseInt(volumeSlider.value) || 0;
                                        
                                        if (videoMuted || uiMuted || sliderValue === 0) {
                                            console.log('SpecStream: Periodic check detected muted audio, restoring volume');
                                            
                                            // Unmute video element
                                            if (videoMuted) {
                                                video.muted = false;
                                            }
                                            
                                            // Unmute UI if needed
                                            if (uiMuted) {
                                                volumeButton.click();
                                            }
                                            
                                            // Restore volume
                                            video.volume = 0.75;
                                            volumeSlider.value = 75;
                                            
                                            // Trigger UI events
                                            var changeEvent = new Event('change', { bubbles: true });
                                            volumeSlider.dispatchEvent(changeEvent);
                                            var inputEvent = new Event('input', { bubbles: true });
                                            volumeSlider.dispatchEvent(inputEvent);
                                        }
                                    }
                                } catch (e) {
                                    console.log('SpecStream: Error in volume monitoring:', e);
                                }
                            }, 30000); // Check every 30 seconds
                            

                            // Stop the cleanup interval once video is found and configured
                            clearInterval(cleanupInterval);
                            console.log('SpecStream: TV UI cleanup completed - volume monitoring active');
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
                
                // Login navigation setup function
                window.enableCursorLoginNavigation = function() {
                    // Only set up if on login page
                    if (document.querySelector('input[type="password"]') === null) {
                        console.log('SpecStream: Not on login page, skipping login navigation setup');
                        return;
                    }
                    
                    console.log('SpecStream: Setting up login D-pad navigation');
                    if (window.loginCursorState && window.loginCursorState.active) return;
                    
                    var usernameField = document.querySelector('#kite-label-input-4');
                        var passwordField = document.querySelector('#kite-label-input-6');
                        var staySignedInCheckbox = document.querySelector('input[type="checkbox"]');
                    var signInButton = document.querySelector('#signInBtn button[type="submit"]') || 
                                      document.querySelector('button[type="submit"].kite-button--primary') ||
                                      document.querySelector('button[type="submit"]');
                    
                    if (usernameField && passwordField && signInButton) {
                        window.loginCursorState = {
                            elements: [usernameField, passwordField, signInButton],
                            currentIndex: 0,
                            active: true
                        };
                        
                        if (staySignedInCheckbox) {
                            window.loginCursorState.elements.splice(2, 0, staySignedInCheckbox);
                        }
                        
                        window.loginCursorState.elements.forEach(function(el) {
                            el.setAttribute('tabindex', '-1');
                        });
                        
                        window.loginCursorState.elements[0].focus();
                        
                        if (typeof SpecStream !== 'undefined') {
                            SpecStream.setLoginInterfaceActive(true);
                        }
                    }
                };
                
                window.handleLoginNavigation = function(direction) {
                    if (!window.loginCursorState || !window.loginCursorState.active) return;
                    
                    switch (direction) {
                        case 'UP':
                            if (window.loginCursorState.currentIndex > 0) {
                                window.loginCursorState.currentIndex--;
                                window.loginCursorState.elements[window.loginCursorState.currentIndex].focus();
                            }
                            break;
                        case 'DOWN':
                            if (window.loginCursorState.currentIndex < window.loginCursorState.elements.length - 1) {
                                window.loginCursorState.currentIndex++;
                                window.loginCursorState.elements[window.loginCursorState.currentIndex].focus();
                            }
                            break;
                        case 'SELECT':
                            var el = window.loginCursorState.elements[window.loginCursorState.currentIndex];
                            if (el.tagName.toLowerCase() === 'input') {
                                if (el.type === 'checkbox') {
                                    el.checked = !el.checked;
                                    el.dispatchEvent(new Event('change', { bubbles: true }));
                                } else {
                                    el.focus();
                                }
                            } else if (el.tagName.toLowerCase() === 'button') {
                                el.click();
                            }
                            break;
                    }
                };
                
                window.cancelLogin = function() {
                    if (window.loginCursorState) {
                        window.loginCursorState.active = false;
                    }
                    if (typeof SpecStream !== 'undefined') {
                        SpecStream.setLoginInterfaceActive(false);
                    }
                };
                
                // Check for login page periodically and enable navigation
                var loginCheckInterval = setInterval(function() {
                    var passwordField = document.querySelector('input[type="password"]');
                    
                    if (passwordField !== null) {
                        // Still on login page - enable navigation if not already active
                        if (!window.loginCursorState || !window.loginCursorState.active) {
                            enableCursorLoginNavigation();
                        }
                    } else {
                        // Password field is gone - check if login was successful
                        if (window.loginCursorState && window.loginCursorState.active) {
                            // We had active login navigation, but now password field is gone
                            // This means login was successful!
                            console.log('SpecStream: Login successful, cleaning up and stopping login check');
                            window.cancelLogin(); // Clean up login state
                            clearInterval(loginCheckInterval);
                        }
                    }
                }, 1500);
                
                // Global function for D-pad navigation
                window.toggleGuide = function(action) {
                    console.log('SpecStream: toggleGuide called with:', action);
                    if (typeof SpecStream !== 'undefined') {
                        SpecStream.channelGuide(action);
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
                
                // Auto-dismiss Spectrum connectivity modal in guide WebView
                var guideModalInterval;
                var guideModalDismissed = false;
                
                function checkAndDismissConnectivityModal() {
                    if (guideModalDismissed) return; // Stop if already dismissed
                    
                    // Look directly for modal dismiss buttons (no text detection needed)
                    var modalSelectors = [
                        '.kite-modal-accept-btn',
                        '.kite-btn-primary[ng-click*="modalInstance.close"]',
                        'button[ng-click*="modalInstance.close"]'
                    ];
                    
                    for (var i = 0; i < modalSelectors.length; i++) {
                        var btn = document.querySelector(modalSelectors[i]);
                        if (btn && btn.offsetParent !== null && !btn.disabled) {
                            console.log('SpecStream: Auto-dismissing connectivity modal in guide:', modalSelectors[i]);
                            btn.click();
                            guideModalDismissed = true;
                            clearInterval(guideModalInterval);
                            console.log('SpecStream: Guide modal dismissed, checking stopped');
                            return;
                        }
                    }
                }
                
                // Check immediately on guide load and then more frequently 
                setTimeout(function() {
                    try {
                        checkAndDismissConnectivityModal();
                    } catch (e) {
                        console.log('SpecStream: Error in initial connectivity modal check:', e);
                    }
                }, 100); // Much faster initial check
                
                guideModalInterval = setInterval(function() {
                    try {
                        checkAndDismissConnectivityModal();
                    } catch (e) {
                        console.log('SpecStream: Error in periodic connectivity modal check:', e);
                    }
                }, 1000); // Check every second instead of 3 seconds
                
                // Safety timeout - stop checking after 10 seconds if modal never appeared
                setTimeout(function() {
                    if (!guideModalDismissed) {
                        clearInterval(guideModalInterval);
                        console.log('SpecStream: Guide modal checking stopped (timeout)');
                    }
                }, 10000);
                
                // Emergency broadcast alert dismissal (one-time check)
                function checkAndDismissEmergencyAlert() {
                    try {
                        var emergencyCloseBtn = document.querySelector('div.si-x.button[aria-label="Close alert"]');
                        if (emergencyCloseBtn && emergencyCloseBtn.offsetParent !== null) {
                            console.log('SpecStream: Emergency broadcast alert detected, dismissing...');
                            emergencyCloseBtn.click();
                            console.log('SpecStream: Emergency alert dismissed successfully');
                            return true;
                        }
                        return false;
                    } catch (e) {
                        console.log('SpecStream: Error checking for emergency alert:', e);
                        return false;
                    }
                }
                
                // Check for emergency alert after 4 seconds (one-time only)
                setTimeout(function() {
                    checkAndDismissEmergencyAlert();
                }, 4000);
                
                
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
                }, 500); // Check every 500ms for faster guide interactivity
                
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
    // Track if login interface is currently active
    private var isLoginInterfaceActive = false
    
    @Suppress("OVERRIDE_DEPRECATION")
    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        // Handle D-pad key events for TV navigation
        if (event?.action == KeyEvent.ACTION_DOWN) {
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
                Log.d("SpecStream", "Channel switch to $channelId")
                Log.d("SpecStream", "Using reliable page reload for channel switching")
                playerWebView.loadUrl("https://watch.spectrum.net/livetv?tmsid=$channelId")
                playerWebView.clearHistory() // Prevent back navigation between channels
                
                // Save the new channel for memory
                saveLastChannel(channelId)
                
                // Hide guide and return to video
                guideWebView.visibility = View.GONE
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
    
    private fun handleLoginKeyEvent(event: KeyEvent?) {
        if (event == null) return
        
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
        guideWebView.onResume()
        
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
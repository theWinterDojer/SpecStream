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
        
        // Performance optimization for TV apps
        playerWebView.setVerticalScrollBarEnabled(false)
        
        // Load initial page
    }
    
    @SuppressLint("SetJavaScriptEnabled")
    private fun setupGuideWebView() {
        Log.d("SpecStream", "Setting up guide WebView...")
        
        configureWebViewSettings(guideWebView)
        
        // 🔥 CRITICAL PERFORMANCE OPTIMIZATIONS
        guideWebView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        guideWebView.setLayerType(WebView.LAYER_TYPE_SOFTWARE, null)
        
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
            
            // KEEP: These improve performance and caching
            cacheMode = WebSettings.LOAD_DEFAULT
            
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
                
                // Create a function that runs repeatedly to handle dynamic content
                var cleanupInterval = setInterval(function() {
                    try {
                        // Accept initial prompts automatically (original lean selectors)
                        var authButtons = [
                            '.continue-button',
                            '[aria-label*="Continue and accept"]',
                            '.btn-success'
                        ];
                        
                        // Try each selector safely
                        authButtons.forEach(function(selector) {
                            try {
                                var button = document.querySelector(selector);
                                if (button && button.offsetParent !== null) { // Check if visible
                                    button.click();
                                    console.log('SpecStream: Auto-clicked auth button:', selector);
                                }
                            } catch (e) {
                                // Silently continue if selector fails
                            }
                        });
                        
                        // Special handling for the original continue-button with child nodes
                        try {
                            if (document.querySelector('.continue-button')?.childNodes?.length > 0) {
                                document.querySelector('.continue-button').childNodes[0].click();
                                console.log('SpecStream: Auto-clicked continue-button child node');
                            }
                        } catch (e) {
                            // Silently continue if this fails
                        }
                        
                        // Check for login screen - prepare for D-pad login interface
                        var loginIndicators = [
                            'Sign In to Get Started',
                            'Username',
                            'Password',
                            'input[type="password"]',
                            'input[name="password"]',
                            'input[name="username"]',
                            '.login-form',
                            '[data-testid*="login"]'
                        ];
                        
                        var isLoginScreen = false;
                        loginIndicators.forEach(function(indicator) {
                            if (indicator.startsWith('.') || indicator.startsWith('[') || indicator.startsWith('input')) {
                                // CSS selector
                                if (document.querySelector(indicator)) {
                                    isLoginScreen = true;
                                }
                            } else {
                                // Text content
                                if (document.body && document.body.textContent.includes(indicator)) {
                                    isLoginScreen = true;
                                }
                            }
                        });
                        
                        if (isLoginScreen) {
                            // Check if cursor navigation is already active to prevent re-initialization
                            if (!window.loginCursorState || !window.loginCursorState.active) {
                                console.log('SpecStream: Login screen detected - enabling cursor navigation');
                                // Enable cursor navigation immediately
                                enableCursorLoginNavigation();
                            } else {
                                console.log('SpecStream: Login screen detected but cursor navigation already active');
                            }
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
                        
                        videoControlsToHide.forEach(function(selector) {
                            var elements = document.querySelectorAll(selector);
                            elements.forEach(function(el) {
                                el.style.visibility = 'hidden';
                                console.log('SpecStream: Hidden video control element:', selector);
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
                
                // Simplified Login Navigation - Targeting Exact Spectrum Elements
                window.enableCursorLoginNavigation = function() {
                    console.log('SpecStream: Enabling simplified login navigation');
                    
                    // Check if already initialized to prevent duplicates
                    if (window.loginCursorState && window.loginCursorState.active) {
                        console.log('SpecStream: Cursor navigation already active, skipping initialization');
                        return;
                    }
                    
                    // Target exact Spectrum login elements
                    var usernameField = document.querySelector('#kite-label-input-4');
                    var passwordField = document.querySelector('#kite-label-input-6');
                    var staySignedInCheckbox = document.querySelector('input[type="checkbox"]');
                    // Target the actual clickable button, not the inner span
                    var signInButton = document.querySelector('#signInBtn button[type="submit"]') || 
                                      document.querySelector('button[type="submit"].kite-button--primary') ||
                                      document.querySelector('button[type="submit"]');
                    
                    console.log('SpecStream: Username field found:', !!usernameField);
                    console.log('SpecStream: Password field found:', !!passwordField);
                    console.log('SpecStream: Stay signed in checkbox found:', !!staySignedInCheckbox);
                    console.log('SpecStream: Sign in button found:', !!signInButton);
                    
                    if (usernameField && passwordField && signInButton) {
                        console.log('SpecStream: All required login elements found, setting up navigation');
                        
                        // Create simplified navigation with only the essential elements
                        window.loginCursorState = {
                            elements: [usernameField, passwordField, signInButton],
                            currentIndex: 0,
                            active: true
                        };
                        
                        // Add checkbox if found (optional element)
                        if (staySignedInCheckbox) {
                            // Insert checkbox before sign in button
                            window.loginCursorState.elements.splice(2, 0, staySignedInCheckbox);
                            console.log('SpecStream: Added stay signed in checkbox to navigation');
                        }
                        
                        console.log('SpecStream: Navigation setup with', window.loginCursorState.elements.length, 'elements');
                        
                        // Style all navigable elements for consistent appearance
                        window.loginCursorState.elements.forEach(function(element, index) {
                            // Remove any existing border/highlighting from all elements
                            element.style.border = '';
                            element.style.outline = 'none';
                            element.style.boxShadow = 'none';
                            
                            if (element.tagName.toLowerCase() === 'input') {
                                element.style.fontSize = '18px';
                                element.style.padding = '12px';
                                element.style.borderRadius = '6px';
                            } else if (element.tagName.toLowerCase() === 'button') {
                                // Style for button elements
                                element.style.fontSize = '16px';
                                element.style.borderRadius = '6px';
                            }
                            
                            // Prevent automatic focus changes that cause snap-back
                            element.setAttribute('tabindex', '-1');
                            
                            console.log('SpecStream: Styled element', index, ':', element.tagName, element.className || 'no-class');
                        });
                        
                        // No automatic focus management - cursor only moves via D-pad navigation
                        // This ensures the cursor stays exactly where the user puts it
                        
                        // Focus the first element to show native cursor
                        if (window.loginCursorState.elements.length > 0) {
                            window.loginCursorState.elements[0].focus();
                        }
                        
                        // Notify Android that login interface is active
                        if (typeof SpecStream !== 'undefined') {
                            SpecStream.setLoginInterfaceActive(true);
                        }
                        
                        console.log('SpecStream: Cursor navigation enabled successfully');
                    } else {
                        console.log('SpecStream: Could not find login form fields');
                    }
                };
                

                

                
                window.handleLoginNavigation = function(direction) {
                    console.log('SpecStream: handleLoginNavigation called with direction:', direction);
                    
                    if (!window.loginCursorState) {
                        console.log('SpecStream: No loginCursorState found');
                        return;
                    }
                    
                    if (!window.loginCursorState.active) {
                        console.log('SpecStream: loginCursorState not active');
                        return;
                    }
                    
                    console.log('SpecStream: Current cursor index:', window.loginCursorState.currentIndex, 
                               'of', window.loginCursorState.elements.length, 'elements');
                    
                    switch (direction) {
                        case 'UP':
                            if (window.loginCursorState.currentIndex > 0) {
                                window.loginCursorState.currentIndex--;
                                console.log('SpecStream: Moved cursor UP to index:', window.loginCursorState.currentIndex);
                                // Focus the new element to show native cursor
                                window.loginCursorState.elements[window.loginCursorState.currentIndex].focus();
                            } else {
                                console.log('SpecStream: Cannot move UP - already at first element');
                            }
                            break;
                            
                        case 'DOWN':
                            if (window.loginCursorState.currentIndex < window.loginCursorState.elements.length - 1) {
                                window.loginCursorState.currentIndex++;
                                console.log('SpecStream: Moved cursor DOWN to index:', window.loginCursorState.currentIndex);
                                // Focus the new element to show native cursor
                                window.loginCursorState.elements[window.loginCursorState.currentIndex].focus();
                            } else {
                                console.log('SpecStream: Cannot move DOWN - already at last element');
                            }
                            break;
                            
                        case 'SELECT':
                            var currentElement = window.loginCursorState.elements[window.loginCursorState.currentIndex];
                            console.log('SpecStream: Selecting element:', currentElement.tagName, currentElement.type || 'no-type', currentElement.className);
                            
                            if (currentElement.tagName.toLowerCase() === 'input') {
                                if (currentElement.type === 'checkbox') {
                                    console.log('SpecStream: Toggling stay signed in checkbox');
                                    // Simple checkbox toggle
                                    currentElement.checked = !currentElement.checked;
                                    // Dispatch change event so form recognizes the change
                                    var changeEvent = new Event('change', { bubbles: true });
                                    currentElement.dispatchEvent(changeEvent);
                                } else {
                                    console.log('SpecStream: Activating input field for keyboard');
                                    // Only focus - avoid click which can trigger form submission behavior
                                    currentElement.focus();
                                }
                            } else if (currentElement.tagName.toLowerCase() === 'button') {
                                console.log('SpecStream: Clicking sign in button');
                                currentElement.click();
                            } else {
                                console.log('SpecStream: Clicking element (fallback)');
                                currentElement.click();
                            }
                            break;
                            
                        default:
                            console.log('SpecStream: Unknown navigation direction:', direction);
                            break;
                    }
                };
                
                window.cancelLogin = function() {
                    console.log('SpecStream: Canceling login interface');
                    
                    // Disable cursor navigation
                    if (window.loginCursorState) {
                        window.loginCursorState.active = false;
                    }
                    
                    // Notify Android that login interface is no longer active
                    if (typeof SpecStream !== 'undefined') {
                        SpecStream.setLoginInterfaceActive(false);
                    }
                };
                
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
                
                // Auto-dismiss Spectrum connectivity modal (only needed in guide WebView)
                var CONNECTIVITY_MODAL_INDICATORS = [
                    'Connect to Your Spectrum Internet for More to Watch',
                    'Due to programming restrictions'
                ];
                
                function checkAndDismissConnectivityModal() {
                    var hasModal = CONNECTIVITY_MODAL_INDICATORS.some(function(text) {
                        return document.body && document.body.textContent.includes(text);
                    });
                    
                    if (!hasModal) return;
                    
                    var buttons = document.querySelectorAll('button');
                    buttons.forEach(function(btn) {
                        if (btn.textContent.trim() === 'OK' && 
                            btn.offsetParent !== null && 
                            !btn.disabled) {
                            console.log('SpecStream: Auto-dismissing connectivity modal in guide');
                            btn.click();
                            return; // Exit after first successful click
                        }
                    });
                }
                
                // Check immediately on guide load and then periodically  
                setTimeout(function() {
                    try {
                        checkAndDismissConnectivityModal();
                    } catch (e) {
                        console.log('SpecStream: Error in initial connectivity modal check:', e);
                    }
                }, 1000); // Initial check after 1 second
                
                setInterval(function() {
                    try {
                        checkAndDismissConnectivityModal();
                    } catch (e) {
                        console.log('SpecStream: Error in periodic connectivity modal check:', e);
                    }
                }, 3000); // Then every 3 seconds
                
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
                
                guideWebView.visibility = View.GONE
                guideWebView.evaluateJavascript("history.back();", null)
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
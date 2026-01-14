(function() {
    if (window.__specStreamPlayerInit) {
        console.log('SpecStream: Player UI cleanup already initialized, skipping');
        return;
    }
    window.__specStreamPlayerInit = true;
    console.log('SpecStream: Starting TV UI cleanup...');

    // Auto-dismiss Spectrum connectivity modal in player WebView
    var modalCheckInterval;
    var modalDismissed = false;

    function checkAndDismissPlayerModal() {
        if (modalDismissed) return;

        // Skip modal checking if on login page (visible password field exists)
        if (findVisiblePasswordField() !== null) {
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

    function isElementVisible(el) {
        if (!el) return false;
        var style = window.getComputedStyle(el);
        if (!style || style.display === 'none' || style.visibility === 'hidden' || style.opacity === '0') return false;
        var rect = el.getBoundingClientRect();
        return rect.width >= 2 && rect.height >= 2;
    }

    function findVisiblePasswordField() {
        try {
            var fields = document.querySelectorAll('input[type="password"]');
            for (var i = 0; i < fields.length; i++) {
                if (isElementVisible(fields[i])) {
                    return fields[i];
                }
            }
        } catch (e) {}
        return null;
    }

    function isElementClickable(el) {
        if (!isElementVisible(el)) return false;
        if (el.disabled) return false;
        if (el.getAttribute && el.getAttribute('aria-disabled') === 'true') return false;
        return true;
    }

    function isElementTopmost(el) {
        try {
            var rect = el.getBoundingClientRect();
            var x = rect.left + rect.width / 2;
            var y = rect.top + rect.height / 2;
            var top = document.elementFromPoint(x, y);
            return top && (top === el || el.contains(top) || top.contains(el));
        } catch (e) {
            return true;
        }
    }

    function triggerAuthClick(el, label) {
        if (!isElementClickable(el) || !isElementTopmost(el)) return false;
        try { el.focus(); } catch (e) {}
        try {
            var rect = el.getBoundingClientRect();
            var x = rect.left + rect.width / 2;
            var y = rect.top + rect.height / 2;
            var mouseOpts = { bubbles: true, cancelable: true, clientX: x, clientY: y };
            if (typeof PointerEvent !== 'undefined') {
                var pointerOpts = { bubbles: true, cancelable: true, clientX: x, clientY: y, pointerType: 'mouse' };
                el.dispatchEvent(new PointerEvent('pointerdown', pointerOpts));
                el.dispatchEvent(new PointerEvent('pointerup', pointerOpts));
            } else {
                el.dispatchEvent(new MouseEvent('mousedown', mouseOpts));
                el.dispatchEvent(new MouseEvent('mouseup', mouseOpts));
            }
            el.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, clientX: x, clientY: y, detail: 1 }));
        } catch (e) {}
        try { el.click(); } catch (e) {}
        console.log('SpecStream: Auto-clicked auth button:', label);
        return true;
    }

    function resolveClickableElement(node) {
        if (!node) return null;
        try {
            if (node.matches && node.matches('button, [role="button"], input[type="button"], input[type="submit"], a')) {
                return node;
            }
            var child = node.querySelector ? node.querySelector('button, [role="button"], input[type="button"], input[type="submit"], a') : null;
            if (child) return child;
            var parent = node.closest ? node.closest('button, [role="button"], input[type="button"], input[type="submit"], a') : null;
            if (parent) return parent;
        } catch (e) {}
        return node;
    }

    function clickAuthButtonsIn(container) {
        var selectors = [
            '.continue-button',
            'button.kite-button--primary',
            'button.kite-btn-primary',
            'button[ng-click*="continue"]',
            'button[ng-click*="accept"]',
            '[aria-label*="Continue"]',
            '[aria-label*="Accept"]',
            '.btn-success'
        ];
        var clicked = false;

        selectors.forEach(function(selector) {
            if (clicked) return;
            try {
                var nodes = (container || document).querySelectorAll(selector);
                nodes.forEach(function(node) {
                    if (clicked) return;
                    var target = resolveClickableElement(node);
                    if (triggerAuthClick(target, selector)) {
                        clicked = true;
                    }
                });
            } catch (e) {}
        });

        if (clicked) return true;

        try {
            var candidates = (container || document).querySelectorAll('button, [role="button"], a');
            for (var i = 0; i < candidates.length; i++) {
                var text = (candidates[i].innerText || candidates[i].textContent || '').trim();
                if (text && /^(continue|accept|agree|ok|okay|yes)/i.test(text)) {
                    var target = resolveClickableElement(candidates[i]);
                    if (triggerAuthClick(target, 'text:' + text)) {
                        return true;
                    }
                }
            }
        } catch (e) {}

        return false;
    }

    function clickAuthButtons() {
        var modalSelectors = [
            '.kite-modal',
            '.modal',
            '.modal-dialog',
            '[role="dialog"]'
        ];
        for (var i = 0; i < modalSelectors.length; i++) {
            try {
                var modal = document.querySelector(modalSelectors[i]);
                if (isElementVisible(modal) && clickAuthButtonsIn(modal)) {
                    return true;
                }
            } catch (e) {}
        }

        return clickAuthButtonsIn(document);
    }

    var authPromptStart = null;
    var authSkipLogged = false;
    var authPromptInterval = setInterval(function() {
        try {
            if (findVisiblePasswordField() !== null) {
                if (!authSkipLogged) {
                    console.log('SpecStream: Login page detected, skipping auth auto-click');
                    authSkipLogged = true;
                }
                return;
            }
            if (authPromptStart === null) {
                authPromptStart = Date.now();
                console.log('SpecStream: Auth prompt auto-click timer started');
            }
            clickAuthButtons();
            if (authPromptStart !== null && Date.now() - authPromptStart > 60000) {
                clearInterval(authPromptInterval);
                console.log('SpecStream: Auth prompt auto-click timeout reached');
            }
        } catch (e) {}
    }, 1000);

    // Start passive cleanup immediately
    var cleanupRuns = 0;
    var cleanupSlowed = false;
    var cleanupInterval;

    function runCleanup() {
        cleanupRuns++;
        try {
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

                if (findVisiblePasswordField() === null) {
                    console.log('SpecStream: Video detected, clearing login state');
                    if (typeof window.cancelLogin === 'function') {
                        window.cancelLogin();
                    } else if (typeof SpecStream !== 'undefined') {
                        if (window.loginCursorState) {
                            window.loginCursorState.active = false;
                        }
                        SpecStream.setLoginInterfaceActive(false);
                    }
                } else {
                    console.log('SpecStream: Video detected but login still visible, keeping login state');
                }

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
                return;
            }

            if (!cleanupSlowed && cleanupRuns >= 5) {
                cleanupSlowed = true;
                clearInterval(cleanupInterval);
                cleanupInterval = setInterval(runCleanup, 5000);
                console.log('SpecStream: Slowing UI cleanup interval');
            }

        } catch (e) {
            console.log('SpecStream: Error in UI cleanup:', e);
        }
    }

    cleanupInterval = setInterval(runCleanup, 2000); // Run every 2 seconds until video is found

    // Stop cleanup after 30 seconds max to prevent infinite running
    setTimeout(function() {
        clearInterval(cleanupInterval);
        console.log('SpecStream: UI cleanup timeout reached');
    }, 30000);

    // Login navigation setup function
    window.enableCursorLoginNavigation = function() {
        // Only set up if on login page
        if (findVisiblePasswordField() === null) {
            console.log('SpecStream: Not on login page, skipping login navigation setup');
            return;
        }

        console.log('SpecStream: Setting up login D-pad navigation');
        if (window.loginCursorState && window.loginCursorState.active) return;

        function pickFirstVisible(root, selectors) {
            for (var i = 0; i < selectors.length; i++) {
                try {
                    var el = root.querySelector(selectors[i]);
                    if (el && isElementVisible(el) && !el.disabled) {
                        return el;
                    }
                } catch (e) {}
            }
            return null;
        }

        function findButtonByText(root, regex) {
            try {
                var buttons = root.querySelectorAll('button, [role="button"], input[type="submit"], input[type="button"]');
                for (var i = 0; i < buttons.length; i++) {
                    var el = buttons[i];
                    if (!isElementVisible(el) || el.disabled) continue;
                    var text = (el.innerText || el.textContent || el.value || el.getAttribute('aria-label') || '').trim();
                    if (text && regex.test(text)) {
                        return el;
                    }
                }
            } catch (e) {}
            return null;
        }

        function findSignInButton(root) {
            var button = findButtonByText(root, /sign in/i);
            if (button) return button;
            return pickFirstVisible(root, [
                '#signInBtn button[type="submit"]',
                'button[type="submit"].kite-button--primary',
                'button[type="submit"]',
                'input[type="submit"]'
            ]);
        }

        function findStaySignedInToggle(root) {
            try {
                var labelSelectors = [
                    'label.kite-checkbox__label',
                    'label[for*="stay" i]',
                    'label'
                ];
                for (var i = 0; i < labelSelectors.length; i++) {
                    var labels = root.querySelectorAll(labelSelectors[i]);
                    for (var j = 0; j < labels.length; j++) {
                        var label = labels[j];
                        if (!isElementVisible(label)) continue;
                        var text = (label.innerText || label.textContent || '').trim();
                        if (text && /stay signed in/i.test(text)) {
                            return label;
                        }
                    }
                }
            } catch (e) {}

            return pickFirstVisible(root, [
                'input[type="checkbox"][name*="stay" i]',
                'input[type="checkbox"][id*="stay" i]',
                'input[type="checkbox"][aria-label*="stay" i]',
                'input[type="checkbox"]'
            ]);
        }

        var passwordField = pickFirstVisible(document, [
            '#kite-label-input-6',
            'input[type="password"]',
            'input[autocomplete="current-password"]',
            'input[name*="pass" i]',
            'input[id*="pass" i]'
        ]);
        var loginRoot = document;
        if (passwordField) {
            loginRoot = passwordField.form || (passwordField.closest ? passwordField.closest('form') : document) || document;
        }
        var usernameField = pickFirstVisible(loginRoot, [
            '#kite-label-input-4',
            'input[autocomplete="username"]',
            'input[name*="user" i]',
            'input[id*="user" i]',
            'input[aria-label*="user" i]',
            'input[placeholder*="user" i]',
            'input[aria-label*="email" i]',
            'input[placeholder*="email" i]',
            'input[type="email"]',
            'input[type="text"]'
        ]);
        if (usernameField === passwordField) {
            usernameField = null;
        }
        var staySignedInCheckbox = findStaySignedInToggle(loginRoot);
        if (!staySignedInCheckbox && loginRoot !== document) {
            staySignedInCheckbox = findStaySignedInToggle(document);
        }
        var signInButton = findSignInButton(loginRoot) || findButtonByText(loginRoot, /log in|login|continue/i);
        if (!signInButton && loginRoot !== document) {
            signInButton = findSignInButton(document) || findButtonByText(document, /log in|login|continue/i);
        }

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
                if (!el) return;
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
                } else if (el.tagName.toLowerCase() === 'label') {
                    var checkbox = null;
                    if (el.control) {
                        checkbox = el.control;
                    } else if (el.getAttribute) {
                        checkbox = document.getElementById(el.getAttribute('for'));
                    }
                    if (!checkbox && el.querySelector) {
                        checkbox = el.querySelector('input[type="checkbox"]');
                    }
                    if (checkbox) {
                        checkbox.checked = !checkbox.checked;
                        checkbox.dispatchEvent(new Event('input', { bubbles: true }));
                        checkbox.dispatchEvent(new Event('change', { bubbles: true }));
                        try { el.focus(); } catch (e) {}
                    } else {
                        el.click();
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
    var loginDetected = false;
    var loginCheckStart = Date.now();
    var loginCheckTimeoutMs = 30000;
    var loginCheckInterval = setInterval(function() {
        var passwordField = findVisiblePasswordField();

        if (passwordField !== null) {
            loginDetected = true;
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
            } else if (!loginDetected && Date.now() - loginCheckStart > loginCheckTimeoutMs) {
                clearInterval(loginCheckInterval);
                console.log('SpecStream: Login check stopped (not on login page)');
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

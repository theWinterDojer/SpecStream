(function() {
    if (window.__specStreamGuideInit) {
        console.log('SpecStream: Guide UI cleanup already initialized, skipping');
        return;
    }
    window.__specStreamGuideInit = true;
    console.log('SpecStream: Starting guide UI cleanup...');

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
        console.log('SpecStream: Guide auto-clicked auth button:', label);
        return true;
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

    var guideAuthStart = null;
    var guideAuthSkipLogged = false;
    var guideAuthInterval = setInterval(function() {
        try {
            if (findVisiblePasswordField() !== null) {
                if (!guideAuthSkipLogged) {
                    console.log('SpecStream: Login page detected, skipping guide auth auto-click');
                    guideAuthSkipLogged = true;
                }
                return;
            }
            if (guideAuthStart === null) {
                guideAuthStart = Date.now();
                console.log('SpecStream: Guide auth auto-click timer started');
            }
            clickAuthButtons();
            if (guideAuthStart !== null && Date.now() - guideAuthStart > 60000) {
                clearInterval(guideAuthInterval);
                console.log('SpecStream: Guide auth auto-click timeout reached');
            }
        } catch (e) {}
    }, 1000);

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

    var cleanupRuns = 0;
    var cleanupSlowed = false;
    var cleanupInterval;

    function runGuideCleanup() {
        cleanupRuns++;
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
                return;
            }

            if (!cleanupSlowed && cleanupRuns >= 10) {
                cleanupSlowed = true;
                clearInterval(cleanupInterval);
                cleanupInterval = setInterval(runGuideCleanup, 1500);
                console.log('SpecStream: Slowing guide cleanup interval');
            }

        } catch (e) {
            console.log('SpecStream: Error in guide cleanup:', e);
        }
    }

    cleanupInterval = setInterval(runGuideCleanup, 500); // Check every 500ms for faster guide interactivity

    // Stop cleanup after 15 seconds
    setTimeout(function() {
        clearInterval(cleanupInterval);
        console.log('SpecStream: Guide cleanup timeout reached');
    }, 15000);

})();

package com.example.shortsblocker;

import android.accessibilityservice.AccessibilityService;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

public class ShortsBlockerService extends AccessibilityService {

    // --- CONFIGURATION ---
    private static final long ALLOWED_TIME_MS = 60 * 1000; // 1 Minute Limit
    private static final long BLOCK_DURATION_MS = 20 * 60 * 1000; // 20 Minutes Penalty
    private static final long RESET_TIMEOUT_MS = 10 * 1000; // 10 Seconds Memory

    // VARIABLES
    private long accumulatedTime = 0; 
    private long lastTimeShortsSeen = 0; 
    private int screenWidth = 0; 
    
    private SharedPreferences prefs;
    private Handler handler;
    private Runnable heartbeatRunnable;
    private boolean isLoopRunning = false;

    @Override
    public void onServiceConnected() {
        prefs = getSharedPreferences("SaneelAI_Prefs", MODE_PRIVATE);
        handler = new Handler(Looper.getMainLooper());
        
        // Calculate Screen Width
        screenWidth = getResources().getDisplayMetrics().widthPixels;
        
        showToast("Saneel.AI: Blocking Active");
        startHeartbeat();
    }

    private void startHeartbeat() {
        if (isLoopRunning) return;
        isLoopRunning = true;

        heartbeatRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    checkScreenState();
                } catch (Exception e) {}
                if (handler != null) {
                    handler.postDelayed(this, 1000);
                }
            }
        };
        handler.post(heartbeatRunnable);
    }

    private void checkScreenState() {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) return;

        // BATTERY GUARD
        CharSequence packageName = rootNode.getPackageName();
        if (packageName == null || !packageName.toString().equals("com.google.android.youtube")) {
            accumulatedTime = 0;
            return; 
        }

        // SCANNING (Precision Check)
        boolean isShorts = scanForRightSideLikeButton(rootNode);
        long now = System.currentTimeMillis();

        if (isShorts) {
            lastTimeShortsSeen = now;
            accumulatedTime += 1000; 
            
            if (accumulatedTime % 10000 == 0) {
                showToast("Used: " + (accumulatedTime/1000) + "s / 60s");
            }
        } else {
            if ((now - lastTimeShortsSeen) > RESET_TIMEOUT_MS) {
                accumulatedTime = 0;
            }
        }

        checkTimeLimit(isShorts);
    }

    private void checkTimeLimit(boolean isShortsCurrent) {
        long now = System.currentTimeMillis();
        long blockUntil = prefs.getLong("block_until", 0);

        if (now < blockUntil) {
            if (isShortsCurrent) {
                performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
                if (accumulatedTime > 0) { 
                    long minsLeft = (blockUntil - now) / 60000;
                    showToast("Blocked! " + (minsLeft + 1) + "m left.");
                }
            }
            accumulatedTime = 0;
            return;
        }

        if (accumulatedTime >= ALLOWED_TIME_MS) {
            performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
            performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK); 

            long newBlockUntil = now + BLOCK_DURATION_MS;
            prefs.edit().putLong("block_until", newBlockUntil).apply();
            
            showToast("Limit Reached! Blocked for 20 mins.");
            accumulatedTime = 0; 
        }
    }

    // --- PRECISION SCANNER ---
    // We ONLY look for "Like" or "Dislike".
    // We IGNORE "Share", "Remix", "Settings", "Cast".
    private boolean scanForRightSideLikeButton(AccessibilityNodeInfo node) {
        if (node == null) return false;

        if (node.isVisibleToUser()) {
            CharSequence descSeq = node.getContentDescription();
            CharSequence textSeq = node.getText();
            
            String label = "";
            if (descSeq != null) label += descSeq.toString().toLowerCase();
            if (textSeq != null) label += textSeq.toString().toLowerCase();

            // STRICT KEYWORDS: Only Like/Dislike
            // We specifically check for "like this" to avoid "Likely to recommend" etc.
            if (label.contains("like this") || label.contains("dislike this")) {
                
                // CHECK POSITION
                Rect rect = new Rect();
                node.getBoundsInScreen(rect);
                
                // STRICT GEOMETRY: Must be past 80% of screen width (Very far right)
                // Normal videos buttons are on the left (0-50%).
                if (rect.left > (screenWidth * 0.80)) {
                    return true; // Found a Like button on the edge!
                }
            }
        }

        // Recursion
        int count = node.getChildCount();
        for (int i = 0; i < count; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                if (scanForRightSideLikeButton(child)) {
                    child.recycle();
                    return true;
                }
                child.recycle();
            }
        }
        return false;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) { }

    private void showToast(String message) {
        Toast.makeText(ShortsBlockerService.this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onInterrupt() { 
        if (handler != null && heartbeatRunnable != null) {
            handler.removeCallbacks(heartbeatRunnable);
        }
        isLoopRunning = false;
    }
    }

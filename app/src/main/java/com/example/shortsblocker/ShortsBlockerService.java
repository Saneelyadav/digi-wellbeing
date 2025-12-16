package com.example.shortsblocker;

import android.accessibilityservice.AccessibilityService;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;
import java.util.List;

public class ShortsBlockerService extends AccessibilityService {

    // --- CONFIGURATION ---
    private static final long ALLOWED_TIME_MS = 60 * 1000; // 1 Minute Limit
    private static final long BLOCK_DURATION_MS = 20 * 60 * 1000; // 20 Minutes Penalty
    private static final long RESET_TIMEOUT_MS = 10 * 1000; // 10 Seconds Memory

    // VARIABLES
    private long accumulatedTime = 0; 
    private long lastTimeShortsSeen = 0; 
    private int screenWidth = 0; // To calculate Right Side
    
    private SharedPreferences prefs;
    private Handler handler;
    private Runnable heartbeatRunnable;
    private boolean isLoopRunning = false;

    @Override
    public void onServiceConnected() {
        prefs = getSharedPreferences("SaneelAI_Prefs", MODE_PRIVATE);
        handler = new Handler(Looper.getMainLooper());
        
        // 1. Calculate Screen Width
        screenWidth = getResources().getDisplayMetrics().widthPixels;
        
        showToast("Saneel.AI: Active (Right-Side Detector)");
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

        // 2. BATTERY GUARD
        CharSequence packageName = rootNode.getPackageName();
        if (packageName == null || !packageName.toString().equals("com.google.android.youtube")) {
            accumulatedTime = 0;
            return; 
        }

        // 3. SCANNING (Right-Side Buttons)
        boolean isShorts = isRightSideButtonVisible(rootNode);
        long now = System.currentTimeMillis();

        if (isShorts) {
            lastTimeShortsSeen = now;
            accumulatedTime += 1000; 
            
            if (accumulatedTime % 10000 == 0) {
                showToast("Used: " + (accumulatedTime/1000) + "s / 60s");
            }
        } else {
            // Only reset if gone for > 10 seconds
            if ((now - lastTimeShortsSeen) > RESET_TIMEOUT_MS) {
                accumulatedTime = 0;
            }
        }

        // 4. CHECK LIMITS
        checkTimeLimit(isShorts);
    }

    private void checkTimeLimit(boolean isShortsCurrent) {
        long now = System.currentTimeMillis();
        long blockUntil = prefs.getLong("block_until", 0);

        // PENALTY BOX
        if (now < blockUntil) {
            // CRITICAL: Only block if buttons are currently on the Right Side
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

        // TIME IS UP
        if (accumulatedTime >= ALLOWED_TIME_MS) {
            performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
            performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK); 

            long newBlockUntil = now + BLOCK_DURATION_MS;
            prefs.edit().putLong("block_until", newBlockUntil).apply();
            
            showToast("Limit Reached! Blocked for 20 mins.");
            accumulatedTime = 0; 
        }
    }

    // --- NEW DETECTOR: Right-Side Geometry Check ---
    private boolean isRightSideButtonVisible(AccessibilityNodeInfo root) {
        // Check for any of these buttons sticking to the right edge
        if (checkButtonPosition(root, "Share")) return true;
        if (checkButtonPosition(root, "Like")) return true;
        if (checkButtonPosition(root, "Dislike")) return true;
        if (checkButtonPosition(root, "Comment")) return true;
        return false;
    }

    private boolean checkButtonPosition(AccessibilityNodeInfo root, String text) {
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(text);
        
        for (AccessibilityNodeInfo node : nodes) {
            if (!node.isVisibleToUser()) continue;

            Rect rect = new Rect();
            node.getBoundsInScreen(rect);
            
            // GEOMETRY MATH:
            // If the button starts past 60% of the screen width, it is on the Right Side.
            // (Shorts buttons are pinned to the right edge. Normal video buttons are Left/Center)
            if (rect.left > (screenWidth * 0.60)) {
                return true;
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

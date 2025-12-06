package com.example.shortsblocker;

import android.accessibilityservice.AccessibilityService;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

public class ShortsBlockerService extends AccessibilityService {

    // --- CONFIGURATION ---
    private static final long ALLOWED_TIME_MS = 60 * 1000; // 1 Minute
    private static final long BLOCK_DURATION_MS = 20 * 60 * 1000; // 20 Minutes
    
    // VARIABLES
    private long accumulatedTime = 0; 
    private SharedPreferences prefs;
    private Handler handler;
    private Runnable heartbeatRunnable;
    private boolean isLoopRunning = false;

    @Override
    public void onServiceConnected() {
        prefs = getSharedPreferences("SaneelAI_Prefs", MODE_PRIVATE);
        handler = new Handler(Looper.getMainLooper());
        
        startHeartbeat();
        showToast("Saneel.AI: Battery Optimized Mode");
    }

    private void startHeartbeat() {
        if (isLoopRunning) return;
        isLoopRunning = true;

        heartbeatRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    checkScreenState();
                } catch (Exception e) {
                    // Prevent crashes
                }
                // Check again in 1 second
                handler.postDelayed(this, 1000); 
            }
        };
        handler.post(heartbeatRunnable);
    }

    private void checkScreenState() {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) return;

        // --- BATTERY SAVER CHECK ---
        // Before we do any heavy scanning, check WHICH APP is open.
        CharSequence packageName = rootNode.getPackageName();
        
        // If we are NOT in YouTube, stop immediately. Don't waste power.
        if (packageName == null || !packageName.toString().equals("com.google.android.youtube")) {
            // We are in WhatsApp, Home Screen, or Phone is locked.
            // Reset the internal timer because user is definitely not watching Shorts.
            accumulatedTime = 0;
            return; 
        }

        // --- IF WE ARE HERE, YOUTUBE IS OPEN ---
        // Now it is worth spending battery to scan for the button.

        boolean isShorts = isRemixButtonVisible(rootNode);

        if (isShorts) {
            accumulatedTime += 1000; // Add 1 second
            
            // Show status every 10s
            if (accumulatedTime % 10000 == 0) {
                showToast("Saneel.AI: " + (accumulatedTime/1000) + "s / 60s");
            }
        } else {
            // YouTube is open, but we don't see "Remix" (probably Home screen)
            accumulatedTime = 0;
        }

        checkTimeLimit();
    }

    private void checkTimeLimit() {
        long now = System.currentTimeMillis();
        long blockUntil = prefs.getLong("block_until", 0);

        // --- A: PENALTY BOX ---
        if (now < blockUntil) {
            performGlobalAction(GLOBAL_ACTION_BACK);
            if (accumulatedTime > 0) { 
                long minsLeft = (blockUntil - now) / 60000;
                showToast("Blocked! " + (minsLeft + 1) + "m penalty left.");
            }
            accumulatedTime = 0;
            return;
        }

        // --- B: TIME IS UP ---
        if (accumulatedTime >= ALLOWED_TIME_MS) {
            performGlobalAction(GLOBAL_ACTION_BACK);
            performGlobalAction(GLOBAL_ACTION_BACK); // Double Kick

            long newBlockUntil = now + BLOCK_DURATION_MS;
            prefs.edit().putLong("block_until", newBlockUntil).apply();
            
            showToast("1 Minute Reached! See you in 20 mins.");
            accumulatedTime = 0; 
        }
    }

    private boolean isRemixButtonVisible(AccessibilityNodeInfo node) {
        if (node == null) return false;
        
        CharSequence textSeq = node.getText();
        CharSequence descSeq = node.getContentDescription();
        String text = (textSeq != null) ? textSeq.toString().toLowerCase() : "";
        String desc = (descSeq != null) ? descSeq.toString().toLowerCase() : "";
        
        if ((text.contains("remix") || desc.contains("remix")) && node.isVisibleToUser()) {
            return true;
        }

        int count = node.getChildCount();
        for (int i = 0; i < count; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                if (isRemixButtonVisible(child)) {
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
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onInterrupt() { 
        if (handler != null && heartbeatRunnable != null) {
            handler.removeCallbacks(heartbeatRunnable);
        }
        isLoopRunning = false;
    }
}

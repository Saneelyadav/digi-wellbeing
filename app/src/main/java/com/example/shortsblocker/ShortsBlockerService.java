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
    private static final long ALLOWED_TIME_MS = 60 * 1000; // 1 Minute Limit
    private static final long BLOCK_DURATION_MS = 20 * 60 * 1000; // 20 Minutes Penalty
    private static final long RESET_TIMEOUT_MS = 10 * 1000; // 10 Seconds Memory

    // VARIABLES
    private long accumulatedTime = 0; 
    private long lastTimeRemixSeen = 0; 
    
    private SharedPreferences prefs;
    private Handler handler;
    private Runnable heartbeatRunnable;
    private boolean isLoopRunning = false;

    @Override
    public void onServiceConnected() {
        prefs = getSharedPreferences("SaneelAI_Prefs", MODE_PRIVATE);
        handler = new Handler(Looper.getMainLooper());
        
        showToast("Saneel.AI: Active (Targeted Blocking)");
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

        // 1. BATTERY GUARD
        CharSequence packageName = rootNode.getPackageName();
        if (packageName == null || !packageName.toString().equals("com.google.android.youtube")) {
            accumulatedTime = 0;
            return; 
        }

        // 2. SCANNING
        boolean isShorts = isRemixButtonVisible(rootNode);
        long now = System.currentTimeMillis();

        if (isShorts) {
            lastTimeRemixSeen = now;
            accumulatedTime += 1000; 
            
            if (accumulatedTime % 10000 == 0) {
                showToast("Used: " + (accumulatedTime/1000) + "s / 60s");
            }
        } else {
            // Only reset if gone for > 10 seconds
            if ((now - lastTimeRemixSeen) > RESET_TIMEOUT_MS) {
                accumulatedTime = 0;
            }
        }

        // 3. CHECK LIMITS (Pass 'isShorts' so we know WHAT to block)
        checkTimeLimit(isShorts);
    }

    private void checkTimeLimit(boolean isShortsCurrent) {
        long now = System.currentTimeMillis();
        long blockUntil = prefs.getLong("block_until", 0);

        // --- PENALTY BOX LOGIC ---
        if (now < blockUntil) {
            // CRITICAL FIX: Only block if user is CURRENTLY trying to watch a Short
            if (isShortsCurrent) {
                performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
                
                // Show message
                long minsLeft = (blockUntil - now) / 60000;
                showToast("Blocked! " + (minsLeft + 1) + "m penalty left.");
            }
            // If isShortsCurrent is FALSE (Home screen), we do NOTHING.
            
            accumulatedTime = 0;
            return;
        }

        // --- TIME IS UP LOGIC ---
        if (accumulatedTime >= ALLOWED_TIME_MS) {
            performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
            performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK); 

            long newBlockUntil = now + BLOCK_DURATION_MS;
            prefs.edit().putLong("block_until", newBlockUntil).apply();
            
            showToast("Limit Reached! Blocked for 20 mins.");
            accumulatedTime = 0; 
        }
    }

    // Recursive Scanner
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

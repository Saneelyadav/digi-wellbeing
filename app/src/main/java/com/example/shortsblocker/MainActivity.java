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
    private static final long ALLOWED_TIME_MS = 60 * 1000; // 1 Minute Total
    private static final long BLOCK_DURATION_MS = 20 * 60 * 1000; // 20 Minutes Penalty
    
    // THE FIX: Wait 10 seconds before resetting the timer
    private static final long RESET_TIMEOUT_MS = 10 * 1000; 
    
    // VARIABLES
    private long accumulatedTime = 0; 
    private long lastTimeRemixSeen = 0; // Timestamp when we last saw the button
    
    private SharedPreferences prefs;
    private Handler handler;
    private Runnable heartbeatRunnable;
    private boolean isLoopRunning = false;

    @Override
    public void onServiceConnected() {
        prefs = getSharedPreferences("SaneelAI_Prefs", MODE_PRIVATE);
        handler = new Handler(Looper.getMainLooper());
        
        startHeartbeat();
        showToast("Saneel.AI: Continuous Timer Loaded");
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
                handler.postDelayed(this, 1000); 
            }
        };
        handler.post(heartbeatRunnable);
    }

    private void checkScreenState() {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) return;

        // 1. BATTERY GUARD: If not YouTube, Reset & Sleep
        CharSequence packageName = rootNode.getPackageName();
        if (packageName == null || !packageName.toString().equals("com.google.android.youtube")) {
            accumulatedTime = 0;
            return; 
        }

        // 2. SCANNING
        boolean isShorts = isRemixButtonVisible(rootNode);
        long now = System.currentTimeMillis();

        if (isShorts) {
            // We found the button!
            lastTimeRemixSeen = now;
            accumulatedTime += 1000; // Add 1 second
            
            // Toast status every 10s
            if (accumulatedTime % 10000 == 0) {
                showToast("Used: " + (accumulatedTime/1000) + "s / 60s");
            }
        } else {
            // We DON'T see the button (Maybe scrolling?)
            // LOGIC FIX: Only reset if it's been gone for > 10 seconds
            if ((now - lastTimeRemixSeen) > RESET_TIMEOUT_MS) {
                accumulatedTime = 0;
            }
            // If it's been less than 10 seconds, we DO NOTHING. 
            // We keep 'accumulatedTime' exactly as it is.
        }

        // 3. CHECK LIMITS
        checkTimeLimit();
    }

    private void checkTimeLimit() {
        long now = System.currentTimeMillis();
        long blockUntil = prefs.getLong("block_until", 0);

        // --- PENALTY BOX ---
        if (now < blockUntil) {
            performGlobalAction(GLOBAL_ACTION_BACK);
            if (accumulatedTime > 0) { 
                long minsLeft = (blockUntil - now) / 60000;
                showToast("Blocked! " + (minsLeft + 1) + "m penalty left.");
            }
            accumulatedTime = 0;
            return;
        }

        // --- TIME IS UP ---
        if (accumulatedTime >= ALLOWED_TIME_MS) {
            performGlobalAction(GLOBAL_ACTION_BACK);
            performGlobalAction(GLOBAL_ACTION_BACK); 

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

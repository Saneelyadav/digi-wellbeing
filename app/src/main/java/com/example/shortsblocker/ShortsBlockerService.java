package com.example.shortsblocker;

import android.accessibilityservice.AccessibilityService;
import android.content.SharedPreferences;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;
import java.util.List;

public class ShortsBlockerService extends AccessibilityService {

    // --- CONFIGURATION ---
    private static final long ALLOWED_TIME_MS = 60 * 1000; // 1 Minute
    private static final long BLOCK_DURATION_MS = 20 * 60 * 1000; // 20 Minutes

    private long currentSessionStart = 0;
    private SharedPreferences prefs;
    private long lastToastTime = 0;

    @Override
    public void onServiceConnected() {
        prefs = getSharedPreferences("SaneelAI_Prefs", MODE_PRIVATE);
        Toast.makeText(this, "Saneel.AI: Remix Detector Loaded", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) return;

        // --- THE "REMIX" STRATEGY ---
        // We strictly look for the "Remix" button.
        // It is the most unique button on the Shorts overlay.
        
        boolean isShorts = false;
        
        List<AccessibilityNodeInfo> remixNodes = rootNode.findAccessibilityNodeInfosByText("Remix");
        
        // We must loop through results to ensure it's actually visible
        // (sometimes hidden elements exist in the background)
        for (AccessibilityNodeInfo node : remixNodes) {
            if (node.isVisibleToUser()) {
                isShorts = true;
                break;
            }
        }

        if (isShorts) {
            handleShortsWatching();
        } else {
            // "Remix" button is gone. User is safe.
            currentSessionStart = 0;
        }
    }

    private void handleShortsWatching() {
        long now = System.currentTimeMillis();
        long blockUntil = prefs.getLong("block_until", 0);

        // --- SCENARIO A: Penalty Box ---
        if (now < blockUntil) {
            performGlobalAction(GLOBAL_ACTION_BACK);
            
            long minsLeft = (blockUntil - now) / 60000;
            if (now - lastToastTime > 5000) { 
                showToast("Cooldown Active: " + (minsLeft + 1) + "m left");
                lastToastTime = now;
            }
            return;
        }

        // --- SCENARIO B: Watching Timer ---
        if (currentSessionStart == 0) {
            currentSessionStart = now;
            showToast("Shorts Detected. 1 min remaining.");
        }

        long timeSpent = now - currentSessionStart;

        if (timeSpent > ALLOWED_TIME_MS) {
            performGlobalAction(GLOBAL_ACTION_BACK);
            
            long newBlockUntil = now + BLOCK_DURATION_MS;
            prefs.edit().putLong("block_until", newBlockUntil).apply();
            
            showToast("Time's up! Blocked for 20 mins.");
            currentSessionStart = 0;
        }
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onInterrupt() {
    }
}

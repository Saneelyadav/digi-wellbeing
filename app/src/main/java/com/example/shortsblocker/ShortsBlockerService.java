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
    private long lastToastTime = 0; // To prevent spamming toasts

    @Override
    public void onServiceConnected() {
        prefs = getSharedPreferences("SaneelAI_Prefs", MODE_PRIVATE);
        showToast("Saneel.AI Active: Scanning for Shorts...");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) return;

        // --- DETECTION LOGIC ---
        
        // 1. Check for "Shorts" text (General Indicator)
        boolean hasShortsText = !rootNode.findAccessibilityNodeInfosByText("Shorts").isEmpty();
        
        // 2. Check for "Home" or "Create" (Bottom Nav Indicators)
        boolean hasHomeButton = !rootNode.findAccessibilityNodeInfosByText("Home").isEmpty();
        boolean hasCreateButton = !rootNode.findAccessibilityNodeInfosByText("Create").isEmpty();
        boolean isNavBarVisible = hasHomeButton || hasCreateButton;

        // 3. Check for "Remix" (Strong Shorts Indicator)
        boolean hasRemixButton = !rootNode.findAccessibilityNodeInfosByText("Remix").isEmpty();

        // --- DECISION ---
        boolean isWatchingShorts = false;

        if (hasRemixButton) {
            // "Remix" button is almost exclusively on Shorts
            isWatchingShorts = true;
        } else if (hasShortsText && !isNavBarVisible) {
            // Classic check: "Shorts" is on screen, but Bottom Nav is gone
            isWatchingShorts = true;
        }

        // --- ACTION ---
        if (isWatchingShorts) {
            handleShortsWatching();
        } else {
            // Safe (Home screen, Long video, etc.)
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
            // Show toast only every 5 seconds
            if (now - lastToastTime > 5000) { 
                showToast("Cooldown Active: " + (minsLeft + 1) + "m left");
                lastToastTime = now;
            }
            return;
        }

        // --- SCENARIO B: Watching Timer ---
        if (currentSessionStart == 0) {
            currentSessionStart = now;
            showToast("Timer Started: 1 min allowed");
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

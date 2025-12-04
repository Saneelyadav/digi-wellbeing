package com.example.shortsblocker;

import android.accessibilityservice.AccessibilityService;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.content.res.Resources;
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
    private int screenWidth = 0;

    @Override
    public void onServiceConnected() {
        prefs = getSharedPreferences("SaneelAI_Prefs", MODE_PRIVATE);
        // Get the width of the phone screen
        screenWidth = Resources.getSystem().getDisplayMetrics().widthPixels;
        showToast("Saneel.AI: Geometry Tracker Loaded");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) return;

        boolean isWatchingShorts = false;

        // --- STRATEGY: CHECK BUTTON POSITIONS ---
        // On Shorts, "Like", "Dislike", "Comment", and "Share" are on the right edge.
        // We look for ANY of these.
        
        if (checkIfButtonIsOnRightEdge(rootNode, "Like")) isWatchingShorts = true;
        else if (checkIfButtonIsOnRightEdge(rootNode, "Share")) isWatchingShorts = true;
        else if (checkIfButtonIsOnRightEdge(rootNode, "Comment")) isWatchingShorts = true;
        else if (checkIfButtonIsOnRightEdge(rootNode, "Remix")) isWatchingShorts = true;

        // --- ACTION ---
        if (isWatchingShorts) {
            handleShortsWatching();
        } else {
            // Buttons are not on the right edge. Must be Home or Normal video.
            currentSessionStart = 0;
        }
    }

    // Helper function to check if a specific button is on the Right Side
    private boolean checkIfButtonIsOnRightEdge(AccessibilityNodeInfo root, String text) {
        // Search for nodes with this text (or Description)
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(text);
        
        for (AccessibilityNodeInfo node : nodes) {
            if (!node.isVisibleToUser()) continue;

            // Get the position of the button
            Rect outBounds = new Rect();
            node.getBoundsInScreen(outBounds);
            
            // GEOMETRY CHECK:
            // If the Left side of the button is past 75% of the screen width,
            // it is "Sticking to the Right Edge".
            if (outBounds.left > (screenWidth * 0.75)) {
                return true; // Found it on the right!
            }
        }
        return false;
    }

    private void handleShortsWatching() {
        long now = System.currentTimeMillis();
        long blockUntil = prefs.getLong("block_until", 0);

        // --- SCENARIO A: Penalty Box (20 Mins) ---
        if (now < blockUntil) {
            performGlobalAction(GLOBAL_ACTION_BACK);
            
            long minsLeft = (blockUntil - now) / 60000;
            if (now - lastToastTime > 5000) { 
                showToast("Saneel.AI: Cooldown (" + (minsLeft + 1) + "m left)");
                lastToastTime = now;
            }
            return;
        }

        // --- SCENARIO B: Watching Timer (1 Min) ---
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

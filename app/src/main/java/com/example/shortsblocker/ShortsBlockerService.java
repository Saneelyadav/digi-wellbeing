package com.example.shortsblocker;

import android.accessibilityservice.AccessibilityService;
import android.content.SharedPreferences;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

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
        showToast("Saneel.AI: Remix Scanner Loaded");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) return;

        // --- THE ROBUST REMIX SCANNER ---
        // We recursively search every element on screen for the word "remix"
        
        if (isRemixButtonVisible(rootNode)) {
            handleShortsWatching();
        } else {
            // Remix button not found. Safe.
            currentSessionStart = 0;
        }
    }

    // Recursive function that digs deep into the layout
    private boolean isRemixButtonVisible(AccessibilityNodeInfo node) {
        if (node == null) return false;

        // 1. Get Text and Description
        CharSequence textSeq = node.getText();
        CharSequence descSeq = node.getContentDescription();

        String text = (textSeq != null) ? textSeq.toString().toLowerCase() : "";
        String desc = (descSeq != null) ? descSeq.toString().toLowerCase() : "";

        // 2. Check for "remix"
        // We check if it CONTAINS "remix", so "Remix this video" also triggers it.
        boolean match = text.contains("remix") || desc.contains("remix");

        // 3. Verify it is visible
        if (match && node.isVisibleToUser()) {
            return true; // FOUND IT!
        }

        // 4. If not found here, check all children (The Loop)
        int count = node.getChildCount();
        for (int i = 0; i < count; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                if (isRemixButtonVisible(child)) {
                    child.recycle(); // Clean up memory
                    return true;
                }
                child.recycle();
            }
        }
        return false;
    }

    private void handleShortsWatching() {
        long now = System.currentTimeMillis();
        long blockUntil = prefs.getLong("block_until", 0);

        // --- SCENARIO A: Penalty Box ---
        if (now < blockUntil) {
            performGlobalAction(GLOBAL_ACTION_BACK);
            
            long minsLeft = (blockUntil - now) / 60000;
            if (now - lastToastTime > 5000) { 
                showToast("Saneel.AI: Cooldown (" + (minsLeft + 1) + "m left)");
                lastToastTime = now;
            }
            return;
        }

        // --- SCENARIO B: Watching Timer ---
        if (currentSessionStart == 0) {
            currentSessionStart = now;
            showToast("Shorts Detected (Remix Found)");
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
        // Only toast on the main thread to prevent crashes
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onInterrupt() {
    }
}

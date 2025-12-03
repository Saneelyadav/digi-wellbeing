package com.example.shortsblocker;

import android.accessibilityservice.AccessibilityService;
import android.content.SharedPreferences;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

public class ShortsBlockerService extends AccessibilityService {

    // --- CONFIGURATION ---
    // 1 Minute (in milliseconds)
    private static final long ALLOWED_TIME_MS = 60 * 1000; 
    // 20 Minutes (in milliseconds)
    private static final long BLOCK_DURATION_MS = 20 * 60 * 1000;

    private long currentSessionStart = 0;
    private SharedPreferences prefs;

    @Override
    public void onServiceConnected() {
        prefs = getSharedPreferences("SaneelAI_Prefs", MODE_PRIVATE);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) return;

        // NEW DETECTION METHOD: Recursive ID Scan
        // We look for internal IDs named "reel" (YouTube's code name for Shorts)
        if (isShortsPlayerVisible(rootNode)) {
            handleShortsWatching();
        } else {
            // Not watching Shorts? Reset the 1-minute timer (but NOT the 20-min penalty)
            currentSessionStart = 0;
        }
    }

    // This function recursively scans the screen for the "reel" player
    private boolean isShortsPlayerVisible(AccessibilityNodeInfo node) {
        if (node == null) return false;

        // 1. Check the ID of the current element
        if (node.getViewIdResourceName() != null) {
            String id = node.getViewIdResourceName().toLowerCase();
            // "reel_player" or "reel_recycler" are the specific IDs for the Shorts feed
            if (id.contains("reel_player") || id.contains("reel_recycler")) {
                return true;
            }
        }
        
        // 2. Also Check Description (Backup method)
        if (node.getContentDescription() != null) {
            String desc = node.getContentDescription().toString().toLowerCase();
            // "shorts player" is sometimes used in accessibility descriptions
            if (desc.contains("shorts player")) {
                return true;
            }
        }

        // 3. Scan all children (Recursive)
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (isShortsPlayerVisible(child)) {
                return true;
            }
        }
        return false;
    }

    private void handleShortsWatching() {
        long now = System.currentTimeMillis();
        long blockUntil = prefs.getLong("block_until", 0);

        // --- SCENARIO A: The Penalty Box (20 Mins) ---
        if (now < blockUntil) {
            performGlobalAction(GLOBAL_ACTION_BACK);
            
            long minsLeft = (blockUntil - now) / 60000;
            if (now % 5000 < 100) { 
                Toast.makeText(this, "Saneel.AI: Cooldown active. Wait " + (minsLeft + 1) + " mins.", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        // --- SCENARIO B: Watching (1 Min Timer) ---
        if (currentSessionStart == 0) {
            currentSessionStart = now; 
        }

        long timeSpent = now - currentSessionStart;

        if (timeSpent > ALLOWED_TIME_MS) {
            // TIME IS UP!
            performGlobalAction(GLOBAL_ACTION_BACK);
            
            // Set 20 minute penalty
            long newBlockUntil = now + BLOCK_DURATION_MS;
            prefs.edit().putLong("block_until", newBlockUntil).apply();
            
            Toast.makeText(this, "1 min limit reached! See you in 20 mins.", Toast.LENGTH_LONG).show();
            currentSessionStart = 0;
        }
    }

    @Override
    public void onInterrupt() {
    }
}

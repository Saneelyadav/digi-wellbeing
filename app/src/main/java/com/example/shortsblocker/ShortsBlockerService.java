package com.example.shortsblocker;

import android.accessibilityservice.AccessibilityService;
import android.content.SharedPreferences;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

public class ShortsBlockerService extends AccessibilityService {

    // --- CONFIGURATION ---
    private static final long ALLOWED_TIME_MS = 60 * 1000; // 1 Minute Total Budget
    private static final long BLOCK_DURATION_MS = 20 * 60 * 1000; // 20 Minutes Penalty
    private static final long SESSION_RESET_TIMEOUT = 10 * 1000; // Must leave for 10s to reset timer

    private long accumulatedTime = 0; // The "Bank Balance" of time watched
    private long lastEventTime = 0;   // When the last screen update happened
    private long lastTimeShortsSeen = 0; // When we last saw the "Remix" button
    
    private SharedPreferences prefs;
    private long lastToastTime = 0;

    @Override
    public void onServiceConnected() {
        prefs = getSharedPreferences("SaneelAI_Prefs", MODE_PRIVATE);
        lastEventTime = System.currentTimeMillis();
        showToast("Saneel.AI: Accumulator Loaded");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) return;

        long now = System.currentTimeMillis();
        long timeSinceLastEvent = now - lastEventTime;
        lastEventTime = now;

        // 1. SCAN: Do we see the "Remix" button right now?
        if (isRemixButtonVisible(rootNode)) {
            lastTimeShortsSeen = now;
        }

        // 2. LOGIC: Are we currently "In a Session"?
        // We consider it a session if we saw "Remix" within the last 2 seconds.
        // This handles the gap when scrolling.
        boolean isWatching = (now - lastTimeShortsSeen) < 2000;

        if (isWatching) {
            // We are watching (or scrolling). ADD time to the bank.
            // We cap the delta to 1000ms to prevent huge jumps if the phone lags.
            if (timeSinceLastEvent < 1000) {
                accumulatedTime += timeSinceLastEvent;
            }
            
            checkTimeLimit(now);
        } else {
            // We are NOT watching.
            // Only reset the bank if we have been away for more than 10 seconds.
            if ((now - lastTimeShortsSeen) > SESSION_RESET_TIMEOUT) {
                accumulatedTime = 0;
            }
        }
    }

    private void checkTimeLimit(long now) {
        long blockUntil = prefs.getLong("block_until", 0);

        // --- A: PENALTY BOX ---
        if (now < blockUntil) {
            performGlobalAction(GLOBAL_ACTION_BACK);
            long minsLeft = (blockUntil - now) / 60000;
            if (now - lastToastTime > 5000) { 
                showToast("Cooldown: " + (minsLeft + 1) + "m left");
                lastToastTime = now;
            }
            return;
        }

        // --- B: TIME LIMIT CHECK ---
        
        // Show a warning at 30s and 50s
        if (accumulatedTime > 30000 && accumulatedTime < 31000 && (now - lastToastTime > 5000)) {
            showToast("30 seconds left...");
            lastToastTime = now;
        }
        
        // TIME IS UP
        if (accumulatedTime > ALLOWED_TIME_MS) {
            performGlobalAction(GLOBAL_ACTION_BACK);
            
            // Double tap back to ensure exit
            performGlobalAction(GLOBAL_ACTION_BACK);

            long newBlockUntil = now + BLOCK_DURATION_MS;
            prefs.edit().putLong("block_until", newBlockUntil).apply();
            
            showToast("1 Minute Limit Reached! Blocked for 20 mins.");
            accumulatedTime = 0; // Reset bank for next time
        }
    }

    // --- RECURSIVE SCANNER (Standard) ---
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

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onInterrupt() { }
}

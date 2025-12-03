package com.example.shortsblocker;

import android.accessibilityservice.AccessibilityService;
import android.content.SharedPreferences;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;
import java.util.List;

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
        // Load memory (to remember the 20 min block even if app closes)
        prefs = getSharedPreferences("SaneelAI_Prefs", MODE_PRIVATE);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) return;

        // --- STEP 1: SAFETY CHECK ---
        // We look for "Shorts" text BUT ensure "Home" button is missing.
        
        // Is the word "Shorts" visible anywhere?
        boolean hasShortsText = !rootNode.findAccessibilityNodeInfosByText("Shorts").isEmpty();
        
        // Is the "Home" navigation button visible? (It is ALWAYS visible on the main menu)
        boolean hasHomeButton = !rootNode.findAccessibilityNodeInfosByText("Home").isEmpty();

        // LOGIC: If we see Shorts, but NO Home button, we are definitely in the player.
        if (hasShortsText && !hasHomeButton) {
            handleShortsWatching();
        } else {
            // If the Home button is visible, we are safe. Reset the "watching" timer.
            // (We do NOT reset the penalty timer though!)
            currentSessionStart = 0;
        }
    }

    private void handleShortsWatching() {
        long now = System.currentTimeMillis();
        long blockUntil = prefs.getLong("block_until", 0);

        // --- SCENARIO A: The Penalty Box (20 Mins) ---
        // If the current time is BEFORE the "unblock time", kick them out.
        if (now < blockUntil) {
            performGlobalAction(GLOBAL_ACTION_BACK);
            
            long minsLeft = (blockUntil - now) / 60000;
            // Only show toast every few seconds to avoid spamming
            if (now % 5000 < 100) { 
                Toast.makeText(this, "Saneel.AI: Cooldown active. Wait " + (minsLeft + 1) + " mins.", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        // --- SCENARIO B: Watching (1 Min Timer) ---
        if (currentSessionStart == 0) {
            currentSessionStart = now; // Start the timer NOW
        }

        long timeSpent = now - currentSessionStart;

        // Check if 1 minute has passed
        if (timeSpent > ALLOWED_TIME_MS) {
            // Time is up! Press Back button.
            performGlobalAction(GLOBAL_ACTION_BACK);
            
            // Set the 20 minute penalty in the future
            long newBlockUntil = now + BLOCK_DURATION_MS;
            prefs.edit().putLong("block_until", newBlockUntil).apply();
            
            Toast.makeText(this, "1 min limit reached! See you in 20 mins.", Toast.LENGTH_LONG).show();
            
            // Reset watching timer
            currentSessionStart = 0;
        }
    }

    @Override
    public void onInterrupt() {
        // Required method
    }
}

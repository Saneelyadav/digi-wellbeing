package com.example.shortsblocker;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.util.Log;
import java.util.List;

public class ShortsBlockerService extends AccessibilityService {

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (getRootInActiveWindow() == null) return;

        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        
        // Look for "Shorts" text
        List<AccessibilityNodeInfo> nodes = rootNode.findAccessibilityNodeInfosByText("Shorts");
        
        for (AccessibilityNodeInfo node : nodes) {
            if (node.isVisibleToUser()) {
                // If found, press BACK
                performGlobalAction(GLOBAL_ACTION_BACK);
                break;
            }
        }
    }

    @Override
    public void onInterrupt() {
    }
}

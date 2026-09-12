package com.aadityasamriya.needle2;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayList;
import java.util.List;

/** User-enabled Android UI automation bridge. */
public class AutomationAccessibilityService extends AccessibilityService {
    private static volatile AutomationAccessibilityService instance;

    public static AutomationAccessibilityService getInstance() { return instance; }

    @Override public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override public void onAccessibilityEvent(android.view.accessibility.AccessibilityEvent event) { }
    @Override public void onInterrupt() { }

    @Override public boolean onUnbind(Intent intent) {
        if (instance == this) instance = null;
        return super.onUnbind(intent);
    }

    public JSONObjectResult tapText(String text) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return JSONObjectResult.error("No active window");
        AccessibilityNodeInfo node = findText(root, text);
        if (node == null) return JSONObjectResult.error("Text not found: " + text);
        boolean ok = node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        return ok ? JSONObjectResult.ok("clicked", text) : JSONObjectResult.error("Click failed: " + text);
    }

    public JSONObjectResult typeText(String text) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return JSONObjectResult.error("No active window");
        AccessibilityNodeInfo node = findFocusedEditable(root);
        if (node == null) node = findFirstEditable(root);
        if (node == null) return JSONObjectResult.error("No editable field found");
        Bundle b = new Bundle();
        b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
        boolean ok = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b);
        return ok ? JSONObjectResult.ok("typed", text) : JSONObjectResult.error("Could not set text");
    }

    public JSONObjectResult tapXY(float x, float y) {
        Path path = new Path(); path.moveTo(x, y);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 60)).build();
        boolean ok = dispatchGesture(gesture, null, null);
        return ok ? JSONObjectResult.ok("tapped", x + "," + y) : JSONObjectResult.error("Gesture dispatch failed");
    }

    public JSONObjectResult swipe(float x1, float y1, float x2, float y2, long duration) {
        Path path = new Path(); path.moveTo(x1, y1); path.lineTo(x2, y2);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, Math.max(100, Math.min(duration, 2000)))).build();
        boolean ok = dispatchGesture(gesture, null, null);
        return ok ? JSONObjectResult.ok("swiped", "ok") : JSONObjectResult.error("Swipe failed");
    }

    public JSONObjectResult back() {
        return performGlobalAction(GLOBAL_ACTION_BACK) ? JSONObjectResult.ok("back", "ok") : JSONObjectResult.error("Back failed");
    }

    public JSONObjectResult home() {
        return performGlobalAction(GLOBAL_ACTION_HOME) ? JSONObjectResult.ok("home", "ok") : JSONObjectResult.error("Home failed");
    }

    public JSONObjectResult openApp(String packageName) {
        try {
            PackageManager pm = getPackageManager();
            Intent intent = pm.getLaunchIntentForPackage(packageName);
            if (intent == null) return JSONObjectResult.error("App not found: " + packageName);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            return JSONObjectResult.ok("opened_app", packageName);
        } catch (Exception e) { return JSONObjectResult.error(e.getClass().getSimpleName() + ": " + e.getMessage()); }
    }

    public JSONObjectResult screenText() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return JSONObjectResult.error("No active window");
        StringBuilder out = new StringBuilder(); collectText(root, out);
        return JSONObjectResult.ok("screen_text", out.toString().trim());
    }

    private AccessibilityNodeInfo findText(AccessibilityNodeInfo node, String text) {
        List<AccessibilityNodeInfo> found = node.findAccessibilityNodeInfosByText(text);
        if (found != null && !found.isEmpty()) return found.get(0);
        return null;
    }
    private AccessibilityNodeInfo findFocusedEditable(AccessibilityNodeInfo node) {
        if (node.isEditable() && node.isFocused()) return node;
        for (int i=0;i<node.getChildCount();i++) { AccessibilityNodeInfo r=findFocusedEditable(node.getChild(i)); if(r!=null)return r; }
        return null;
    }
    private AccessibilityNodeInfo findFirstEditable(AccessibilityNodeInfo node) {
        if (node.isEditable()) return node;
        for (int i=0;i<node.getChildCount();i++) { AccessibilityNodeInfo r=findFirstEditable(node.getChild(i)); if(r!=null)return r; }
        return null;
    }
    private void collectText(AccessibilityNodeInfo node, StringBuilder out) {
        CharSequence t=node.getText(); CharSequence d=node.getContentDescription();
        if(t!=null && t.length()>0) out.append(t).append('\n');
        else if(d!=null && d.length()>0) out.append(d).append('\n');
        for(int i=0;i<node.getChildCount();i++) collectText(node.getChild(i),out);
    }

    public static final class JSONObjectResult {
        public final boolean success; public final String action; public final String value; public final String error;
        private JSONObjectResult(boolean s,String a,String v,String e){success=s;action=a;value=v;error=e;}
        static JSONObjectResult ok(String a,String v){return new JSONObjectResult(true,a,v,null);}
        static JSONObjectResult error(String e){return new JSONObjectResult(false,null,null,e);}
        public String json(){
            String ev=error==null?"null":"\""+escape(error)+"\"";
            return "{\"success\":"+success+",\"action\":"+(action==null?"null":"\""+escape(action)+"\"")+",\"value\":"+(value==null?"null":"\""+escape(value)+"\"")+",\"error\":"+ev+"}";
        }
        private static String escape(String s){return s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n");}
    }
}

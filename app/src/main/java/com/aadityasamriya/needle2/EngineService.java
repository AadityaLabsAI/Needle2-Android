package com.aadityasamriya.needle2;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Native Needle engine in an isolated process. Device UI actions are delegated to the main-process accessibility service over loopback. */
public class EngineService extends Service {
    private final LocalBinder binder = new LocalBinder();
    private volatile boolean ready = false;
    private volatile String error = null;

    private static final String TOOLS = "[" +
        "{\"name\":\"get_battery\",\"description\":\"Get the phone battery percentage and charging state.\",\"parameters\":{\"type\":\"object\",\"properties\":{},\"required\":[]}}," +
        "{\"name\":\"get_time\",\"description\":\"Get the current local date and time on the phone.\",\"parameters\":{\"type\":\"object\",\"properties\":{},\"required\":[]}}," +
        "{\"name\":\"tap_text\",\"description\":\"Tap a visible UI element by its text or accessibility label. Requires the user-enabled Needle 2 accessibility service.\",\"parameters\":{\"type\":\"object\",\"properties\":{\"text\":{\"type\":\"string\"}},\"required\":[\"text\"]}}," +
        "{\"name\":\"type_text\",\"description\":\"Type text into the currently focused or first visible editable field.\",\"parameters\":{\"type\":\"object\",\"properties\":{\"text\":{\"type\":\"string\"}},\"required\":[\"text\"]}}," +
        "{\"name\":\"tap_xy\",\"description\":\"Tap the phone screen at pixel coordinates x,y.\",\"parameters\":{\"type\":\"object\",\"properties\":{\"x\":{\"type\":\"number\"},\"y\":{\"type\":\"number\"}},\"required\":[\"x\",\"y\"]}}," +
        "{\"name\":\"swipe\",\"description\":\"Swipe from one screen coordinate to another.\",\"parameters\":{\"type\":\"object\",\"properties\":{\"x1\":{\"type\":\"number\"},\"y1\":{\"type\":\"number\"},\"x2\":{\"type\":\"number\"},\"y2\":{\"type\":\"number\"},\"duration_ms\":{\"type\":\"integer\"}},\"required\":[\"x1\",\"y1\",\"x2\",\"y2\"]}}," +
        "{\"name\":\"press_back\",\"description\":\"Press Android Back.\",\"parameters\":{\"type\":\"object\",\"properties\":{},\"required\":[]}}," +
        "{\"name\":\"press_home\",\"description\":\"Press Android Home.\",\"parameters\":{\"type\":\"object\",\"properties\":{},\"required\":[]}}," +
        "{\"name\":\"open_app\",\"description\":\"Launch an installed Android app by its package name.\",\"parameters\":{\"type\":\"object\",\"properties\":{\"package\":{\"type\":\"string\"}},\"required\":[\"package\"]}}," +
        "{\"name\":\"open_url\",\"description\":\"Open a URL in the Android browser.\",\"parameters\":{\"type\":\"object\",\"properties\":{\"url\":{\"type\":\"string\"}},\"required\":[\"url\"]}}," +
        "{\"name\":\"get_screen_text\",\"description\":\"Read visible text and accessibility labels from the current app screen.\",\"parameters\":{\"type\":\"object\",\"properties\":{},\"required\":[]}}" +
        "]";

    private static final String SYSTEM =
        "device: Android phone; locale: en-IN; assistant: Needle 2 Local AI device agent. " +
        "You may operate the phone only through the declared tools. Use get_screen_text before guessing UI coordinates. " +
        "Prefer tap_text over tap_xy. Never invent a package name when it is not evidenced by the request or screen. " +
        "If accessibility automation is unavailable, report that the user must enable the Needle 2 accessibility service. " +
        "For risky actions such as sending messages, purchases, deletion, account changes, or irreversible actions, ask for confirmation instead of executing them.";

    public class LocalBinder extends Binder {
        public boolean initialize() {
            if (ready) return true;
            try (InputStream in = getAssets().open("needle2.cact")) {
                byte[] model = readAll(in);
                int rc = NativeNeedle.load(model);
                if (rc != 0) throw new IllegalStateException("needle_load failed: " + rc);
                int init = NativeNeedle.init(SYSTEM, TOOLS);
                if (init < 0) throw new IllegalStateException("needle_init failed: " + init);
                ready = true; error = null; return true;
            } catch (Throwable t) {
                error = t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage());
                return false;
            }
        }

        public String statusJson() {
            try {
                JSONObject o = new JSONObject();
                o.put("ready", ready); o.put("error", error == null ? JSONObject.NULL : error);
                o.put("engine_process", android.os.Process.myPid());
                return o.toString();
            } catch (Exception e) { return "{\"ready\":false}"; }
        }

        public String complete(String query) throws Exception {
            if (!initialize()) throw new IllegalStateException(error == null ? "Engine unavailable" : error);
            String response = NativeNeedle.complete(query, 256);
            for (int round = 0; round < 8; round++) {
                JSONObject obj = new JSONObject(response);
                JSONArray calls = obj.optJSONArray("function_calls");
                if (calls == null || calls.length() == 0) return textFrom(obj, response);
                for (int i = 0; i < calls.length(); i++) {
                    JSONObject call = calls.getJSONObject(i);
                    JSONObject args = call.optJSONObject("arguments");
                    JSONObject result = executeTool(call.optString("name"), args == null ? new JSONObject() : args);
                    response = NativeNeedle.complete(result.toString(), 256);
                }
            }
            return response;
        }

        private String textFrom(JSONObject obj, String fallback) {
            String reasoning = obj.optString("reasoning", "");
            return reasoning.isEmpty() ? fallback : reasoning;
        }
    }

    @Override public IBinder onBind(Intent intent) { return binder; }
    @Override public int onStartCommand(Intent intent, int flags, int startId) { return START_STICKY; }

    private JSONObject executeTool(String name, JSONObject args) throws Exception {
        JSONObject result = new JSONObject();
        if ("get_battery".equals(name)) {
            android.os.BatteryManager bm = (android.os.BatteryManager)getSystemService(BATTERY_SERVICE);
            result.put("battery_percent", bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY));
            result.put("charging", bm.isCharging());
        } else if ("get_time".equals(name)) {
            result.put("local_time", java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.MEDIUM, java.util.Locale.getDefault()).format(new java.util.Date()));
        } else {
            result = callMainAutomation(name, args);
        }
        return result;
    }

    private JSONObject callMainAutomation(String action, JSONObject args) throws Exception {
        JSONObject request = new JSONObject(); request.put("action", action); request.put("args", args);
        URL url = new URL("http://127.0.0.1:8765/api/automation");
        HttpURLConnection c = (HttpURLConnection)url.openConnection();
        c.setConnectTimeout(1500); c.setReadTimeout(8000); c.setRequestMethod("POST"); c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        byte[] bytes = request.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream out = c.getOutputStream()) { out.write(bytes); }
        InputStream in = c.getResponseCode() >= 400 ? c.getErrorStream() : c.getInputStream();
        if (in == null) return new JSONObject().put("success", false).put("error", "Automation bridge unavailable");
        return new JSONObject(new String(readAll(in), StandardCharsets.UTF_8));
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int n;
        while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n); return out.toByteArray();
    }
}

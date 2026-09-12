package com.aadityasamriya.needle2;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/** Runs the native Needle engine in a separate Android process.
 * A native crash therefore cannot take down the playground UI/server process.
 */
public class EngineService extends Service {
    private final LocalBinder binder = new LocalBinder();
    private volatile boolean ready = false;
    private volatile String error = null;

    private static final String TOOLS = "[" +
            "{\"name\":\"get_battery\",\"description\":\"Get the phone battery percentage and charging state.\",\"parameters\":{\"type\":\"object\",\"properties\":{},\"required\":[]}}," +
            "{\"name\":\"get_time\",\"description\":\"Get the current local date and time on the phone.\",\"parameters\":{\"type\":\"object\",\"properties\":{},\"required\":[]}}" +
            "]";
    private static final String SYSTEM =
            "device: Android phone; locale: en-IN; assistant: Needle 2 Local. " +
            "Use only the declared tools. If a request cannot be served by a declared tool, return an empty call.";

    public class LocalBinder extends Binder {
        public boolean initialize() {
            if (ready) return true;
            try (InputStream in = getAssets().open("needle2.cact")) {
                byte[] model = readAll(in);
                int rc = NativeNeedle.load(model);
                if (rc != 0) throw new IllegalStateException("needle_load failed: " + rc);
                int init = NativeNeedle.init(SYSTEM, TOOLS);
                if (init < 0) throw new IllegalStateException("needle_init failed: " + init);
                ready = true;
                error = null;
                return true;
            } catch (Throwable t) {
                error = t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage());
                return false;
            }
        }

        public String statusJson() {
            try {
                JSONObject o = new JSONObject();
                o.put("ready", ready);
                o.put("error", error == null ? JSONObject.NULL : error);
                o.put("process", android.os.Process.myPid());
                return o.toString();
            } catch (Exception e) { return "{\"ready\":false}"; }
        }

        public String complete(String query) throws Exception {
            if (!initialize()) throw new IllegalStateException(error == null ? "Engine unavailable" : error);
            String response = NativeNeedle.complete(query, 256);
            for (int round = 0; round < 4; round++) {
                JSONObject obj = new JSONObject(response);
                JSONArray calls = obj.optJSONArray("function_calls");
                if (calls == null || calls.length() == 0) return textFrom(obj, response);
                for (int i = 0; i < calls.length(); i++) {
                    JSONObject call = calls.getJSONObject(i);
                    JSONObject result = executeTool(call.optString("name"));
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

    private JSONObject executeTool(String name) throws Exception {
        JSONObject result = new JSONObject();
        if ("get_battery".equals(name)) {
            android.os.BatteryManager bm = (android.os.BatteryManager) getSystemService(BATTERY_SERVICE);
            result.put("battery_percent", bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY));
            result.put("charging", bm.isCharging());
        } else if ("get_time".equals(name)) {
            result.put("local_time", java.text.DateFormat.getDateTimeInstance(
                    java.text.DateFormat.MEDIUM, java.text.DateFormat.MEDIUM,
                    java.util.Locale.getDefault()).format(new java.util.Date()));
        } else {
            result.put("error", "Unknown tool: " + name);
        }
        return result;
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
        return out.toByteArray();
    }
}

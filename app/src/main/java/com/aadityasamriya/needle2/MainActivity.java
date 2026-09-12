package com.aadityasamriya.needle2;

import android.app.Activity;
import android.os.Bundle;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.graphics.Typeface;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private TextView status;
    private TextView output;
    private EditText input;
    private Button send;
    private boolean ready = false;

    private static final String TOOLS = "[" +
            "{\"name\":\"get_battery\",\"description\":\"Get the phone battery percentage and charging state.\",\"parameters\":{\"type\":\"object\",\"properties\":{},\"required\":[]}}," +
            "{\"name\":\"get_time\",\"description\":\"Get the current local date and time on the phone.\",\"parameters\":{\"type\":\"object\",\"properties\":{},\"required\":[]}}" +
            "]";

    private static final String SYSTEM =
            "device: Android phone; locale: en-IN; assistant: Needle 2 Local. " +
            "Use only the declared tools. If a request cannot be served by a declared tool, return an empty call.";

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        executor.execute(this::loadModel);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 16);

        TextView title = new TextView(this);
        title.setText("Needle 2 Local");
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        status = new TextView(this);
        status.setText("Loading local engine…");
        status.setPadding(0, 8, 0, 16);
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(this);
        output = new TextView(this);
        output.setTextSize(16);
        output.setText("Needle 2 runs completely on this phone.\n");
        output.setPadding(0, 8, 0, 16);
        scroll.addView(output);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        input = new EditText(this);
        input.setHint("Try: What is my battery level?");
        input.setSingleLine(true);
        row.addView(input, new LinearLayout.LayoutParams(0, -2, 1));
        send = new Button(this);
        send.setText("Send");
        send.setEnabled(false);
        send.setOnClickListener(v -> sendQuery());
        row.addView(send, new LinearLayout.LayoutParams(-2, -2));
        root.addView(row, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);
    }

    private void loadModel() {
        try (InputStream in = getAssets().open("needle2.cact")) {
            byte[] model = readAll(in);
            int rc = NativeNeedle.load(model);
            if (rc != 0) throw new IllegalStateException("needle_load failed: " + rc);
            int init = NativeNeedle.init(SYSTEM, TOOLS);
            if (init < 0) throw new IllegalStateException("needle_init failed: " + init);
            ready = true;
            main.post(() -> { status.setText("Ready • fully local • Android ARM64"); send.setEnabled(true); });
        } catch (Exception e) {
            main.post(() -> status.setText("Engine error: " + e.getMessage()));
        }
    }

    private void sendQuery() {
        if (!ready) return;
        final String query = input.getText().toString().trim();
        if (query.isEmpty()) return;
        input.setText("");
        send.setEnabled(false);
        output.append("\nYou: " + query + "\n");
        executor.execute(() -> {
            try {
                String finalText = runAgentTurn(query);
                main.post(() -> { output.append("Needle: " + finalText + "\n"); send.setEnabled(true); });
            } catch (Exception e) {
                main.post(() -> { output.append("Error: " + e.getMessage() + "\n"); send.setEnabled(true); });
            }
        });
    }

    private String runAgentTurn(String query) throws Exception {
        String response = NativeNeedle.complete(query, 256);
        for (int round = 0; round < 4; round++) {
            JSONObject obj = new JSONObject(response);
            JSONArray calls = obj.optJSONArray("function_calls");
            if (calls == null || calls.length() == 0) {
                String reasoning = obj.optString("reasoning", "");
                return reasoning.isEmpty() ? response : reasoning;
            }
            // Needle's documented multi-turn contract feeds each tool result back as the next complete().
            for (int i = 0; i < calls.length(); i++) {
                JSONObject call = calls.getJSONObject(i);
                String name = call.optString("name");
                JSONObject result = executeTool(name);
                response = NativeNeedle.complete(result.toString(), 256);
                JSONObject next = new JSONObject(response);
                JSONArray nextCalls = next.optJSONArray("function_calls");
                if (nextCalls == null || nextCalls.length() == 0) {
                    String reasoning = next.optString("reasoning", "");
                    return reasoning.isEmpty() ? response : reasoning;
                }
            }
        }
        return response;
    }

    private JSONObject executeTool(String name) throws Exception {
        JSONObject result = new JSONObject();
        if ("get_battery".equals(name)) {
            BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
            result.put("battery_percent", bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY));
            result.put("charging", bm.isCharging());
        } else if ("get_time".equals(name)) {
            result.put("local_time", DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM, Locale.getDefault()).format(new Date()));
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

    @Override protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}

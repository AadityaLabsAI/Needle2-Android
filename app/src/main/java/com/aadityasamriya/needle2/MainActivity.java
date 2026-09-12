package com.aadityasamriya.needle2;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.view.Gravity;
import android.graphics.Typeface;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {
    private EngineService.LocalBinder engine;
    private LocalPlaygroundServer server;
    private TextView status;
    private boolean bound;
    private static final int PORT = 8765;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder service) {
            engine = (EngineService.LocalBinder) service;
            bound = true;
            LocalPlaygroundServer.AssetStore.init(MainActivity.this);
            try {
                if (server == null) server = new LocalPlaygroundServer(engine, PORT);
                server.start();
                status.setText("Local playground running • no Internet required");
            } catch (Exception e) {
                status.setText("Could not start local server: " + e.getMessage());
            }
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            bound = false; engine = null;
            status.setText("Engine process stopped. The app UI is still safe.");
        }
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        // Do not load native Needle during UI startup. The engine is isolated in its own process.
        Intent service = new Intent(this, EngineService.class);
        startService(service);
        bindService(service, connection, BIND_AUTO_CREATE);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 18);

        TextView title = new TextView(this);
        title.setText("Needle 2 Local"); title.setTextSize(25);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        status = new TextView(this);
        status.setText("Starting local playground…"); status.setTextSize(16);
        status.setPadding(0, 10, 0, 14);
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(this);
        TextView instructions = new TextView(this);
        instructions.setTextSize(16);
        instructions.setText(
                "यह app Internet के बिना local Needle 2 playground चलाता है।\n\n" +
                "कैसे इस्तेमाल करें:\n" +
                "1. नीचे ‘Open Playground in Chrome’ दबाएँ।\n" +
                "2. Chrome में local playground खुलेगा।\n" +
                "3. Internet बंद होने पर भी page और bundled files उपलब्ध रहेंगे।\n" +
                "4. पहली query पर local Needle 2 engine initialize होगा।\n" +
                "5. फिर message लिखकर Run locally दबाएँ।\n\n" +
                "Dependencies / model app के अंदर bundled हैं। Chrome में कुछ अलग से install करने की जरूरत नहीं है; Chrome केवल local web UI render करता है।\n\n" +
                "अगर Chrome बंद कर दिया जाए, तो app को background में चालू रखें ताकि local server चलता रहे।");
        instructions.setPadding(0, 8, 0, 20);
        scroll.addView(instructions);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        Button open = new Button(this);
        open.setText("Open Playground in Chrome");
        open.setOnClickListener(v -> openChrome());
        root.addView(open, new LinearLayout.LayoutParams(-1, -2));

        Button statusButton = new Button(this);
        statusButton.setText("Check Engine Status");
        statusButton.setOnClickListener(v -> {
            if (engine == null) status.setText("Engine service not connected");
            else status.setText("Local server: http://127.0.0.1:" + PORT + "/\n" + engine.statusJson());
        });
        root.addView(statusButton, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout.LayoutParams p = (LinearLayout.LayoutParams) open.getLayoutParams();
        p.gravity = Gravity.CENTER_HORIZONTAL;
        open.setLayoutParams(p);
        setContentView(root);
    }

    private void openChrome() {
        Uri uri = Uri.parse("http://127.0.0.1:" + PORT + "/");
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.setPackage("com.android.chrome");
        try { startActivity(intent); }
        catch (Exception chromeMissing) { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
    }

    @Override protected void onDestroy() {
        if (server != null) server.stop();
        if (bound) unbindService(connection);
        super.onDestroy();
    }
}

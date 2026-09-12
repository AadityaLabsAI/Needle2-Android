package com.aadityasamriya.needle2;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.graphics.Typeface;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final int PORT = 8765;
    private TextView status;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
        startService(new Intent(this, LocalServerService.class));
        status.setText("Local browser bridge starting…");
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24,24,24,18);
        TextView title = new TextView(this);
        title.setText("Needle 2 • Local Browser Agent");
        title.setTextSize(24); title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title);
        status = new TextView(this);
        status.setTextSize(16); status.setPadding(0,10,0,12);
        root.addView(status);

        ScrollView scroll = new ScrollView(this);
        TextView help = new TextView(this);
        help.setTextSize(16);
        help.setText("यह APK जानबूझकर lightweight है। Android के अंदर Needle का native engine नहीं चलता।\n\n"+
            "पूरा AI inference Chrome के local browser में WebAssembly से चलेगा। APK केवल localhost bridge और device automation देता है।\n\n"+
            "पहली बार:\n1. ‘Enable Device Automation’ दबाएँ।\n2. Accessibility में ‘Needle 2 Device Automation’ ON करें।\n3. वापस आकर ‘Open Local Agent’ दबाएँ।\n4. Chrome में पूरा agent खुलेगा।\n\n"+
            "Needle 2 model, WebAssembly engine और UI app के साथ bundled हैं। Runtime पर cloud AI/API की जरूरत नहीं है।\n\n"+
            "अगर Chrome बंद हो जाए तो app को दोबारा खोलकर Local Agent दबाएँ।");
        scroll.addView(help); root.addView(scroll, new LinearLayout.LayoutParams(-1,0,1));

        Button access = new Button(this); access.setText("Enable Device Automation");
        access.setOnClickListener(v -> openAccessibilitySettings()); root.addView(access);
        Button open = new Button(this); open.setText("Open Local Agent in Chrome");
        open.setOnClickListener(v -> openChrome()); root.addView(open);
        Button check = new Button(this); check.setText("Check Local Bridge");
        check.setOnClickListener(v -> { status.setText("Local agent: http://127.0.0.1:"+PORT+"/\nAccessibility: " +
            (AutomationAccessibilityService.getInstance()!=null ? "ON" : "OFF")); }); root.addView(check);
        setContentView(root);
    }

    private void openAccessibilitySettings() {
        try { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); }
        catch(Exception e) { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
    }
    private void openChrome() {
        Uri uri = Uri.parse("http://127.0.0.1:"+PORT+"/");
        Intent i = new Intent(Intent.ACTION_VIEW, uri); i.setPackage("com.android.chrome");
        try { startActivity(i); } catch(Exception e) { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
    }
}

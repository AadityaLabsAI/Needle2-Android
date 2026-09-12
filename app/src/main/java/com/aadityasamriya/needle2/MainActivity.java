package com.aadityasamriya.needle2;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.graphics.Typeface;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {
    private EngineService.LocalBinder engine; private LocalPlaygroundServer server; private TextView status; private boolean bound; private static final int PORT=8765;
    private final ServiceConnection connection=new ServiceConnection(){
        @Override public void onServiceConnected(ComponentName name,IBinder service){engine=(EngineService.LocalBinder)service;bound=true;LocalPlaygroundServer.AssetStore.init(MainActivity.this);try{if(server==null)server=new LocalPlaygroundServer(engine,PORT);server.start();status.setText("Local AI agent ready • localhost running");}catch(Exception e){status.setText("Local server: "+e.getMessage());}}
        @Override public void onServiceDisconnected(ComponentName name){bound=false;engine=null;status.setText("AI engine process stopped; UI remains safe.");}
    };
    @Override public void onCreate(Bundle savedInstanceState){super.onCreate(savedInstanceState);buildUi();Intent service=new Intent(this,EngineService.class);startService(service);bindService(service,connection,BIND_AUTO_CREATE);}
    private void buildUi(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(24,24,24,18);
        TextView title=new TextView(this);title.setText("Needle 2 Local AI");title.setTextSize(25);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);root.addView(title,new LinearLayout.LayoutParams(-1,-2));
        status=new TextView(this);status.setText("Starting local AI agent…");status.setTextSize(16);status.setPadding(0,10,0,12);root.addView(status,new LinearLayout.LayoutParams(-1,-2));
        ScrollView scroll=new ScrollView(this);TextView instructions=new TextView(this);instructions.setTextSize(16);instructions.setText(
            "यह app आपके फोन पर local AI device agent चलाता है। Needle 2 model और automation bridge phone के अंदर रहते हैं।\n\n"+
            "पहली बार setup:\n"+
            "1. नीचे ‘Enable Device Automation’ दबाएँ।\n"+
            "2. Android Accessibility settings में ‘Needle 2 Device Automation’ ON करें।\n"+
            "3. वापस इस app में आएँ।\n"+
            "4. ‘Open Playground in Chrome’ दबाएँ।\n\n"+
            "आप commands दे सकते हैं जैसे:\n"+
            "• ‘Open YouTube’\n• ‘Go back’\n• ‘Scroll down’\n• ‘Read the current screen’\n• ‘Open Chrome and search for …’\n• ‘Tap Settings’\n\n"+
            "AI पहले screen text पढ़ सकता है और फिर visible controls पर action कर सकता है। Internet inference के लिए जरूरी नहीं है।\n\n"+
            "Safety: messaging, purchases, deletion और अन्य irreversible actions के लिए agent confirmation मांगेगा।");scroll.addView(instructions);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        Button access=new Button(this);access.setText("Enable Device Automation");access.setOnClickListener(v->openAccessibilitySettings());root.addView(access,new LinearLayout.LayoutParams(-1,-2));
        Button open=new Button(this);open.setText("Open Playground in Chrome");open.setOnClickListener(v->openChrome());root.addView(open,new LinearLayout.LayoutParams(-1,-2));
        Button statusButton=new Button(this);statusButton.setText("Check Agent Status");statusButton.setOnClickListener(v->{if(engine==null)status.setText("Engine service not connected");else status.setText("http://127.0.0.1:"+PORT+"/\n"+engine.statusJson()+"\nAccessibility: "+(AutomationAccessibilityService.getInstance()!=null?"ON":"OFF"));});root.addView(statusButton,new LinearLayout.LayoutParams(-1,-2));
        setContentView(root);
    }
    private void openAccessibilitySettings(){try{startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));}catch(Exception e){startActivity(new Intent(Settings.ACTION_SETTINGS));}}
    private void openChrome(){Uri uri=Uri.parse("http://127.0.0.1:"+PORT+"/");Intent i=new Intent(Intent.ACTION_VIEW,uri);i.setPackage("com.android.chrome");try{startActivity(i);}catch(Exception e){startActivity(new Intent(Intent.ACTION_VIEW,uri));}}
    @Override protected void onDestroy(){if(bound)unbindService(connection);/* Keep the localhost server alive while the started service remains alive. */super.onDestroy();}
}

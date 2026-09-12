package com.aadityasamriya.needle2;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

/** Tiny loopback-only HTTP bridge. AI inference stays entirely in the browser. */
public final class LocalPlaygroundServer {
    private final Context context;
    private ServerSocket server; private Thread thread; private volatile boolean running; private final int port;
    public LocalPlaygroundServer(Context c, int p) { context=c.getApplicationContext(); port=p; }
    public synchronized void start() throws IOException {
        if (running) return;
        server=new ServerSocket(); server.setReuseAddress(true);
        server.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(),port));
        running=true; thread=new Thread(this::loop,"needle-local-http"); thread.start();
    }
    public synchronized void stop() { running=false; try { if(server!=null) server.close(); } catch(Exception ignored) {} }
    private void loop() { while(running) try { Socket s=server.accept(); new Thread(()->handle(s),"needle-http-client").start(); } catch(IOException e) { if(!running) break; } }
    private void handle(Socket socket) {
        try(Socket s=socket) {
            s.setSoTimeout(10000);
            BufferedReader r=new BufferedReader(new InputStreamReader(s.getInputStream(),StandardCharsets.UTF_8));
            String request=r.readLine(); if(request==null)return;
            String[] first=request.split(" "); String method=first.length>0?first[0]:"GET"; String path=first.length>1?first[1]:"/";
            int len=0; String line; while((line=r.readLine())!=null&&!line.isEmpty()){int c=line.indexOf(':'); if(c>0&&"content-length".equalsIgnoreCase(line.substring(0,c).trim())) try{len=Integer.parseInt(line.substring(c+1).trim());}catch(Exception ignored){}}
            char[] chars=new char[Math.min(Math.max(len,0),1024*1024)]; int n=0; while(n<chars.length){int x=r.read(chars,n,chars.length-n);if(x<0)break;n+=x;} String body=new String(chars,0,n);
            if("GET".equals(method)) {
                String clean=path.split("\\?",2)[0];
                if(clean.equals("/")||clean.equals("/index.html")) send(s,200,"text/html; charset=utf-8",AssetStore.read("playground/index.html"));
                else if(clean.equals("/api/status")) sendJson(s,200,status());
                else serveAsset(s,clean);
            } else if("POST".equals(method)&&path.startsWith("/api/automation")) handleAutomation(s,new JSONObject(body));
            else sendText(s,404,"Not found");
        } catch(Throwable ignored) {}
    }
    private void serveAsset(Socket s,String path)throws IOException {
        if(!path.startsWith("/runtime/")||path.contains("..")){sendText(s,404,"Not found");return;}
        String name=path.substring(1);
        String type=name.endsWith(".js")?"application/javascript":name.endsWith(".wasm")?"application/wasm":name.endsWith(".cact")?"application/octet-stream":"application/octet-stream";
        send(s,200,type,AssetStore.read(name));
    }
    private String status() throws Exception { return new JSONObject().put("ready",true).put("browser_inference",true).put("engine","WebAssembly in Chrome").put("model","Needle 2").put("accessibility_enabled",AutomationAccessibilityService.getInstance()!=null).toString(); }
    private void handleAutomation(Socket s,JSONObject req)throws Exception {
        AutomationAccessibilityService a=AutomationAccessibilityService.getInstance();
        if(a==null){sendJson(s,503,new JSONObject().put("success",false).put("error","Enable Needle 2 Device Automation in Android Accessibility settings.").toString());return;}
        String action=req.optString("action"); JSONObject args=req.optJSONObject("args"); if(args==null)args=new JSONObject(); String result;
        switch(action){
            case "tap_text": result=a.tapText(args.optString("text")).json(); break;
            case "type_text": result=a.typeText(args.optString("text")).json(); break;
            case "tap_xy": result=a.tapXY((float)args.optDouble("x"),(float)args.optDouble("y")).json(); break;
            case "swipe": result=a.swipe((float)args.optDouble("x1"),(float)args.optDouble("y1"),(float)args.optDouble("x2"),(float)args.optDouble("y2"),args.optLong("duration_ms",400)).json(); break;
            case "press_back": result=a.back().json(); break;
            case "press_home": result=a.home().json(); break;
            case "open_app": result=a.openApp(args.optString("package")).json(); break;
            case "get_screen_text": result=a.screenText().json(); break;
            case "open_url": { Intent i=new Intent(Intent.ACTION_VIEW,Uri.parse(args.optString("url"))); i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); context.startActivity(i); result=new JSONObject().put("success",true).toString(); break; }
            default: result=new JSONObject().put("success",false).put("error","Unknown automation action").toString();
        }
        sendJson(s,200,result);
    }
    public static final class AssetStore {
        private static Context context;
        public static void init(Context c){context=c.getApplicationContext();}
        static byte[] read(String name)throws IOException{if(context==null)throw new IOException("Asset store not initialized");try(InputStream in=context.getAssets().open(name);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[]b=new byte[16384];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toByteArray();}}
    }
    private static void sendJson(Socket s,int code,String json)throws IOException{send(s,code,"application/json; charset=utf-8",json.getBytes(StandardCharsets.UTF_8));}
    private static void sendText(Socket s,int code,String text)throws IOException{send(s,code,"text/plain; charset=utf-8",text.getBytes(StandardCharsets.UTF_8));}
    private static void send(Socket s,int code,String type,byte[]data)throws IOException{OutputStream out=s.getOutputStream();String status=code==200?"OK":code==404?"Not Found":"Service Unavailable";String h="HTTP/1.1 "+code+" "+status+"\r\nContent-Type: "+type+"\r\nContent-Length: "+data.length+"\r\nCache-Control: no-store\r\nAccess-Control-Allow-Origin: *\r\nConnection: close\r\n\r\n";out.write(h.getBytes(StandardCharsets.UTF_8));out.write(data);out.flush();}
}

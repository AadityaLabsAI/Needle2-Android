package com.aadityasamriya.needle2;

import android.content.Intent;
import android.net.Uri;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

/** Dependency-free HTTP server bound only to 127.0.0.1. */
public final class LocalPlaygroundServer {
    private final EngineService.LocalBinder engine;
    private ServerSocket server; private Thread thread; private volatile boolean running; private final int port;
    public LocalPlaygroundServer(EngineService.LocalBinder engine,int port){this.engine=engine;this.port=port;}
    public synchronized void start() throws IOException { if(running)return; server=new ServerSocket();server.setReuseAddress(true);server.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(),port));running=true;thread=new Thread(this::loop,"needle-local-http");thread.start(); }
    public synchronized void stop(){running=false;try{if(server!=null)server.close();}catch(Exception ignored){}}
    private void loop(){while(running){try{final Socket socket=server.accept();new Thread(()->handle(socket),"needle-http-client").start();}catch(IOException ignored){if(!running)break;}}}
    private void handle(Socket socket){try(Socket s=socket){s.setSoTimeout(10000);BufferedReader r=new BufferedReader(new InputStreamReader(s.getInputStream(),StandardCharsets.UTF_8));String request=r.readLine();if(request==null)return;String[] first=request.split(" ");String method=first.length>0?first[0]:"GET";String path=first.length>1?first[1]:"/";int contentLength=0;String line;while((line=r.readLine())!=null&&!line.isEmpty()){int colon=line.indexOf(':');if(colon>0&&"content-length".equalsIgnoreCase(line.substring(0,colon).trim()))try{contentLength=Integer.parseInt(line.substring(colon+1).trim());}catch(Exception ignored){}}
        char[] bodyChars=new char[Math.min(contentLength,1024*1024)];int read=0;while(read<bodyChars.length){int n=r.read(bodyChars,read,bodyChars.length-read);if(n<0)break;read+=n;}String body=new String(bodyChars,0,read);
        if("GET".equals(method)&&(path.equals("/")||path.equals("/index.html"))) send(s,200,"text/html; charset=utf-8",readAsset("playground/index.html"));
        else if("GET".equals(method)&&path.equals("/api/status")) sendJson(s,200,status());
        else if("POST".equals(method)&&path.equals("/api/chat")){JSONObject in=new JSONObject(body);String message=in.optString("message","").trim();if(message.isEmpty()){sendJson(s,400,"{\"error\":\"Message is empty\"}");return;}try{sendJson(s,200,new JSONObject().put("answer",engine.complete(message)).toString());}catch(Throwable t){sendJson(s,503,new JSONObject().put("error",t.getClass().getSimpleName()+": "+String.valueOf(t.getMessage())).toString());}}
        else if("POST".equals(method)&&path.equals("/api/automation")){handleAutomation(s,new JSONObject(body));}
        else send(s,404,"text/plain; charset=utf-8","Not found".getBytes(StandardCharsets.UTF_8));
    }catch(Throwable ignored){}}

    private String status(){try{JSONObject o=new JSONObject(engine.statusJson());AutomationAccessibilityService a=AutomationAccessibilityService.getInstance();o.put("accessibility_enabled",a!=null);return o.toString();}catch(Exception e){return "{\"ready\":false}";}}
    private void handleAutomation(Socket s,JSONObject req) throws Exception {AutomationAccessibilityService a=AutomationAccessibilityService.getInstance();if(a==null){sendJson(s,503,new JSONObject().put("success",false).put("error","Accessibility service is not enabled. Enable Needle 2 Device Automation in Android Accessibility settings.").toString());return;}String action=req.optString("action");JSONObject args=req.optJSONObject("args");if(args==null)args=new JSONObject();String result;
        switch(action){
            case "tap_text": result=a.tapText(args.optString("text")).json();break;
            case "type_text": result=a.typeText(args.optString("text")).json();break;
            case "tap_xy": result=a.tapXY((float)args.optDouble("x"),(float)args.optDouble("y")).json();break;
            case "swipe": result=a.swipe((float)args.optDouble("x1"),(float)args.optDouble("y1"),(float)args.optDouble("x2"),(float)args.optDouble("y2"),args.optLong("duration_ms",400)).json();break;
            case "press_back": result=a.back().json();break;
            case "press_home": result=a.home().json();break;
            case "open_app": result=a.openApp(args.optString("package")).json();break;
            case "get_screen_text": result=a.screenText().json();break;
            case "open_url": {String url=args.optString("url");Intent i=new Intent(Intent.ACTION_VIEW,Uri.parse(url));i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);getContext().startActivity(i);result=new JSONObject().put("success",true).put("action","open_url").put("value",url).toString();break;}
            default: result=new JSONObject().put("success",false).put("error","Unknown automation action: "+action).toString();
        } sendJson(s,200,result); }
    private static android.content.Context appContext; public static android.content.Context getContext(){return appContext;} public static final class AssetStore{private static android.content.Context context;public static void init(android.content.Context c){context=c.getApplicationContext();appContext=context;}static byte[] read(String name)throws IOException{if(context==null)throw new IOException("Asset store not initialized");try(InputStream in=context.getAssets().open(name);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[]b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toByteArray();}}}
    private byte[] readAsset(String name)throws IOException{return AssetStore.read(name);}
    private static void sendJson(Socket s,int code,String json)throws IOException{send(s,code,"application/json; charset=utf-8",json.getBytes(StandardCharsets.UTF_8));}
    private static void send(Socket s,int code,String type,byte[]data)throws IOException{OutputStream out=s.getOutputStream();String status=code==200?"OK":code==400?"Bad Request":code==404?"Not Found":"Service Unavailable";String headers="HTTP/1.1 "+code+" "+status+"\r\nContent-Type: "+type+"\r\nContent-Length: "+data.length+"\r\nCache-Control: no-store\r\nAccess-Control-Allow-Origin: *\r\nConnection: close\r\n\r\n";out.write(headers.getBytes(StandardCharsets.UTF_8));out.write(data);out.flush();}
}

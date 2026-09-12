package com.aadityasamriya.needle2;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

/** Tiny dependency-free HTTP server bound only to 127.0.0.1. */
public final class LocalPlaygroundServer {
    private final EngineService.LocalBinder engine;
    private ServerSocket server;
    private Thread thread;
    private volatile boolean running;
    private final int port;

    public LocalPlaygroundServer(EngineService.LocalBinder engine, int port) {
        this.engine = engine;
        this.port = port;
    }

    public synchronized void start() throws IOException {
        if (running) return;
        server = new ServerSocket();
        server.setReuseAddress(true);
        server.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), port));
        running = true;
        thread = new Thread(() -> loop(), "needle-local-http");
        thread.start();
    }

    public synchronized void stop() {
        running = false;
        try { if (server != null) server.close(); } catch (Exception ignored) {}
    }

    private void loop() {
        while (running) {
            try { final Socket socket = server.accept(); new Thread(() -> handle(socket), "needle-http-client").start(); }
            catch (IOException ignored) { if (!running) break; }
        }
    }

    private void handle(Socket socket) {
        try (Socket s = socket) {
            s.setSoTimeout(10000);
            BufferedReader r = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            String request = r.readLine();
            if (request == null) return;
            String[] first = request.split(" ");
            String method = first.length > 0 ? first[0] : "GET";
            String path = first.length > 1 ? first[1] : "/";
            int contentLength = 0;
            String line;
            while ((line = r.readLine()) != null && !line.isEmpty()) {
                int colon = line.indexOf(':');
                if (colon > 0 && "content-length".equalsIgnoreCase(line.substring(0, colon).trim())) {
                    try { contentLength = Integer.parseInt(line.substring(colon + 1).trim()); } catch (Exception ignored) {}
                }
            }
            char[] bodyChars = new char[Math.min(contentLength, 1024 * 1024)];
            int read = 0;
            while (read < bodyChars.length) { int n = r.read(bodyChars, read, bodyChars.length - read); if (n < 0) break; read += n; }
            String body = new String(bodyChars, 0, read);

            if ("GET".equals(method) && ("/".equals(path) || "/index.html".equals(path))) {
                byte[] html = readAsset("playground/index.html");
                send(s, 200, "text/html; charset=utf-8", html);
            } else if ("GET".equals(method) && "/api/status".equals(path)) {
                send(s, 200, "application/json; charset=utf-8", engine.statusJson().getBytes(StandardCharsets.UTF_8));
            } else if ("POST".equals(method) && "/api/chat".equals(path)) {
                JSONObject input = new JSONObject(body);
                String message = input.optString("message", "").trim();
                if (message.isEmpty()) { sendJson(s, 400, "{\"error\":\"Message is empty\"}"); return; }
                try {
                    String answer = engine.complete(message);
                    JSONObject out = new JSONObject(); out.put("answer", answer);
                    sendJson(s, 200, out.toString());
                } catch (Throwable t) {
                    JSONObject out = new JSONObject(); out.put("error", t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage()));
                    sendJson(s, 503, out.toString());
                }
            } else {
                send(s, 404, "text/plain; charset=utf-8", "Not found".getBytes(StandardCharsets.UTF_8));
            }
        } catch (Throwable ignored) {}
    }

    private byte[] readAsset(String name) throws IOException {
        // The server is created by MainActivity, so use its asset helper through a holder.
        return AssetStore.read(name);
    }

    private static void sendJson(Socket s, int code, String json) throws IOException {
        send(s, code, "application/json; charset=utf-8", json.getBytes(StandardCharsets.UTF_8));
    }

    private static void send(Socket s, int code, String type, byte[] data) throws IOException {
        OutputStream out = s.getOutputStream();
        String status = code == 200 ? "OK" : (code == 400 ? "Bad Request" : code == 404 ? "Not Found" : "Service Unavailable");
        String headers = "HTTP/1.1 " + code + " " + status + "\r\n" +
                "Content-Type: " + type + "\r\n" +
                "Content-Length: " + data.length + "\r\n" +
                "Cache-Control: no-store\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Connection: close\r\n\r\n";
        out.write(headers.getBytes(StandardCharsets.UTF_8)); out.write(data); out.flush();
    }

    public static final class AssetStore {
        private static android.content.Context context;
        public static void init(android.content.Context c) { context = c.getApplicationContext(); }
        static byte[] read(String name) throws IOException {
            if (context == null) throw new IOException("Asset store not initialized");
            try (InputStream in = context.getAssets().open(name); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] b = new byte[8192]; int n; while ((n = in.read(b)) != -1) out.write(b, 0, n); return out.toByteArray();
            }
        }
    }
}

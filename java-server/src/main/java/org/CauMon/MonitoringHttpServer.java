package org.CauMon;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import java.util.logging.Logger;

/**
 * 簡易HTTPサーバー - Webページから監視サーバーの起動・停止を制御
 *
 * エンドポイント:
 * - GET /       -> 制御用のHTML UIを返す
 * - POST /start -> JSON形式で signals, phi, port を受け取り、TCP監視サーバーを起動
 * - POST /stop  -> TCP監視サーバーを停止
 */
public class MonitoringHttpServer {

    private static final Logger logger = Logger.getLogger(MonitoringHttpServer.class.getName());
    private final MonitoringTCPServer monitoringServer = new MonitoringTCPServer();
    private HttpServer httpServer;

    /**
     * HTTPサーバーを起動する
     * @param httpPort HTTPサーバーのポート番号
     * @throws IOException サーバー起動に失敗した場合
     */
    public void start(int httpPort) throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(httpPort), 0);

        httpServer.createContext("/", new RootHandler());
        httpServer.createContext("/start", new StartHandler());
        httpServer.createContext("/stop", new StopHandler());
        httpServer.setExecutor(null);
        httpServer.start();
        logger.info("HTTP control server started on port " + httpPort);
    }

    /**
     * HTTPサーバーを停止する
     */
    public void stop() {
        if (httpServer != null) {
            httpServer.stop(1);
            logger.info("HTTP control server stopped.");
        }
    }

    /**
     * ルートハンドラー - HTML UIを返す
     */
    private class RootHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            // 白を基調としたシンプルでスタイリッシュなUI
            String html = "<!doctype html><html lang='ja'><head><meta charset='utf-8'>"
                    + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
                    + "<title>監視サーバー制御</title>"
                    + "<style>"
                    + ":root{--bg:#ffffff;--bg-sub:#f6f8fa;--text:#0f172a;--muted:#64748b;--primary:#2563eb;--primary-hover:#1d4ed8;--danger:#ef4444;--border:#e5e7eb;--ring:#93c5fd;}"
                    + "html,body{height:100%;}"
                    + "body{margin:0;background:var(--bg-sub);color:var(--text);font:14px/1.6 -apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,'Helvetica Neue','Noto Sans JP',sans-serif;}"
                    + "/* ラップ幅を少し広くしてレスポンシブに */"
                    + " .wrap{max-width:920px;width:calc(min(92vw,920px));margin:48px auto;padding:28px;}"
                    + " .card{background:var(--bg);border:1px solid var(--border);border-radius:12px;box-shadow:0 2px 18px rgba(0,0,0,.05);padding:28px;}"
                    + " h1{font-size:20px;margin:0 0 8px 0;}"
                    + " p.desc{margin:0 0 20px 0;color:var(--muted);}"
                    + " label{display:block;margin:12px 0 6px;font-weight:600;}"
                    + " /* 入力欄のはみ出し防止: box-sizing と min-width を指定 */"
                    + " input,textarea,select{box-sizing:border-box;min-width:0;width:100%;padding:10px 12px;border:1px solid var(--border);border-radius:8px;background:#fff;transition:border .2s,box-shadow .2s;}"
                    + " input:focus,textarea:focus,select:focus{outline:none;border-color:var(--ring);box-shadow:0 0 0 3px rgba(147,197,253,.45);}"
                    + " .row{display:flex;gap:12px;flex-wrap:wrap;}"
                    + " /* フレックスの子が縮むときは min-width:0 を許容するため、子要素に対しても適用 */"
                    + " .row > div{flex:1 1 160px;min-width:0;}"
                    + " .actions{margin-top:18px;display:flex;gap:12px;flex-wrap:wrap;}"
                    + " button{appearance:none;border:1px solid var(--border);background:#fff;color:var(--text);padding:10px 16px;border-radius:8px;font-weight:600;cursor:pointer;transition:background .2s,border-color .2s,transform .02s;}"
                    + " button:active{transform:translateY(1px);}"
                    + " .primary{background:var(--primary);border-color:var(--primary);color:#fff;}"
                    + " .primary:hover{background:var(--primary-hover);}"
                    + " .danger{border-color:var(--danger);color:var(--danger);}"
                    + " .danger:hover{background:#fee2e2;}"
                    + " #status{margin-top:18px;padding:12px 14px;border:1px solid var(--border);border-radius:8px;background:#fff;white-space:pre-wrap;font-family:ui-monospace,SFMono-Regular,Consolas,'Liberation Mono',Menlo,monospace;}"
                    + " #status.ok{border-color:#86efac;background:#f0fdf4;}"
                    + " #status.err{border-color:#fecaca;background:#fff1f2;}"
                    + " footer{margin-top:20px;color:var(--muted);font-size:12px;text-align:center;}"
                    + "</style></head><body>"
                    + "<div class='wrap'><div class='card'>"
                    + "<h1>監視サーバー制御パネル</h1>"
                    + "<p class='desc'>必要項目を入力してください。</p>"
                    + "<label for='signals'>シグナル名（カンマ区切り）</label>"
                    + "<input id='signals' value='time,speed,RPM'/>"
                    + "<label for='phi'>STL式（φ）</label>"
                    + "<input id='phi' value=\"alw_[0,27](not(speed[t]>50) or ev_[1,3](RPM[t] < 3000))\"/>"
                    + "<div class='row'>"
                    + "  <div style='flex:1 1 160px'>"
                    + "    <label for='port'>TCPポート番号</label>"
                    + "    <input id='port' type='number' value='9999'/>"
                    + "  </div>"
                    + "</div>"
                    + "<div class='actions'>"
                    + "  <button class='primary' onclick='startServer()'>サーバー起動</button>"
                    + "  <button class='danger' onclick='stopServer()'>サーバー停止</button>"
                    + "</div>"
                    + "<div id='status'>準備完了</div>"
                    + "</div><footer>© CauMon Server</footer></div>"
                    + "<script>"
                    + "function setStatus(text,isErr){var el=document.getElementById('status');el.textContent=text;el.className=isErr?'err':'ok';}"
                    + "function post(path,obj){setStatus('処理中...',false);"
                    + "  fetch(path,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(obj)})"
                    + "    .then(r=>r.text().then(t=>({ok:r.ok,text:t})))"
                    + "    .then(({ok,text})=>{var isErr=!ok||/失敗|エラー/i.test(text);setStatus(text,isErr);})"
                    + "    .catch(e=>setStatus('エラー: '+e,true));}"
                    + "function startServer(){post('/start',{"
                    + "  signals:document.getElementById('signals').value,"
                    + "  phi:document.getElementById('phi').value,"
                    + "  port:parseInt(document.getElementById('port').value||'9999')"
                    + "});}"
                    + "function stopServer(){post('/stop',{});}"
                    + "</script></body></html>";
            byte[] resp = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        }
    }

    /**
     * 起動ハンドラー - TCP監視サーバーを起動
     */
    private class StartHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            String body = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))
                    .lines().collect(Collectors.joining("\n"));

            // 簡易JSONパース（外部ライブラリを使わない）
            String signals = extractJsonValue(body, "signals");
            String phi = extractJsonValue(body, "phi");
            int port = 9999;
            String portStr = extractJsonValue(body, "port");
            try {
                if (portStr != null) port = Integer.parseInt(portStr);
            } catch (NumberFormatException ignored) {}

            String resp;
            try {
                monitoringServer.configure(signals, phi);
                monitoringServer.startServerAsync(port);
                resp = "サーバーを起動しました (ポート: " + port + ")\n"
                     + "シグナル: " + signals + "\n"
                     + "STL式: " + phi;
            } catch (Exception e) {
                resp = "サーバー起動に失敗しました: " + e.getMessage();
            }
            byte[] out = resp.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
            exchange.sendResponseHeaders(200, out.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(out);
            }
        }
    }

    /**
     * 停止ハンドラー - TCP監視サーバーを停止
     */
    private class StopHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            monitoringServer.stopServer();
            String resp = "サーバーを停止しました";
            byte[] out = resp.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
            exchange.sendResponseHeaders(200, out.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(out);
            }
        }
    }

    /**
     * 簡易JSON値抽出ヘルパー（外部ライブラリ不要）
     * @param json JSON文字列
     * @param key 抽出するキー
     * @return 値（文字列または数値）
     */
    private static String extractJsonValue(String json, String key) {
        if (json == null || key == null) return null;
        String q = "\"" + key + "\"";
        int idx = json.indexOf(q);
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx + q.length());
        if (colon < 0) return null;
        int i = colon + 1;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
        if (i >= json.length()) return null;
        char c = json.charAt(i);
        if (c == '\"') {
            int end = json.indexOf('\"', i + 1);
            if (end > i) return json.substring(i + 1, end);
        } else {
            // 数値等
            int j = i;
            while (j < json.length() && (Character.isDigit(json.charAt(j)) || json.charAt(j) == '-')) j++;
            return json.substring(i, j);
        }
        return null;
    }

    /**
     * メインメソッド - HTTPサーバーをポート8080で起動
     */
    public static void main(String[] args) throws Exception {
        MonitoringHttpServer s = new MonitoringHttpServer();
        s.start(8080);
        logger.info("ブラウザで http://localhost:8080 にアクセスしてください");
        // JVM 終了時に HTTP を停止
        Runtime.getRuntime().addShutdownHook(new Thread(() -> s.stop()));
    }
}


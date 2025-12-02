package org.CauMon;

import com.mathworks.engine.MatlabEngine;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * [成功版] 外部からのTCP/IP通信を待ち受けるリアルタイム監視サーバー。
 * 成功した 'eval' + 'trace文字列埋め込み' + 'n=1型チェック' のロジックを使用。
 * mainメソッドのみを 'for' ループから 'ServerSocket' に変更。
 */
public class MonitoringTCPServer {

    // loggerインスタンスの取得(存在しない場合は新規作成)
    // 慣例的にクラス名をloggerインスタンス名にして使用する
    private static final Logger logger = Logger.getLogger(MonitoringTCPServer.class.getName());
    private static final int PORT = 9999; // 待ち受けるTCPポート番号

    private MatlabEngine matlabEngine;
    private final List<double[]> javaTraceHistory = new ArrayList<>();

    // システムプロパティ(設定情報)からカレントディレクトリを取得
    // user.dir はJavaアプリケーションの起動ディレクトリを指す
    //　cauMonPath = "C:\\CauMonServer";
    private final String cauMonPath = System.getProperty("user.dir");

    // 非同期起動・停止用のフィールド
    private volatile boolean running = false;
    private ServerSocket serverSocket;
    private Thread acceptThread;
    private int tcpPort = PORT;

    // 設定可能にした文字列 (デフォルトは従来のもの)
    private String signalStr = "speed,RPM,gear";
    private String phiStr = "alw_[0,30](not(speed[t]>50) or (gear[t]>=3 and RPM[t]<4500))";

    // 追加: 可視化（MATLAB呼び出し）を間引くための設定
    // visualizeIntervalMillis: 最小時間間隔（ミリ秒）で可視化（デフォルト:1000ms）
    private volatile long visualizeIntervalMillis = 3000L;
    private volatile long lastVisualizeTimeMillis = 0L;

    // 追加: STL 評価の間隔（ミリ秒）。可視化とは独立に制御する。
    private volatile long stlEvalIntervalMillis = 1000L;
    private volatile long lastStlEvalTimeMillis = 0L;

    // MATLAB 上に最新の STL 結果があるかを示すフラグ
    private volatile boolean haveStlResults = false;

    /**
     * 可視化スロットリングの設定。
     * @param everyNSteps Nステップごとに1回可視化（1以上）
     * @param intervalMillis 最低間隔（ミリ秒、0で無効）
     */
    @SuppressWarnings("unused")
    public void setVisualizationThrottle(int everyNSteps, long intervalMillis) {
        // 従来互換のために残すが、everyNSteps は無視されます。
        if (intervalMillis >= 0) {
            this.visualizeIntervalMillis = intervalMillis;
        }
    }

    /**
     * 可視化間隔（ミリ秒）を設定する。0 を指定すると時間判定は無効化されます。
     * @param intervalMillis 最低間隔（ミリ秒、0で無効）
     */
    @SuppressWarnings("unused")
    public void setVisualizationIntervalMillis(long intervalMillis) {
        if (intervalMillis >= 0) {
            this.visualizeIntervalMillis = intervalMillis;
        }
    }

    /**
     * STL 評価間隔（ミリ秒）を設定する。可視化とは独立に制御する。
     * @param intervalMillis ミリ秒、0で無効（常に評価）
     */
    @SuppressWarnings("unused")
    public void setStlEvalIntervalMillis(long intervalMillis) {
        if (intervalMillis >= 0) {
            this.stlEvalIntervalMillis = intervalMillis;
        }
    }

    /**
     * サーバー起動前に信号名とSTL式を設定する
     * @param signals カンマ区切りの信号名 (例: "speed,RPM")
     * @param phi STL式 (例: "alw_[0,27](not(speed[t]>50) or ev_[1,3](RPM[t] < 3000))")
     */
    public void configure(String signals, String phi) {
        if (signals != null && !signals.isEmpty()) {
            // トリムと正規化
            String s = signals.trim();
            // 先頭に time または t がある場合は取り除く
            String[] parts = s.split(",");
            if (parts.length > 0) {
                // 空白を除去して小文字化して比較
                String first = parts[0].trim().toLowerCase();
                if (first.equals("time") || first.equals("t")) {
                    // 再結合（先頭を除く）
                    StringBuilder sb = new StringBuilder();
                    for (int i = 1; i < parts.length; i++) {
                        if (i > 1) sb.append(',');
                        sb.append(parts[i].trim());
                    }
                    s = sb.toString();
                }
            }
            this.signalStr = s;
        }
        if (phi != null && !phi.isEmpty()) {
            this.phiStr = phi;
        }
    }

    // サーバー起動時に呼び出され、MATLABエンジンを起動・設定する
    public void startup() throws Exception {
        // loggerインスタンスにinfoレベルのログを記録
        logger.info("Starting MATLAB engine...");

        // PCにインストールされているmatlabを起動し、Javaプログラムからの通信チャネルを確立
        matlabEngine = MatlabEngine.startMatlab();

        // cauMonPathに移動してからconfigure.mを実行
        // その後、'experiment'サブフォルダに移動 (visualize.mのため)
        try {
            // eval:matlabのコマンドウィンドウで実行するコマンドを文字列で指定
            matlabEngine.eval("cd '" + cauMonPath + "'");
            matlabEngine.eval("configure");
            matlabEngine.eval("cd 'experiment'");
            logger.info("Changed directory to 'experiment' subfolder.");
        } catch (Exception e) {
            // log: エラー発生時に詳細を記録、任意のタグ付けも可能
            logger.log(Level.SEVERE, "Failed to configure MATLAB path or run configure.m", e);
            throw e;
        }
        logger.info("MATLAB engine started and configured.");

        // ===== ウォームアップ: ダミー trace で一度だけ STL 評価と visualize を実行 =====
        try {
            logger.info("Warming up MATLAB visualization with dummy trace...");

            // シンプルなダミー trace: 時間 0,1,2 に対してゼロ信号（time + 3 signals を想定）
            // 実際の signalStr/phiStr に依存しないよう、安全な小さな値にする
            matlabEngine.eval("trace = [0 1 2; -50 -50 -50; 0 0 0; 1 1 1];\n");
            matlabEngine.eval("signal_str = '" + signalStr + "';\n");
            matlabEngine.eval("phi_str = '" + phiStr + "';\n");
            matlabEngine.eval("tau = 0;\n");

            long warmStart = System.currentTimeMillis();
            matlabEngine.eval("[up_robM, low_robM] = stl_eval_mex_pw(signal_str, phi_str, trace, tau);\n");
            matlabEngine.eval("[up_optCau, low_optCau] = stl_causation_opt(signal_str, phi_str, trace, tau);\n");
            matlabEngine.eval("visualize(trace, phi_str, up_robM, low_robM, up_optCau, low_optCau, '', signal_str);\n");
            long warmEnd = System.currentTimeMillis();
            logger.info(String.format("Warm-up visualize completed in %d ms", (warmEnd - warmStart)));

        } catch (Exception we) {
            // ウォームアップ失敗は致命的ではないので警告のみ
            logger.log(Level.WARNING, "Warm-up visualize failed (continuing without warm-up)", we);
        }
    }


    // サーバー終了時に呼び出され、MATLABエンジンを安全に停止する
    public void shutdown() {
        if (matlabEngine != null) {
            try {
                matlabEngine.close();
                logger.info("MATLAB engine shut down.");
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error shutting down MATLAB", e);
            } finally {
                matlabEngine = null;
            }
        }
    }

    /**
     * 新しいデータポイントを受信したときに呼び出される
     * @param newDataPoint 新しいデータ [time, signal1, signal2, ...]
     */
    public void onNewDataReceived(double[] newDataPoint) {

        // まず履歴にデータを追加（スレッドセーフ）
        synchronized (javaTraceHistory) {
            this.javaTraceHistory.add(newDataPoint);
        }

        int numTimeSteps;
        int numSignals;
        // 参照はロックして基本情報を取得
        synchronized (javaTraceHistory) {
            numTimeSteps = this.javaTraceHistory.size();
            if (numTimeSteps == 0) return;
            numSignals = this.javaTraceHistory.get(0).length;
        }
        if (numSignals == 0) { return; }

        long now = System.currentTimeMillis();

        // 判定: STL評価が必要か、可視化が必要か（それぞれ独立）
        boolean needStlEval = (stlEvalIntervalMillis <= 0) || ((now - lastStlEvalTimeMillis) >= stlEvalIntervalMillis);
        boolean needVisualize = (visualizeIntervalMillis <= 0) || ((now - lastVisualizeTimeMillis) >= visualizeIntervalMillis);

        if (!needStlEval && !needVisualize) {
            // どちらも不要ならば早期リターン
            logger.fine(String.format("Skipping both STL eval and visualize (traceSize=%d, elapsedStl=%dms, elapsedVis=%dms).",
                    numTimeSteps, (now - lastStlEvalTimeMillis), (now - lastVisualizeTimeMillis)));
            return;
        }

        // 履歴コピーは STL 評価または可視化時に必要になるため、条件付きで作成
        double[][] historyCopy = null;
        if (needStlEval || !haveStlResults) {
            historyCopy = new double[numTimeSteps][numSignals];
            synchronized (javaTraceHistory) {
                for (int t = 0; t < numTimeSteps; t++) {
                    double[] row = this.javaTraceHistory.get(t);
                    System.arraycopy(row, 0, historyCopy[t], 0, numSignals);
                }
            }
        }

        // STM: 実行順序は STL 評価 -> 可視化 を基本とする。
        try {
            if (needStlEval) {
                // 'trace' を MATLAB にセットし、STL 評価のみ行う（visualize は行わない）
                StringBuilder evalBuilder = new StringBuilder();
                evalBuilder.append("trace = [");
                for (int s = 0; s < numSignals; s++) {
                    for (int t = 0; t < numTimeSteps; t++) {
                        evalBuilder.append(historyCopy[t][s]);
                        if (t < numTimeSteps - 1) evalBuilder.append(" ");
                    }
                    if (s < numSignals - 1) evalBuilder.append("; ");
                }
                evalBuilder.append("];\n");
                evalBuilder.append("signal_str = '").append(signalStr).append("';\n");
                evalBuilder.append("phi_str = '").append(phiStr).append("';\n");
                evalBuilder.append("tau = 0;\n");
                evalBuilder.append("[up_robM, low_robM] = stl_eval_mex_pw(signal_str, phi_str, trace, tau);\n");
                evalBuilder.append("[up_optCau, low_optCau] = stl_causation_opt(signal_str, phi_str, trace, tau);\n");

                long stlStart = System.currentTimeMillis();
                matlabEngine.eval(evalBuilder.toString());
                long stlEnd = System.currentTimeMillis();
                logger.info(String.format("MATLAB stl_eval took %d ms (traceSize=%d)", (stlEnd - stlStart), numTimeSteps));

                // 結果を取得してログ
                Object up_robM_obj = matlabEngine.getVariable("up_robM");
                Object low_robM_obj = matlabEngine.getVariable("low_robM");

                double[] up_robM;
                double[] low_robM;

                if (up_robM_obj instanceof Double) {
                    up_robM = new double[] { (Double) up_robM_obj };
                    low_robM = new double[] { (Double) low_robM_obj };
                } else {
                    up_robM = (double[]) up_robM_obj;
                    low_robM = (double[]) low_robM_obj;
                }

                lastStlEvalTimeMillis = now;
                haveStlResults = true;

                if (up_robM != null && up_robM.length > 0) {
                    logger.info(String.format("STL evaluated (Trace size: %-4d) | Robustness len: %-4d | Last up=%.4f, low=%.4f",
                            numTimeSteps, up_robM.length, up_robM[up_robM.length - 1], low_robM[low_robM.length - 1]));
                }
            }

            // 可視化が必要なら、MATLAB 内の変数を使って visualize を呼び出す
            if (needVisualize) {
                // もし STL 評価をしていないが結果がない場合は、先に評価を行う
                if (!haveStlResults) {
                    // historyCopy は作成済み
                    StringBuilder evalBuilder = new StringBuilder();
                    evalBuilder.append("trace = [");
                    for (int s = 0; s < numSignals; s++) {
                        for (int t = 0; t < numTimeSteps; t++) {
                            evalBuilder.append(Objects.requireNonNull(historyCopy)[t][s]);
                            if (t < numTimeSteps - 1) evalBuilder.append(" ");
                        }
                        if (s < numSignals - 1) evalBuilder.append("; ");
                    }
                    evalBuilder.append("];\n");
                    evalBuilder.append("signal_str = '").append(signalStr).append("';\n");
                    evalBuilder.append("phi_str = '").append(phiStr).append("';\n");
                    evalBuilder.append("tau = 0;\n");
                    evalBuilder.append("[up_robM, low_robM] = stl_eval_mex_pw(signal_str, phi_str, trace, tau);\n");
                    evalBuilder.append("[up_optCau, low_optCau] = stl_causation_opt(signal_str, phi_str, trace, tau);\n");

                    long stlStart2 = System.currentTimeMillis();
                    matlabEngine.eval(evalBuilder.toString());
                    long stlEnd2 = System.currentTimeMillis();
                    logger.info(String.format("MATLAB stl_eval (fallback) took %d ms (traceSize=%d)", (stlEnd2 - stlStart2), numTimeSteps));
                    haveStlResults = true;
                    lastStlEvalTimeMillis = now;
                }

                // visualize を呼び出す（MATLAB内の trace と robustness 変数を使う）
                long visStart = System.currentTimeMillis();
                // 実行中はファイル保存を行わず、描画更新のみ行う（outfile を空文字にする想定）
                matlabEngine.eval("visualize(trace, phi_str, up_robM, low_robM, up_optCau, low_optCau, '', signal_str);\n");
                long visEnd = System.currentTimeMillis();
                logger.info(String.format("MATLAB visualize (runtime, no-save) took %d ms (traceSize=%d)", (visEnd - visStart), numTimeSteps));

                lastVisualizeTimeMillis = now;

                // optional: MATLAB から up_robM を取り出してログ
                try {
                    Object up_robM_obj2 = matlabEngine.getVariable("up_robM");
                    Object low_robM_obj2 = matlabEngine.getVariable("low_robM");
                    double[] up_robM2;
                    double[] low_robM2;
                    if (up_robM_obj2 instanceof Double) {
                        up_robM2 = new double[] { (Double) up_robM_obj2 };
                        low_robM2 = new double[] { (Double) low_robM_obj2 };
                    } else {
                        up_robM2 = (double[]) up_robM_obj2;
                        low_robM2 = (double[]) low_robM_obj2;
                    }
                    if (up_robM2 != null && up_robM2.length > 0) {
                        logger.info(String.format("Graph updated (Trace size: %-4d) | Last up=%.4f, low=%.4f",
                                numTimeSteps, up_robM2[up_robM2.length - 1], low_robM2[low_robM2.length - 1]));
                    }
                } catch (Exception e) {
                    // ignore logging errors
                }
            }

        } catch (Exception e) {
            if (e.getClass().getName().contains("MatlabException")) {
                logger.log(Level.SEVERE, "MATLAB execution/engine exception (e.g., crash):", e);
            } else {
                logger.log(Level.SEVERE, "General error calling MATLAB", e);
            }
        }
    }

    /**
     * TCPサーバーを非同期で起動する（HTTPサーバーから呼び出すことを想定）
     * @param port 待ち受けるTCPポート番号
     * @throws Exception MATLAB起動やソケット作成に失敗した場合
     */
    public synchronized void startServerAsync(int port) throws Exception {
        if (running) {
            logger.info("Server already running.");
            return;
        }
        this.tcpPort = port;
        startup();
        serverSocket = new ServerSocket(tcpPort);
        running = true;

        acceptThread = new Thread(() -> {
            logger.info("Server is listening on port " + tcpPort);
            while (running) {
                try (Socket clientSocket = serverSocket.accept()) {
                    logger.info("Client connected from: " + clientSocket.getInetAddress());
                    try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))) {
                        String rawLine;
                        while ((rawLine = in.readLine()) != null) {
                            // trim と空行チェック
                            String line = rawLine.trim();
                            if (line.isEmpty()) {
                                logger.fine("Skipping empty/whitespace line from client.");
                                continue;
                            }
                            // 先頭にプレフィックスがある場合、数字から始まる部分を抽出
                            int idx = -1;
                            for (int i = 0; i < line.length(); i++) {
                                char c = line.charAt(i);
                                if ((c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.') {
                                    idx = i;
                                    break;
                                }
                            }
                            if (idx > 0) {
                                line = line.substring(idx).trim();
                            }
                            if (line.isEmpty()) {
                                logger.fine("Skipping line after stripping prefix: '" + rawLine + "'");
                                continue;
                            }

                            logger.info("Received data: " + line);

                            // カンマで分割した後、空トークンを削除してからパース
                            String[] rawParts = line.split(",");
                            List<String> partsList = new ArrayList<>();
                            for (String p : rawParts) {
                                if (p == null) continue;
                                String t = p.trim();
                                if (!t.isEmpty()) partsList.add(t);
                            }
                            if (partsList.size() < 2) { // 少なくとも time と 1 signal を期待
                                logger.warning("Received malformed data: " + rawLine);
                                continue;
                            }

                            try {
                                double[] newData = new double[partsList.size()];
                                for (int i = 0; i < partsList.size(); i++) {
                                    newData[i] = Double.parseDouble(partsList.get(i));
                                }
                                onNewDataReceived(newData);
                            } catch (NumberFormatException e) {
                                logger.warning("Failed to parse data to double: " + rawLine);
                            }
                        }
                    }
                    logger.info("Client disconnected.");
                    finalizeVisualizationAndSave("result_realtime.png");
                } catch (IOException e) {
                    if (running) {
                        logger.log(Level.WARNING, "Error during client connection", e);
                    } else {
                        logger.info("Server socket closed; stopping accept loop.");
                    }
                }
            }
            logger.info("Accept thread exiting.");
            finalizeVisualizationAndSave("result_realtime.png");
        }, "MonitoringTCP-AcceptThread");

        acceptThread.start();
    }

    /**
     * TCPサーバーを停止する（HTTPサーバーから呼び出すことを想定）
     */
    public synchronized void stopServer() {
        if (!running) {
            logger.info("Server not running.");
            return;
        }
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close(); // accept を終了させる
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Error closing server socket", e);
        }
        try {
            if (acceptThread != null) {
                acceptThread.join(2000);
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        finalizeVisualizationAndSave("result_realtime.png");

        shutdown(); // MATLAB 停止
        logger.info("Monitoring TCP server stopped.");
    }

    /**
     * 受信した全履歴を用いて MATLAB 側のグラフを最終更新し、PNG を保存する共通処理。
     * @param outfile 保存先ファイル名（null/空の場合はデフォルト名）
     */
    private synchronized void finalizeVisualizationAndSave(String outfile) {
        if (matlabEngine == null) {
            logger.fine("MATLAB engine unavailable; skipping final visualization.");
            return;
        }
        if (outfile == null || outfile.isEmpty()) {
            outfile = "result_realtime.png";
        }
        int numTimeSteps;
        int numSignals;
        synchronized (javaTraceHistory) {
            numTimeSteps = javaTraceHistory.size();
            numSignals = (numTimeSteps > 0) ? javaTraceHistory.get(0).length : 0;
        }
        if (numTimeSteps == 0 || numSignals == 0) {
            logger.info("No trace data available for final visualization.");
            return;
        }

        double[][] historyCopy = new double[numTimeSteps][numSignals];
        synchronized (javaTraceHistory) {
            for (int t = 0; t < numTimeSteps; t++) {
                double[] row = javaTraceHistory.get(t);
                System.arraycopy(row, 0, historyCopy[t], 0, numSignals);
            }
        }

        StringBuilder evalBuilder = new StringBuilder();
        evalBuilder.append("trace = [");
        for (int s = 0; s < numSignals; s++) {
            for (int t = 0; t < numTimeSteps; t++) {
                evalBuilder.append(historyCopy[t][s]);
                if (t < numTimeSteps - 1) {
                    evalBuilder.append(" ");
                }
            }
            if (s < numSignals - 1) {
                evalBuilder.append("; ");
            }
        }
        evalBuilder.append("];\n");
        evalBuilder.append("signal_str = '").append(signalStr).append("';\n");
        evalBuilder.append("phi_str = '").append(phiStr).append("';\n");
        evalBuilder.append("tau = 0;\n");
        evalBuilder.append("[up_robM, low_robM] = stl_eval_mex_pw(signal_str, phi_str, trace, tau);\n");
        evalBuilder.append("[up_optCau, low_optCau] = stl_causation_opt(signal_str, phiStr, trace, tau);\n");

        try {
            // 最終評価実行
            long saveStart = System.currentTimeMillis();
            matlabEngine.eval(evalBuilder.toString());

            // 可視化＋保存用の呼び出し（MATLAB 側で必要に応じて exportgraphics を実装する想定）
            String safeOutfile = outfile.replace("'", "''");
            matlabEngine.eval("visualize(trace, phi_str, up_robM, low_robM, up_optCau, low_optCau, '" + safeOutfile + "', signal_str);\n");
            long saveEnd = System.currentTimeMillis();
            logger.info(String.format("Final visualize (with save) took %d ms (traceSize=%d)", (saveEnd - saveStart), numTimeSteps));
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error during final visualization/save", e);
        }
    }

    // TCPサーバーを実行する main メソッド
    public static void main(String[] args) {
        MonitoringTCPServer server = new MonitoringTCPServer();

        // JVM終了時(Ctrl+Cなど)にMATLABを安全にシャットダウンするためのフック
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown hook triggered. Saving latest graph and shutting down MATLAB...");
            server.finalizeVisualizationAndSave("result_realtime.png");
            server.shutdown();
            logger.info("Shutdown complete.");
        }));

        try {
            // 1. サーバー（とMATLAB）を起動
            server.startup();
            logger.info("MATLAB engine started. Starting TCP server on port " + PORT);

            // 2. TCPサーバーソケットを作成
            try (ServerSocket serverSocket = new ServerSocket(PORT)) {
                logger.info("Server is listening... waiting for client connection.");

                // 3. クライアントの接続を待機 (無限ループ)
                while (true) {
                    // accept: 一時停止し、クライアントからの接続要求を待ち受ける
                    try (Socket clientSocket = serverSocket.accept()) {
                        logger.info("Client connected from: " + clientSocket.getInetAddress());

                        // 4. クライアントからのデータストリームを読み取る
                        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))) {
                            String rawLine;
                            // クライアントが切断する(null)まで1行ずつ読み続ける
                            while ((rawLine = in.readLine()) != null) {
                                // trim と空行チェック
                                String line = rawLine.trim();
                                if (line.isEmpty()) {
                                    logger.fine("Skipping empty/whitespace line from client.");
                                    continue;
                                }
                                // 先頭にプレフィックスがある場合、数字から始まる部分を抽出
                                int idx = -1;
                                for (int i = 0; i < line.length(); i++) {
                                    char c = line.charAt(i);
                                    if ((c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.') {
                                        idx = i;
                                        break;
                                    }
                                }
                                if (idx > 0) {
                                    line = line.substring(idx).trim();
                                }
                                if (line.isEmpty()) {
                                    logger.fine("Skipping line after stripping prefix: '" + rawLine + "'");
                                    continue;
                                }
                                logger.info("Received data: " + line);

                                // 5. 受信した文字列をパース
                                try {
                                    // 期待する形式: "time,speed,rpm" (例: "0.5,40.0,2050.0")
                                    String[] rawParts = line.split(",");
                                    List<String> partsList = new ArrayList<>();
                                    for (String p : rawParts) {
                                        if (p == null) continue;
                                        String t = p.trim();
                                        if (!t.isEmpty()) partsList.add(t);
                                    }

                                    // 入力されたデ��タ数が3つでない場合は警告を出してスキップ
                                    if (partsList.size() != 3) { // [time, speed, RPM]
                                        logger.warning("Received malformed data (expected 3 parts): " + rawLine);
                                        continue;
                                    }

                                    double[] newData = new double[3];
                                    newData[0] = Double.parseDouble(partsList.get(0)); // time
                                    newData[1] = Double.parseDouble(partsList.get(1)); // speed
                                    newData[2] = Double.parseDouble(partsList.get(2)); // RPM

                                    // 6. サーバーに新しいデータを渡す
                                    server.onNewDataReceived(newData);

                                } catch (NumberFormatException e) {
                                    logger.warning("Failed to parse data to double: " + rawLine);
                                }
                            }
                        }
                        // クライアントが切断した場合、readLine()の戻り値はnullになる
                        // すると、whileループを抜けてここに到達する
                        logger.info("Client disconnected.");
                        server.finalizeVisualizationAndSave("result_realtime.png");
                    } catch (IOException e) {
                        logger.log(Level.WARNING, "Error during client connection", e);
                    }
                }
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Could not start TCP server on port " + PORT, e);
            }

        } catch (Exception e) {
            // MATLAB起動失敗など
            logger.log(Level.SEVERE, "An error occurred during server startup", e);
            server.finalizeVisualizationAndSave("result_realtime.png");
        } finally {
            server.finalizeVisualizationAndSave("result_realtime.png");
            server.shutdown();
        }
    }
}


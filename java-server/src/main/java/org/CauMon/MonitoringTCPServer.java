package org.CauMon;

import com.mathworks.engine.MatlabEngine;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
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
    //　cauMonPath = "C:\CauMonServer";
    private final String cauMonPath = System.getProperty("user.dir");

    // 非同期起動・停止用のフィールド
    private volatile boolean running = false;
    private ServerSocket serverSocket;
    private Thread acceptThread;
    private int tcpPort = PORT;

    // 設定可能にした文字列 (デフォルトは従来のもの)
    private String signalStr = "speed,RPM";
    private String phiStr = "alw_[0,27](not(speed[t]>50) or ev_[1,3](RPM[t] < 3000))";

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

        this.javaTraceHistory.add(newDataPoint);
        // 配列の長さを取得
        int numTimeSteps = this.javaTraceHistory.size();

        // [time, speed, RPM] : numSignals = 3
        int numSignals = this.javaTraceHistory.get(0).length;
        if (numSignals == 0) { return; }

        // 'trace' を文字列として構築
        // 例：trace = [0.0 0.5 1.0; 40.0 45.0 50.0; 2000.0 2100.0 2200.0]
        StringBuilder scriptBuilder = new StringBuilder();
        scriptBuilder.append("trace = [");
        for (int s = 0; s < numSignals; s++) { // 行 (シグナル)
            for (int t = 0; t < numTimeSteps; t++) { // 列 (時刻)
                scriptBuilder.append(this.javaTraceHistory.get(t)[s]);
                if (t < numTimeSteps - 1) scriptBuilder.append(" ");
            }
            if (s < numSignals - 1) scriptBuilder.append("; ");
        }
        scriptBuilder.append("];\n"); // 行列の終わり

        // その他の引数を追加
        scriptBuilder.append("signal_str = '").append(signalStr).append("';\n");
        scriptBuilder.append("phi_str = '").append(phiStr).append("';\n");
        scriptBuilder.append("tau = 0;\n");

        // stl_eval_mex_pw と stl_causation_opt の呼び出し
        scriptBuilder.append("[up_robM, low_robM] = stl_eval_mex_pw(signal_str, phi_str, trace, tau);\n");
        scriptBuilder.append("[up_optCau, low_optCau] = stl_causation_opt(signal_str, phi_str, trace, tau);\n");

        // visualize の呼び出し (グラフを更新してPNG保存) - signal_strを追加
        scriptBuilder.append("visualize(trace, phi_str, up_robM, low_robM, up_optCau, low_optCau, 'result_realtime.png', signal_str);\n");

        try {
            // 完成したスクリプト文字列を 'eval' で実行
            matlabEngine.eval(scriptBuilder.toString());

            // MATLABから結果を取得
            // n=1 (Double) と n>1 (double[]) の両方に対応するためにObjectで受け取る
            Object up_robM_obj = matlabEngine.getVariable("up_robM");
            Object low_robM_obj = matlabEngine.getVariable("low_robM");

            double[] up_robM;
            double[] low_robM;

            if (up_robM_obj instanceof Double) {
                // n=1 (Double)ならば double[] に変換
                up_robM = new double[] { (Double) up_robM_obj };
                low_robM = new double[] { (Double) low_robM_obj };
            } else {
                // n>1 (double[])ならばそのままキャスト
                up_robM = (double[]) up_robM_obj;
                low_robM = (double[]) low_robM_obj;
            }

            // 結果を表示
            if (up_robM != null && up_robM.length > 0) {
                logger.info(String.format("Trace size: %-4d | Robustness array length: %-4d | Last Robustness: up=%.4f, low=%.4f (Graph updated)",
                        numTimeSteps,
                        up_robM.length,
                        up_robM[up_robM.length - 1],
                        low_robM[low_robM.length - 1]));
            } else {
                logger.warning("Robustness result was null.");
            }

        } catch (Exception e) {
            // matlab固有の例外と一般例外を区別してログ出力
            if (e instanceof com.mathworks.engine.MatlabException) {
                logger.log(Level.SEVERE, "MATLAB execution/engine exception (e.g., crash):", e);
            } else {
                logger.log(Level.SEVERE, "General error calling MATLAB", e);
            }
            // (注: リアルタイムサーバーではクラッシュ時に RuntimeException をスローすると
            // サーバー全体が停止する可能性があるため、ロギングのみに留める)
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
                        String line;
                        while ((line = in.readLine()) != null) {
                            logger.info("Received data: " + line);
                            try {
                                String[] parts = line.split(",");
                                if (parts.length < 2) { // 少なくとも time と 1 signal を期待
                                    logger.warning("Received malformed data: " + line);
                                    continue;
                                }
                                double[] newData = new double[parts.length];
                                for (int i = 0; i < parts.length; i++) {
                                    newData[i] = Double.parseDouble(parts[i]);
                                }
                                onNewDataReceived(newData);
                            } catch (NumberFormatException e) {
                                logger.warning("Failed to parse data to double: " + line);
                            }
                        }
                    }
                    logger.info("Client disconnected.");
                } catch (IOException e) {
                    if (running) {
                        logger.log(Level.WARNING, "Error during client connection", e);
                    } else {
                        logger.info("Server socket closed; stopping accept loop.");
                    }
                }
            }
            logger.info("Accept thread exiting.");
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
        shutdown(); // MATLAB 停止
        logger.info("Monitoring TCP server stopped.");
    }

    // TCPサーバーを実行する main メソッド
    public static void main(String[] args) {
        MonitoringTCPServer server = new MonitoringTCPServer();

        // JVM終了時(Ctrl+Cなど)にMATLABを安全にシャットダウンするためのフック
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown hook triggered. Shutting down MATLAB...");
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
                            String line;
                            // クライアントが切断する(null)まで1行ずつ読み続ける
                            while ((line = in.readLine()) != null) {
                                logger.info("Received data: " + line);

                                // 5. 受信した文字列をパース
                                try {
                                    // 期待する形式: "time,speed,rpm" (例: "0.5,40.0,2050.0")
                                    String[] parts = line.split(",");

                                    // 入力されたデータ数が3つでない場合は警告を出してスキップ
                                    if (parts.length != 3) { // [time, speed, RPM]
                                        logger.warning("Received malformed data (expected 3 parts): " + line);
                                        continue;
                                    }

                                    double[] newData = new double[3];
                                    newData[0] = Double.parseDouble(parts[0]); // time
                                    newData[1] = Double.parseDouble(parts[1]); // speed
                                    newData[2] = Double.parseDouble(parts[2]); // RPM

                                    // 6. サーバーに新しいデータを渡す
                                    server.onNewDataReceived(newData);

                                } catch (NumberFormatException e) {
                                    logger.warning("Failed to parse data to double: " + line);
                                }
                            }
                        }
                        // クライアントが切断した場合、readLine()の戻り値はnullになる
                        // すると、whileループを抜けてここに到達する
                        logger.info("Client disconnected.");
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
        }
    }
}
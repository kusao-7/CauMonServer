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
 *
 * 成功した 'eval' + 'trace文字列埋め込み' + 'n=1型チェック' のロジックを使用。
 * mainメソッドのみを 'for' ループから 'ServerSocket' に変更。
 */
public class MonitoringTCPServer {

    private static final Logger logger = Logger.getLogger(MonitoringTCPServer.class.getName());
    private static final int PORT = 9999; // 待ち受けるTCPポート番号

    private MatlabEngine matlabEngine;
    private final List<double[]> javaTraceHistory = new ArrayList<>();

    // --- ユーザー設定 (必須) ---
    private final String cauMonPath = "C:\\CauMonServer";

    /**
     * サーバー起動時に呼び出され、MATLABエンジンを起動・設定します。
     * (このメソッドは成功したコードから変更なし)
     */
    public void startup() throws Exception {
        logger.info("Starting MATLAB engine...");
        matlabEngine = MatlabEngine.startMatlab();
        try {
            matlabEngine.eval("cd '" + cauMonPath + "'");
            matlabEngine.eval("configure");
            matlabEngine.eval("cd 'experiment'"); // visualize.m のため
            logger.info("Changed directory to 'experiment' subfolder.");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to configure MATLAB path or run configure.m", e);
            throw e;
        }
        logger.info("MATLAB engine started and configured.");
    }

    /**
     * サーバー終了時に呼び出され、MATLABエンジンを安全に停止します。
     * (このメソッドは成功したコードから変更なし)
     */
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
     * 新しいデータポイントを受信したときに呼び出されます。
     * (このメソッドは成功したコードから変更なし)
     * @param newDataPoint 新しいデータ [time, signal1, signal2, ...]
     */
    public void onNewDataReceived(double[] newDataPoint) {

        this.javaTraceHistory.add(newDataPoint);
        int numTimeSteps = this.javaTraceHistory.size(); // 時刻ステップ数 (n)

        int numSignals = this.javaTraceHistory.get(0).length; // (例: 3)
        if (numSignals == 0) { return; }

        // 'trace' を文字列として構築
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

        // 'test.m' の残りの部分を文字列に追加 (visualize も含む)
        scriptBuilder.append("signal_str = 'speed,RPM';\n");
        scriptBuilder.append("phi_str = 'alw_[0,27](not(speed[t]>50) or ev_[1,3](RPM[t] < 3000))';\n");
        scriptBuilder.append("tau = 0;\n");
        scriptBuilder.append("[up_robM, low_robM] = stl_eval_mex_pw(signal_str, phi_str, trace, tau);\n");
        scriptBuilder.append("[up_optCau, low_optCau] = stl_causation_opt(signal_str, phi_str, trace, tau);\n");
        scriptBuilder.append("visualize(trace, phi_str, up_robM, low_robM, up_optCau, low_optCau, 'result_realtime.png');\n");

        try {
            // 完成したスクリプト文字列を 'eval' で実行
            matlabEngine.eval(scriptBuilder.toString());

            // n=1 (Double) と n>1 (double[]) の両方に対応
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

            // 結果を表示
            if (up_robM != null && up_robM.length > 0) {
                logger.info(String.format("Trace size: %-4d | Last Robustness: up=%.4f, low=%.4f (Graph updated)",
                        numTimeSteps,
                        up_robM[up_robM.length - 1],
                        low_robM[low_robM.length - 1]));
            } else {
                logger.warning("Robustness result was null.");
            }

        } catch (Exception e) {
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
     * TCPサーバーを実行する main メソッド。
     * (シミュレーション用の 'for' ループを 'ServerSocket' に置き換え)
     */
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
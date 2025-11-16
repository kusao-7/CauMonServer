# WebUIを使った監視サーバーの起動方法

## 概要

Webページから監視サーバーを起動・停止できるようになりました。
シグナル名とSTL式を入力して、サーバーを制御できます。

## 起動手順

### 1. HTTPサーバーの起動

#### 方法A: IDEから実行（推奨）

1. IntelliJ IDEA で `java-server/src/main/java/org/CauMon/MonitoringHttpServer.java` を開く
2. `main` メソッドの横にある緑色の実行ボタン（▶）をクリック
3. または、ファイル上で右クリック → 「実行 'MonitoringHttpServer.main()'」

#### 方法B: Mavenから実行（Maven がインストールされている場合）

```bash
cd java-server
mvn clean compile
mvn exec:java -Dexec.mainClass="org.CauMon.MonitoringHttpServer"
```

#### 方法C: コンパイル済みクラスから実行

```bash
cd java-server/target/classes
java -cp ".;C:/Program Files/MATLAB/R2025b/extern/engines/java/jar/engine.jar" org.CauMon.MonitoringHttpServer
```

### 2. Webページにアクセス

ブラウザで以下のURLにアクセスします：

```
http://localhost:8080
```

### 3. 設定入力

以下の項目を入力します：

- **シグナル名（カンマ区切り）**: 例: `time,speed,RPM`
- **STL式（φ）**: 例: `alw_[0,27](not(speed[t]>50) or ev_[1,3](RPM[t] < 3000))`
- **TCPポート番号**: 例: `9999`

### 4. サーバー起動

「サーバー起動」ボタンをクリックすると、入力した設定でTCP監視サーバーが起動します。

### 5. データ送信

別のターミナルから、TCPクライアントでデータを送信します：

```bash
cd ..
python src/compile/TcpMockDataSender.py
```

### 6. サーバー停止

「サーバー停止」ボタンをクリックすると、TCP監視サーバーが停止します。

## 従来の起動方法（CLI）

従来通り、`MonitoringTCPServer` の `main` メソッドを直接実行することもできます。
この場合、設定はデフォルト値（`speed,RPM` と既定のSTL式）が使用されます。

## 技術詳細

### アーキテクチャ

```
MonitoringHttpServer (Port 8080)
  ↓ 制御
MonitoringTCPServer (Port 9999, 設定可能)
  ↓ データ受信
TCPクライアント（Pythonスクリプトなど）
```

### 主な変更点

1. **MonitoringTCPServer**
   - `configure(String signals, String phi)`: 信号名とSTL式を設定
   - `startServerAsync(int port)`: 非同期でTCPサーバーを起動
   - `stopServer()`: TCPサーバーを停止
   - 従来の `main` メソッドは維持（下位互換性）

2. **MonitoringHttpServer**（新規）
   - `GET /`: Web UIを配信
   - `POST /start`: TCP監視サーバーを起動
   - `POST /stop`: TCP監視サーバーを停止

### 注意事項

- 入力値の検証は最小限です。運用環境では強化が必要です。
- STL式に含まれる特殊文字（シングルクォートなど）は適切にエスケープしてください。
- セキュリティ対策（認証、HTTPS等）は実装されていません。
- 同時に複数のサーバーインスタンスを起動することはできません。


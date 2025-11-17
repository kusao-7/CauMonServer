# ✨ グラフの縦軸ラベル動的表示機能 - 実装完了

## 📋 概要

グラフの縦軸ラベルとタイトルが、**Web UIから入力したシグナル名**を使って動的に表示されるようになりました。

---

## 🎯 変更されたファイル

### 1. `experiment/visualize.m`
- 新しい引数 `signal_names` を追加
- シグナル名をパースして各グラフに適用
- 後方互換性を維持（引数省略時は従来の表示）

### 2. `java-server/src/main/java/org/CauMon/MonitoringTCPServer.java`
- `visualize` 関数呼び出し時に `signal_str` を渡すように変更

---

## 📊 実際の表示例

### Before（変更前）
```
┌─────────────────┐
│   Signal 1      │  ← 汎用的な名前
│                 │
│ Value           │  ← 汎用的なラベル
└─────────────────┘
```

### After（変更後）
```
┌─────────────────┐
│   temp          │  ← Web UIで入力した名前
│                 │
│ temp            │  ← 実際のシグナル名
└─────────────────┘
```

---

## 🧪 テスト手順

### ステップ1: サーバー起動
IntelliJ IDEAで `MonitoringHttpServer.java` を実行

### ステップ2: Web UI設定
http://localhost:8080 にアクセスして入力：

- **シグナル名**: `temp,cooling`
- **STL式**: `alw_[0,10](temp[t] < 100)`
- **ポート**: `9999`

「サーバー起動」をクリック

### ステップ3: データ送信
```powershell
.\experiment\send_test_data.ps1
```

### ステップ4: 結果確認
MATLABのFigureウィンドウで確認：

- **1つ目のグラフ**
  - タイトル: `temp`
  - 縦軸: `temp`

- **2つ目のグラフ**
  - タイトル: `cooling`
  - 縦軸: `cooling`

---

## 💡 技術的な詳細

### シグナル名のパース
```matlab
signal_name_list = strsplit(signal_names, ',');
signal_name_list = strtrim(signal_name_list);
```
- カンマで分割
- 前後の空白を削除

### Interpreter設定
```matlab
'Interpreter', 'none'
```
- アンダースコア（`motor_speed`）などをそのまま表示
- LaTeX解釈を無効化

### デフォルト動作
引数を省略した場合は従来通り「Signal 1」「Signal 2」を表示

---

## 🎨 様々なシグナル名での例

### 例1: 温度制御
**入力**: `temp,cooling`
```
グラフ1: temp
グラフ2: cooling
```

### 例2: バッテリー監視
**入力**: `voltage,current,soc`
```
グラフ1: voltage
グラフ2: current
グラフ3: soc
```

### 例3: ロボット制御
**入力**: `position,velocity,target`
```
グラフ1: position
グラフ2: velocity
グラフ3: target
```

### 例4: アンダースコア付き
**入力**: `motor_speed,wheel_rpm`
```
グラフ1: motor_speed  ← アンダースコアもそのまま表示
グラフ2: wheel_rpm
```

---

## ✅ メリット

1. **直感的**: グラフを見ただけで何のシグナルかわかる
2. **柔軟**: 任意のシグナル名に対応
3. **後方互換性**: 既存のスクリプトは影響を受けない
4. **デバッグ容易**: MATLAB Command Windowにシグナル名が表示される

---

## 🔍 デバッグ情報

MATLAB Command Windowの出力例：
```
=== DEBUG INFO ===
Signal names: temp,cooling
Number of signals: 2
Time array length: 11
up_robM length: 11
low_robM length: 11
...
==================
```

---

## 📚 関連ドキュメント

- メインREADME: `README.md`
- Web UI詳細: `java-server/README_WEB_UI.md`
- この機能の説明: `experiment/DYNAMIC_YLABEL_FEATURE.md`

---

## 🎉 完成！

これでグラフがより使いやすくなりました。
複数のシグナルを監視する際、一目で何のデータかわかります。

**実装日**: 2025年11月17日


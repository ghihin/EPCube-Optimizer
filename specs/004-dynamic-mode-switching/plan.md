# Implementation Plan - Dynamic Mode Switching (Ver 3.0)

## 概要
「現在のバッテリーSOC」と「翌日の天気予報結果（晴れ）」を組み合わせて、グリーンモードとスマートモードの２つのモード間を動的に切り替える。

## UI遷移のアプローチ方針
スマートモードの設定時において、ラジオボタン選択後すぐにスライダーが現れない・反映されない挙動を想定し、一度「スマートモードにして設定（保存）」し、再度「スライダーを操作して設定（保存）」する確実な2段階操作フローにて実装する。

## 変更内容詳細

### 1. ドメイン層 (Domain)
- **CalculateTargetSocUseCase**: `TargetSocResult` に `isSunnyTomorrow: Boolean` プロパティを追加し、天気の晴天条件（`cloudiness <= 30 && pop < 0.2f` など計算済みのもの）を満たすかどうかの結果を保持させる。
- **CalculateTargetSocUseCaseTest**: 新しい戻り値に関するユニットテスト検証の追加。

### 2. データ・UI層 (Presentation/Model)
- **DailySchedule**: UIで判定結果を保持・利用できるように `isSunnyTomorrow: Boolean = false` のプロパティを追加。
- **ScheduleViewModel**: 自動スケジュール予測の一覧作成時、ユースケースの戻り値から `isSunnyTomorrow` を `DailySchedule` にマッピングする。
- **ScheduleViewerScreen**: スケジュール一覧の各カードにおいて、翌日の天気が晴れ（`isSunnyTomorrow == true`）の場合は「🌿 グリーンモード対象 (※SOC≥60%時)」、それ以外は「🔋 スマートモード予定」の表示ラベルを追加し、視覚的な予定の把握を改善する。

### 3. 自動化連携・インテント (Automation)
- **AutomationOrchestrator**: マクロサービス呼び出し時（深夜実行時など）、 `TargetSocResult` の `isSunnyTomorrow` 値を Intent Extras に `EXTRA_IS_SUNNY_TOMORROW` として追加して送出する。

### 4. マクロ・UIスクレイピング (AccessibilityService)
- **EpCubeAccessibilityService**:
  - `startMacro()` 等の初期化にて Intent から `EXTRA_IS_SUNNY_TOMORROW` を抽出。
  - **Read（Scraping & 状態確認）**: 画面起動後の待機状態にて、ホーム画面のテキストから正規表現 `\((\d+)%\)` を用いて「カッコで囲まれた」現在のバッテリーSOC値を取得する（自給率等の誤検知防止）。同時に「現在の運転モード名」も取得する。
  - **分岐ロジックと冪等性**: `currentSoc >= 60 && isSunnyTomorrow` ならグリーンモード、それ以外ならスマートモード。切替先のモードがすでに現在のモードと一致している場合は、不要なモード切替操作（グリーンモード時のラジオ選択や、スマートモード時の再保存フロー）をスキップし、必要最低限の操作のみとする。
  - **UI変更処理**: 上記判断に従い、必要な場合のみ各種ボタンタップやスライダー操作を実行する。

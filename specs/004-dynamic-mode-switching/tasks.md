# 開発タスク - Dynamic Mode Switching (Ver 3.0)

## 1. ドメインロジックの実装
- [x] `DailySchedule` クラスへの `isSunnyTomorrow` プロパティ追加
- [x] `CalculateTargetSocUseCase` の戻り値 `TargetSocResult` への `isSunnyTomorrow` プロパティ追加
- [x] 上記 UseCase 内の晴れ判定結果のフラグセット処理
- [x] `CalculateTargetSocUseCaseTest` の変更対応

## 2. 自動化連携の修正
- [x] `AutomationOrchestrator` から発行されるIntentに `EXTRA_IS_SUNNY_TOMORROW` フラグを追加

## 3. UIの改修
- [x] `ScheduleViewModel` で `CalculateTargetSocUseCase` の値を `DailySchedule` オブジェクトへマッピング
- [x] `ScheduleViewerScreen` のカード部に、予定されるモードの視覚的バッジ（「🌿 グリーンモード対象…」「🔋 スマートモード予定」）を表示するよう改修

## 4. AccessibilityService の機能追加
- [x] Intentからの `EXTRA_IS_SUNNY_TOMORROW` パラメータ読み取り追加
- [x] アプリ起動後、ホーム画面から正規表現等による現在のSOC読み取り（Readフェーズ）機能を追加
- [x] モード分岐ロジックの実装（1.SOC >= 60%、2.翌日晴れ で判定）
- [x] 新しいActionフェーズ（Green Mode：モード選択 → 確認 → 設定）の実装
- [x] 新しいActionフェーズ（Smart Mode：モード選択 → 設定 → スライダー調整 → 設定）の実装
- [x] `pollForNodeByText` などの既存メソッドを用いた「運転モードボタン」検知方法のアップデート

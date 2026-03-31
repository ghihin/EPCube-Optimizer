# データモデル: EP CUBE マクロアクセシビリティ

## エンティティ

### 1. TargetSOC (既存)
- **説明**: EPCubeOptimizerで算出された充電上限の目標値。
- **フィールド**:
  - `value`: Int (0〜100の整数値)

### 2. MacroState (新規)
- **説明**: 自動操作の現在の進行状態を表す列挙型（Enum）。
- **値**:
  - `IDLE`: 待機中（マクロ未実行）
  - `STARTING`: EP CUBEアプリ起動中
  - `WAITING_FOR_HOME`: ホーム画面の「スマートモード」設定ボタン待機中
  - `WAITING_FOR_SETTINGS`: 運転モード切替画面の「充電上限」待機中
  - `ADJUSTING_SOC`: 目標SOCへの値変更操作中
  - `SAVING`: 「設定」ボタンクリック後の保存処理中
  - `WAITING_FOR_SUCCESS`: 20秒待機後の設定値再確認中
  - `COMPLETED`: マクロ正常終了
  - `FAILED`: エラー発生（タイムアウト、要素が見つからない等）

### 3. MacroResult (新規)
- **説明**: マクロ実行結果をEPCubeOptimizerに返すためのデータクラス。
- **フィールド**:
  - `isSuccess`: Boolean (成功したかどうか)
  - `targetSoc`: Int (設定しようとした目標SOC)
  - `errorMessage`: String? (失敗時のエラーメッセージ、成功時はnull)
  - `timestamp`: Long (実行完了時刻)

## 状態遷移

- `IDLE` -> `STARTING` (マクロ実行開始)
- `STARTING` -> `WAITING_FOR_HOME` (EP CUBEアプリ起動後)
- `WAITING_FOR_HOME` -> `WAITING_FOR_SETTINGS` (設定ボタンクリック後)
- `WAITING_FOR_SETTINGS` -> `ADJUSTING_SOC` (現在値読み取り後、目標値と異なる場合)
- `WAITING_FOR_SETTINGS` -> `SAVING` (現在値と目標値が一致している場合)
- `ADJUSTING_SOC` -> `SAVING` (値変更完了後)
- `SAVING` -> `WAITING_FOR_SUCCESS` (20秒待機後)
- `WAITING_FOR_SUCCESS` -> `COMPLETED` (設定値の再確認成功後)
- 任意のState -> `FAILED` (タイムアウト、要素未発見、予期せぬエラー発生時)

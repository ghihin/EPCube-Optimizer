# Phase 1: Data Model

## Entities

### 1. `DailySchedule`
対象日のカレンダー予定（出社/在宅）と予測される最適SOCを保持するデータモデル。

- **Fields**:
  - `date: LocalDate` (対象日)
  - `isCommute: Boolean` (true: 外出モード【出社】あり, false: 在宅モード)
  - `predictedSoc: Int` (算出された0〜100のSOC値)
- **Validation Rules**:
  - `predictedSoc` は0〜100の範囲内であること。
- **Relationships**:
  - UI (LazyColumn) に表示するために、リスト構造 `List<DailySchedule>` として管理される。

### 2. `AutomationExecutionLog`
毎晩行われる全自動処理（天候取得〜マクロ実行）の結果を記録し、Observability（監視性）を担保するモデル。

- **Fields**:
  - `id: String` (UUID等のユニークID)
  - `timestamp: LocalDateTime` (実行日時)
  - `status: ExecutionStatus` (Enum: `SUCCESS`, `VALIDATION_FAILED`, `MACRO_FAILED`, `CALENDAR_ERROR` 等)
  - `socTarget: Int?` (設定を試みたSOC値)
  - `errorDetail: String?` (エラー時の詳細なメッセージや例外スタックトレース)
- **State Transitions**:
  - `STARTED` -> `CALCULATED` -> `MACRO_RUNNING` -> `SUCCESS` / `FAILED`

## Validation Rules / Constraints

1. **カレンダーキーワード判定**:
   予定のタイトル欄を検索し、「【出社】」という文字列が含まれている場合のみ `isCommute = true` と判定する。大文字・小文字、全角・半角の揺れを吸収するかどうかは要件上必須ではないが、完全一致（含むか否か）を基本とする。
2. **SOCの安全値フォールバック**:
   天候データの取得エラー、カレンダーへのアクセスエラー等が発生した場合は、事前定義された安全値（例: 一律 50% もしくは在宅モード相当）を適用し、システムをクラッシュさせない（Safety Firstの原則）。

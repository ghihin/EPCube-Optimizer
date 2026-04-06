# データモデル: Googleカレンダーへの実行結果ロギング

**Branch**: `005-calendar-logging` | **Date**: 2026-04-06

---

## エンティティ定義

### 1. ExecutionCalendarEvent（新規）

カレンダーへINSERTするための中間データオブジェクト。ドメイン層の情報を集約してカレンダー書き込みに渡す。

```
ExecutionCalendarEvent
├── isSuccess: Boolean               // 実行成否
├── executionTimeMillis: Long        // 実行開始時刻（ミリ秒 epoch）
├── settingMode: String              // "グリーンモード" or "スマートモード"
├── targetSoc: Int?                  // 設定した目標SOC (null = グリーンモード/深夜充電なし)
├── errorMessage: String?            // 失敗時のエラー理由（success時はnull）
├── preExecSoc: Int?                 // マクロ実行直前に読み取った現在のSOC
├── preExecMode: String?             // マクロ実行直前に読み取った現在の運転モード
├── weatherDescription: String?      // 翌日の天気説明（例: "晴れ"）
├── cloudiness: Int?                 // 翌日の雲量（0-100%）
└── precipitationProbability: Float? // 翌日の降水確率（0.0-1.0）
```

**バリデーション規則**:
- `isSuccess = true` のとき: `settingMode` は非null・非空
- `isSuccess = false` のとき: `errorMessage` は非null・非空
- `targetSoc` が null の場合、カレンダー説明欄の目標SOC欄に「(深夜充電なし)」を表示する
- `preExecSoc` が null の場合、説明欄の対応フィールドを「取得不可」と表示する

---

### 2. CalendarLogWriter（新規クラス）

`ExecutionCalendarEvent` を受け取り、`CalendarContract` へ INSERT する責務を持つ。

**主要メソッド**:

```
CalendarLogWriter
├── suspend fun writeExecutionResult(event: ExecutionCalendarEvent): Boolean
│     ├── getDefaultCalendarId(): Long?   // SELECT でプライマリカレンダーID取得
│     ├── isDuplicateEvent(timeMillis: Long): Boolean  // SELECT で重複チェック
│     └── insert(calendarId, event)       // ContentResolver.insert()
└── private fun buildEventValues(calendarId: Long, event: ExecutionCalendarEvent): ContentValues
```

**説明欄フォーマット**（カレンダーイベントの `DESCRIPTION` フィールド）:

```
【実行結果】
設定モード: グリーンモード
目標SOC: (深夜充電なし)

【変更前の状態】
取得SOC: 65%
運転モード: スマートモード

【気象条件 (明日)】
天気: 晴れ
雲量: 15%
降水確率: 0%
```

失敗例:
```
【実行結果】
エラー: タイムアウト (状態: WAITING_FOR_HOME)

【変更前の状態】
取得SOC: 取得不可
運転モード: 取得不可

【気象条件 (明日)】
天気: 晴れ
雲量: 20%
降水確率: 10%
```

---

### 3. 既存 AutomationExecutionLog の拡張

既存 `AutomationExecutionLog` にカレンダーロギング用データの一時保持フィールドを追加する。

```diff
data class AutomationExecutionLog(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val status: ExecutionStatus,
    val socTarget: Int? = null,
-   val errorDetail: String? = null
+   val errorDetail: String? = null,
+   // カレンダーロギング用に追加（in-memoryのみ、DB永続化なし）
+   val preExecSoc: Int? = null,
+   val preExecMode: String? = null,
+   val weatherDescription: String? = null,
+   val cloudiness: Int? = null,
+   val precipitationProbability: Float? = null
)
```

---

### 4. EpCubeAccessibilityService returnMacroResult の拡張

マクロ完了時のインテントに、変更前SOCと変更前運転モードを EXTRA として追加する。

```diff
companion object {
    // 既存
    const val ACTION_MACRO_RESULT = "com.ghihin.epcubeoptimizer.ACTION_MACRO_RESULT"
    const val EXTRA_IS_SUCCESS = "EXTRA_IS_SUCCESS"
    const val EXTRA_ERROR_MESSAGE = "EXTRA_ERROR_MESSAGE"
    const val EXTRA_TARGET_SOC_RESULT = "EXTRA_TARGET_SOC_RESULT"
    // 追加
+   const val EXTRA_PRE_EXEC_SOC = "EXTRA_PRE_EXEC_SOC"        // Int (-1 = 未取得)
+   const val EXTRA_PRE_EXEC_MODE = "EXTRA_PRE_EXEC_MODE"      // String? (null = 未取得)
}
```

---

### 5. CalendarContract への書き込み項目マッピング

| `CalendarContract.Events` フィールド | 値 |
|-------------------------------------|-----|
| `CALENDAR_ID` | `getDefaultCalendarId()` で取得したID |
| `TITLE` | 成功: `"✅ EPCUBE設定完了"` / 失敗: `"❌ EPCUBE設定失敗"` |
| `DTSTART` | `executionTimeMillis` |
| `DTEND` | `executionTimeMillis + 15 * 60 * 1000` (15分後) |
| `EVENT_TIMEZONE` | `TimeZone.getDefault().ID` |
| `DESCRIPTION` | 上記フォーマットで生成した文字列 |
| `HAS_ALARM` | `0` (リマインダー不要) |

---

### 6. 重複チェックのSELECTクエリ仕様

```
SELECT COUNT(*) FROM CalendarContract.Events
WHERE TITLE LIKE '%EPCUBE設定%'
  AND DTSTART >= [実行時刻 - 30分]
  AND DTSTART <= [実行時刻 + 30分]
  AND CALENDAR_ID = [プライマリカレンダーID]
```

カウントが 1 以上の場合は INSERT をスキップする。

---

### 7. PermissionHelper の設計

```
object PermissionHelper {
    val CALENDAR_PERMISSIONS = arrayOf(
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR
    )
    fun hasCalendarPermissions(context: Context): Boolean
    fun areCalendarPermissionsGranted(results: Map<String, Boolean>): Boolean
}
```

`MainActivity` の `onCreate()` で `ActivityResultContracts.RequestMultiplePermissions()` を使いランタイム要求を実行。権限が拒否された場合はSnackbarで再誘導するが、アプリ本体の动作（SOC設定）は継続する。

---

## 状態遷移

```
AutomationOrchestrator.executeNightlyRoutine()
    ├─ [成功] → マクロ起動 → ACTION_MACRO_RESULT 受信
    │              ├─ EXTRA_IS_SUCCESS = true
    │              ├─ EXTRA_PRE_EXEC_SOC = n
    │              ├─ EXTRA_PRE_EXEC_MODE = "スマートモード"
    │              └─ CalendarLogWriter.writeExecutionResult(✅ イベント)
    │
    └─ [失敗] → catch(e: Exception)
                └─ CalendarLogWriter.writeExecutionResult(❌ イベント)
```

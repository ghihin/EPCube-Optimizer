# 実装計画書: Googleカレンダーへの自動実行結果ロギング

**Branch**: `005-calendar-logging` | **Date**: 2026-04-06 | **Spec**: [spec.md](spec.md)  
**Research**: [research.md](research.md) | **Data Model**: [data-model.md](data-model.md)

---

## Summary

深夜の自動実行（天候取得〜マクロ操作）が完了またはエラー終了した直後に、`CalendarContract` (ContentResolver) を用いてGoogleカレンダーへ実行結果をイベントとして書き込む。ユーザーは翌朝スマートフォンのカレンダーアプリを開くだけで「✅ EPCUBE設定完了」または「❌ EPCUBE設定失敗」の予定と判定根拠（気象条件・変更前SOC）を確認できる。

---

## Technical Context

**Language/Version**: Kotlin 1.9+  
**Primary Dependencies**: Android SDK CalendarContract, Jetpack Compose, Hilt, Coroutines  
**Storage**: N/A（カレンダーへのINSERT のみ。アプリDBへの永続化なし）  
**Testing**: JUnit 4 + MockK / Robolectric (既存パターンと同じ)  
**Target Platform**: Android (API 26+、専用余剰端末での24時間常駐)  
**Project Type**: Android モバイルアプリ  
**Performance Goals**: 実行完了から5秒以内にカレンダーへ書き込み完了（SC-001）  
**Constraints**: カレンダー書き込みの失敗がメインのSOC設定フロー（AccessibilityService）を中断しないこと（FR-006 / SC-003）  
**Scale/Scope**: 1ユーザー・1デバイス。1日1件のINSERT。

---

## Constitution Check

| 原則 | 評価 | 根拠 |
|------|------|------|
| **I. Safety First** | ✅ PASS | カレンダー書き込きがサイレントに失敗しても SOC 設定処理は継続する（try-catch で包み、FR-006 を遵守） |
| **II. Observability** | ✅ PASS | 本機能そのものが Observability の実現。実行根拠と結果をカレンダーに記録する |
| **III. Resilience** | ✅ PASS | 最大1回リトライ。失敗時はサイレント終了。重複チェック（FR-009）でべき等性を保証 |
| **IV. Pragmatism** | ✅ PASS | アプリ内に新しい画面やDBを追加しない。CalendarContract で端末カレンダーをそのまま活用するシンプルな実装 |

**Language Requirement**: ✅ 全ドキュメント・コードコメントを日本語で記述する

---

## Project Structure

### Documentation (this feature)

```text
specs/005-calendar-logging/
├── plan.md              ← 本ファイル
├── research.md          ← Phase 0 調査レポート
├── data-model.md        ← Phase 1 データモデル
├── checklists/
│   └── requirements.md
└── tasks.md             ← /speckit.tasks で作成予定
```

### Source Code 変更ファイル一覧

```text
app/src/main/
├── AndroidManifest.xml                             [MODIFY] WRITE_CALENDAR 追加
├── java/com/ghihin/epcubeoptimizer/
│   ├── calendar/
│   │   ├── CalendarRepository.kt                  [MODIFY] CalendarLogWriter を同パッケージに追加
│   │   └── CalendarLogWriter.kt                   [NEW]    書き込み・重複チェック・カレンダーID取得
│   ├── automation/
│   │   ├── AutomationExecutionLog.kt              [MODIFY] preExecSoc/Mode/気象フィールド追加
│   │   └── AutomationOrchestrator.kt              [MODIFY] マクロ結果受信 + カレンダー書き込み呼び出し
│   ├── core/
│   │   ├── accessibility/
│   │   │   └── EpCubeAccessibilityService.kt      [MODIFY] returnMacroResult に EXTRA 追加
│   │   └── permission/
│   │       └── PermissionHelper.kt                [NEW]    READ/WRITE_CALENDAR 権限ヘルパー
│   ├── presentation/main/
│   │   ├── MainActivity.kt                        [MODIFY] 起動時の権限要求フロー追加
│   │   └── MainScreen.kt                          [MODIFY] PermissionHelper 呼び出し追加
│   └── core/di/
│       └── AppModule.kt                           [MODIFY] CalendarLogWriter の Hilt 提供追加

app/src/test/java/com/ghihin/epcubeoptimizer/
└── calendar/
    └── CalendarLogWriterTest.kt                   [NEW]    書き込み・重複チェックのユニットテスト
```

---

## Proposed Changes（詳細）

### 1. [MODIFY] AndroidManifest.xml

`WRITE_CALENDAR` パーミッションを `READ_CALENDAR` の直下に追加する。

```xml
<!-- 既存 -->
<uses-permission android:name="android.permission.READ_CALENDAR" />
<!-- 追加 -->
<uses-permission android:name="android.permission.WRITE_CALENDAR" />
```

---

### 2. [NEW] CalendarLogWriter.kt

`calendar` パッケージに新規作成。Hilt `@Singleton` で提供する。

**主要責務**:
- `getDefaultCalendarId()`: `CalendarContract.Calendars.CONTENT_URI` をクエリし `IS_PRIMARY=1` または `VISIBLE=1` の最初のカレンダーIDを返す。
- `isDuplicateEvent(timeMillis)`: `DTSTART` が `timeMillis ± 30分` の範囲で `TITLE LIKE '%EPCUBE設定%'` なイベントが存在するか SELECT で確認する。
- `writeExecutionResult(event: ExecutionCalendarEvent)`: 上記2メソッドを利用してINSERT。失敗時は `Log.e` のみで例外を外に伝播しない。

---

### 3. [MODIFY] AutomationExecutionLog.kt

カレンダーロギング専用フィールドを5件追加する（DB永続化なし、インメモリのみ）。

```kotlin
data class AutomationExecutionLog(
    // ... 既存フィールド unchanged ...
    val preExecSoc: Int? = null,
    val preExecMode: String? = null,
    val weatherDescription: String? = null,
    val cloudiness: Int? = null,
    val precipitationProbability: Float? = null
)
```

---

### 4. [MODIFY] EpCubeAccessibilityService.kt

`returnMacroResult()` に `EXTRA_PRE_EXEC_SOC` (Int, -1=未取得) と `EXTRA_PRE_EXEC_MODE` (String?) を追加する。`executeMacroSteps()` で取得済みの `currentSoc` と `currentMode` をフィールドへ退避し、`returnMacroResult()` 呼び出し時に渡す。

```kotlin
companion object {
    // 追加
    const val EXTRA_PRE_EXEC_SOC = "EXTRA_PRE_EXEC_SOC"
    const val EXTRA_PRE_EXEC_MODE = "EXTRA_PRE_EXEC_MODE"
}
// returnMacroResult() シグネチャ変更
private fun returnMacroResult(isSuccess: Boolean, errorMessage: String?, preExecSoc: Int = -1, preExecMode: String? = null)
```

---

### 5. [MODIFY] AutomationOrchestrator.kt

`CalendarLogWriter` を DI でインジェクト。`executeNightlyRoutine()` を以下の順序で更新する:

1. `calculateTargetSocUseCase()` 呼び出し後に `WeatherForecast` を内部フィールドに保持する。
2. `BroadcastReceiver` を動的登録して `ACTION_MACRO_RESULT` を受信する。
3. 受信時に `ExecutionCalendarEvent` を組み立て `CalendarLogWriter.writeExecutionResult()` を呼び出す。
4. 既存の例外ハンドラ (`catch(e: Exception)`) でもカレンダーに失敗イベントを書き込む。

---

### 6. [NEW] PermissionHelper.kt

```kotlin
object PermissionHelper {
    val CALENDAR_PERMISSIONS = arrayOf(
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR
    )
    fun hasCalendarPermissions(context: Context): Boolean
}
```

---

### 7. [MODIFY] MainActivity.kt

`ActivityResultContracts.RequestMultiplePermissions()` を使い、`onCreate()` 内で `CALENDAR_PERMISSIONS` を一括要求する。拒否された場合は Snackbar で案内するがアプリは継続する。

---

### 8. [NEW] AppModule.kt

`CalendarLogWriter` の `@Provides` / `@Singleton` 定義を追加する。

---

## Verification Plan

### 自動テスト

#### 既存テストの確認

既存テストは `app/src/test/` 配下に4ファイル存在する。実行コマンド:

```bash
./gradlew test
```

現状のパスを確認してから実装後に再実行し、リグレッションがないことを確認する。

#### 新規ユニットテスト: CalendarLogWriterTest.kt

`CalendarLogWriter` の核心ロジックを MockK + Robolectric でテストする:

1. **重複チェックのテスト**: `isDuplicateEvent()` が既存イベントを検出した場合に `insert()` が呼ばれないことを検証。
2. **成功イベントの書き込みテスト**: `ExecutionCalendarEvent(isSuccess=true)` を渡したとき, `insert()` が呼ばれてタイトルに「✅」が含まれることを検証。
3. **失敗イベントの書き込みテスト**: `ExecutionCalendarEvent(isSuccess=false, errorMessage="...")` を渡したとき, タイトルに「❌」が含まれることを検証。
4. **説明欄グリーンモードのテスト**: `targetSoc=null` のとき説明欄に「(深夜充電なし)」が含まれることを検証。
5. **説明欄部分データのテスト**: `preExecSoc=null` のとき「取得不可」が含まれることを検証。

```bash
./gradlew testDebugUnitTest --tests "com.ghihin.epcubeoptimizer.calendar.CalendarLogWriterTest"
```

### 手動検証

デバイスを用いた手動テスト（ユーザーによる確認を依頼）:

1. **権限要求の確認**:
   - アプリを初回インストール（または権限を再度削除）後に起動する
   - 「カレンダーへのアクセス」ダイアログが表示されることを確認
   - 「許可」を選択後、次回以降は表示されないことを確認

2. **成功ケースの確認**（`NightlyAlarmReceiver` を手動でトリガー）:
   - ADB コマンド: `adb shell am broadcast -a com.ghihin.epcubeoptimizer.ACTION_EXECUTE_NIGHTLY -p com.ghihin.epcubeoptimizer`
   - マクロ完了後（約90秒以内）にGoogleカレンダーを開く
   - 当日の21:00付近に「✅ EPCUBE設定完了」の15分予定が作成されていることを確認
   - 詳細を開いて説明欄に設定モード・SOC・気象条件が記載されていることを確認

3. **重複登録のテスト**:
   - 上記手順を同日に2回実行する
   - Googleカレンダーにイベントが1件だけ存在することを確認

4. **権限拒否ケースの確認**:
   - 端末設定からカレンダー権限を「拒否」にする
   - `AutomationOrchestrator` を実行する
   - EPCubeの本体設定は正常に完了し（SOCが設定される）、カレンダーイベントは作成されないことを確認

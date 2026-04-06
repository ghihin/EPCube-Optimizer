# 調査レポート: Googleカレンダーへの実行結果ロギング

**Branch**: `005-calendar-logging` | **Date**: 2026-04-06

---

## Phase 0: технических制約と設計判断の解決

### 1. CalendarContract による書き込みの実装方法

**Decision**: `ContentResolver.insert(CalendarContract.Events.CONTENT_URI, values)` を使用する。

**Rationale**:
- `CalendarContract` は Android SDK 標準 API であり、外部依存ゼロで実装できる。
- 既存の `CalendarRepository` がすでに `CalendarContract.Events.CONTENT_URI` に対して `contentResolver.query()` でREAD操作を行っており、同じパターンで WRITE が可能。
- `CalendarContract.Events.CALENDAR_ID` にはデバイスのデフォルトカレンダーIDを SELECTして指定する。

**Alternatives considered**:
- **Google Calendar API (REST)**: OAuth2認証フローが必要でアプリの複雑度が増す。CalendarContract の方が端末内完結でシンプル。
- **Google Calendar SDK (google-api-client)**: 同様にOAuth2が必要で over-kill。

---

### 2. 重複登録防止（べき等性）の実装方法

**Decision**: INSERT 前に SELECT を実行し、当日の `AUTO_EXECUTION_HOUR`（21:00）±30分の範囲にタイトルが `EPCUBE設定` を含むイベントが存在する場合は INSERT をスキップする。

**Rationale**:
- `CalendarContract` はユニークIDによる upsert をサポートしていないため、SELECT-before-INSERT パターンが最も安全で標準的。
- 既存 `CalendarRepository.getEventsForNextNDays()` の SELECT ロジックを参考に、時間範囲のフィルタリングは `DTSTART >= ? AND DTSTART <= ?` で実現できる。
- 時間範囲を ±30分に設定することで「同日21:00付近のイベント」を一意に識別できる。

**Alternatives considered**:
- **カスタムイベントID (`_sync_id`)**: 標準の `CalendarContract` では端末ローカルの書き込みにはサポートされない。
- **`dtstart` の一致チェック**: ±0分（完全一致）だと秒単位のズレで重複検出を見逃すリスクがある。

---

### 3. WRITE_CALENDAR ランタイム権限の実装方法

**Decision**: `MainActivity` の `onCreate()` で `ActivityCompat.requestPermissions()` を利用する。権限チェックロジックを `PermissionHelper.kt` に集約する。

**Rationale**:
- `WRITE_CALENDAR` は Android の Dangerous Permission に分類されるため、マニフェスト宣言に加えてランタイム要求が必須。
- `READ_CALENDAR` は既存でランタイム要求が**未実装**であることを確認した（`CalendarRepository.getEventsForNextNDays()` は try-catch でエラーを握りつぶしている）。
- 今回 `WRITE_CALENDAR` 要求に合わせて `READ_CALENDAR` も同時に要求することで UI が一度で完結する。
- `ActivityResultContracts.RequestMultiplePermissions()` を使えば Compose との相性が良く実装がシンプル。

**Alternatives considered**:
- **WakeActivity でのランタイム要求**: 深夜実行時の WakeActivity で要求するとユーザーが気づかない可能性があるため不適切。

---

### 4. イベント書き込み処理の挿入ポイント

**Decision**: カレンダーへの書き込みは `EpCubeAccessibilityService.returnMacroResult()` の**呼び出し前**に **`AutomationOrchestrator` で実行**する。

**Rationale**:
- `EpCubeAccessibilityService` は UI 自動操作専用クラスであり、カレンダー書き込みという副作用を持たせることは単一責任原則に反する。
- `AutomationOrchestrator` は天候取得・SOC計算・マクロ起動を束ねる司令塔であり、気象条件 (`WeatherContext`) も保持しているため、ここでロギングに必要な全データを集約できる。
- ただし、マクロ完了はインテント経由で `MainActivity` に通知される現在の構造では、`AutomationOrchestrator` は `EpCubeAccessibilityService` のマクロ完了を非同期で受け取れない。
- **解決策**: `AutomationOrchestrator` に `BroadcastReceiver` を動的登録して `ACTION_MACRO_RESULT` を受信し、そこでカレンダー書き込みを実行する。

---

### 5. デフォルトカレンダーIDの取得方法

**Decision**: `CalendarContract.Calendars.CONTENT_URI` を SELECT し、`IS_PRIMARY = 1` または `VISIBLE = 1` で最初のカレンダーIDを取得する。

**Rationale**:
- ハードコードは端末再セットアップの際に壊れるリスクがある。
- `IS_PRIMARY` が利用できない古いAndroidでは `VISIBLE AND SYNC_EVENTS` を使った fallback で対応できる。

---

### 6. AutomationOrchestrator へのデータ受け渡し設計

**Decision**: マクロ結果を受け取った後、カレンダー書き込みに必要な以下のデータを `AutomationOrchestrator` のフィールドとして保持する。

| データ | 保持タイミング | 取得元 |
|--------|--------------|-------|
| 目標SOC | `calculateTargetSocUseCase()` 呼び出し後 | `targetSocResult.targetSoc` |
| グリーンモードか否か | 同上 | `targetSocResult.isSunnyTomorrow && currentSoc >= 60` |
| 気象条件（天気・雲量・降水確率） | `calculateTargetSocUseCase()` 内で取得 | `WeatherForecast` |
| 変更前SOC・運転モード | インテント `ACTION_MACRO_RESULT` の EXTRA | `EpCubeAccessibilityService` が返す値 |

現状、`EpCubeAccessibilityService.returnMacroResult()` は `EXTRA_TARGET_SOC_RESULT` と `EXTRA_IS_SUCCESS` しか返していない。変更前のSOCと運転モードも `EXTRA` として追加で返す改修が必要。

---

### 7. 既存テストの範囲確認

以下のユニットテストが存在する（`app/src/test/`）:
- `ScheduleAnalyzerTest.kt` — カレンダーイベント解析ロジックのみ
- `ScheduleRepositoryImplTest.kt` — スケジュール取得のリポジトリテスト
- `WeatherRepositoryImplTest.kt` — 天気予報取得のリポジトリテスト
- `CalculateTargetSocUseCaseTest.kt` — SOC計算ユースケースのテスト

**Result**: カレンダー書き込み・ランタイム権限要求・`AutomationOrchestrator` の統合テストは存在しない。新規ユニットテストを `CalendarLogWriterTest.kt` として追加する。

---

## 結論

本機能の実装は以下の 4 コンポーネントに対する変更で完結する：

1. **[MODIFY] `AndroidManifest.xml`**: `WRITE_CALENDAR` 権限追加
2. **[NEW] `calendar/CalendarLogWriter.kt`**: INSERT ロジック・SELECT重複チェック・デフォルトカレンダーID取得を担当する専用クラス
3. **[MODIFY] `calendar/CalendarRepository.kt`**: `CalendarLogWriter` を Hilt で提供できるよう `AppModule` と連携
4. **[MODIFY] `automation/AutomationOrchestrator.kt`**: マクロ結果受信リスナーを追加し、`CalendarLogWriter` を呼び出す
5. **[MODIFY] `core/accessibility/EpCubeAccessibilityService.kt`**: `returnMacroResult()` に現在SOC・運転モードの EXTRA を追加
6. **[NEW] `core/permission/PermissionHelper.kt`**: `READ_CALENDAR` / `WRITE_CALENDAR` ランタイム権限要求ヘルパー
7. **[MODIFY] `presentation/main/MainScreen.kt` & `MainActivity.kt`**: アプリ起動時の権限要求フロー追加
8. **[NEW] `app/src/test/.../calendar/CalendarLogWriterTest.kt`**: 書き込みロジック・重複チェックのユニットテスト

# Tasks: Googleカレンダーへの自動実行結果ロギング

**Input**: `/specs/005-calendar-logging/` (spec.md, plan.md, research.md, data-model.md)  
**Branch**: `005-calendar-logging`  
**Created**: 2026-04-06

**Organization**: ユーザーストーリー単位でフェーズを構成し、各ストーリーが独立してテスト・実装可能。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 並列実行可能（異なるファイル・相互依存なし）
- **[Story]**: 属するユーザーストーリー（US1 / US2 / US3）
- 各タスクには実装対象ファイルの絶対パスを記載

---

## Phase 1: セットアップ（共有インフラ）

**Purpose**: 権限定義とモデル拡張。全フェーズのブロッカー。

**⚠️ CRITICAL**: Phase 1 が完了するまで、いかなるユーザーストーリー実装も開始できない。

- [x] T001 `app/src/main/AndroidManifest.xml` に `<uses-permission android:name="android.permission.WRITE_CALENDAR" />` を `READ_CALENDAR` の直下に追加する
- [x] T002 [P] `app/src/main/java/com/ghihin/epcubeoptimizer/automation/AutomationExecutionLog.kt` の `AutomationExecutionLog` データクラスにカレンダーロギング用フィールド（`preExecSoc: Int? = null`, `preExecMode: String? = null`, `weatherDescription: String? = null`, `cloudiness: Int? = null`, `precipitationProbability: Float? = null`）を追加する
- [x] T003 [P] `app/src/main/java/com/ghihin/epcubeoptimizer/core/accessibility/EpCubeAccessibilityService.kt` の `companion object` に `EXTRA_PRE_EXEC_SOC: String` と `EXTRA_PRE_EXEC_MODE: String` の定数を追加し、`executeMacroSteps()` 内で取得した `currentSoc` と `currentMode` をフィールドとして退避し、`returnMacroResult()` のシグネチャを `(isSuccess: Boolean, errorMessage: String?, preExecSoc: Int = -1, preExecMode: String? = null)` に変更して呼び出し箇所をすべて更新する

**Checkpoint**: AndroidManifest・モデル・サービスの基礎変更が完了。次フェーズへ進める。

---

## Phase 2: 基盤（全ストーリー共通コンポーネント）

**Purpose**: カレンダー書き込みエンジン・権限ヘルパーの実装。全USの前提。

**⚠️ CRITICAL**: Phase 2 完了前に Phase 3 以降を開始しないこと。

- [x] T004 [P] `app/src/main/java/com/ghihin/epcubeoptimizer/calendar/CalendarLogWriter.kt` を新規作成する。Hilt `@Singleton` で提供。以下のメソッドを実装する:
  - `private suspend fun getDefaultCalendarId(): Long?` — `CalendarContract.Calendars.CONTENT_URI` に対し `IS_PRIMARY = 1` または `VISIBLE = 1` で SELECT し最初のカレンダーIDを返す。取得不可の場合は `null`
  - `private suspend fun isDuplicateEvent(timeMillis: Long): Boolean` — `DTSTART >= timeMillis - 30分` かつ `DTSTART <= timeMillis + 30分` かつ `TITLE LIKE '%EPCUBE設定%'` で SELECT し 1件以上存在すれば `true`
  - `suspend fun writeExecutionResult(event: ExecutionCalendarEvent): Boolean` — `isDuplicateEvent()` が `true` の場合は INSERT をスキップして `false` を返す。`getDefaultCalendarId()` が `null` の場合も同様。INSERT 失敗時は `Log.e` のみで例外を外に伝播しない

- [x] T005 [P] `app/src/main/java/com/ghihin/epcubeoptimizer/calendar/CalendarLogWriter.kt` 内の `buildDescription()` メソッドを実装する。`ExecutionCalendarEvent` を受け取り以下のフォーマットで説明欄文字列を生成する（400文字以内を遵守）:
  ```
  【実行結果】
  設定モード: {settingMode}
  目標SOC: {targetSoc}% | (深夜充電なし)  ← targetSoc が null の場合は後者
  ※失敗時: エラー: {errorMessage}

  【変更前の状態】
  取得SOC: {preExecSoc}% | 取得不可  ← null の場合は後者
  運転モード: {preExecMode} | 取得不可

  【気象条件 (明日)】
  天気: {weatherDescription} | 取得不可
  雲量: {cloudiness}% | 取得不可
  降水確率: {precipitationProbability*100}% | 取得不可
  ```
  `buildEventValues()` で `CalendarContract.Events.DESCRIPTION` にセットする

- [x] T006 [P] `app/src/main/java/com/ghihin/epcubeoptimizer/core/permission/PermissionHelper.kt` を新規作成する。以下を実装する:
  - `val CALENDAR_PERMISSIONS = arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)`
  - `fun hasCalendarPermissions(context: Context): Boolean` — `ContextCompat.checkSelfPermission()` で両権限をチェック

- [x] T007 `app/src/main/java/com/ghihin/epcubeoptimizer/core/di/AppModule.kt` に `CalendarLogWriter` の `@Provides @Singleton` バインドを追加する

**Checkpoint**: `CalendarLogWriter` 単独で動作確認可能（単体テスト可能な状態）。

---

## Phase 3: User Story 1 - 成功時の実行結果をカレンダーで確認する (Priority: P1) 🎯 MVP

**Goal**: 自動実行が成功した後、Googleカレンダーに「✅ EPCUBE設定完了」の15分予定が作成され、説明欄に設定モード・SOC・気象条件・変更前状態が記載される。

**Independent Test**: `ACTION_EXECUTE_NIGHTLY` ブロードキャストを手動送信し、マクロ成功後にGoogleカレンダーを確認して「✅ EPCUBE設定完了」予定が1件のみ存在することを確認。

### 実装: User Story 1

- [x] T008 [US1] `app/src/main/java/com/ghihin/epcubeoptimizer/automation/AutomationOrchestrator.kt` を修正する。`CalendarLogWriter` を `@Inject constructor` に追加し、`executeNightlyRoutine()` 内で以下の処理を実装する:
  1. `calculateTargetSocUseCase()` の結果（`targetSocResult`）から `WeatherForecast`（天気・雲量・降水確率）を内部フィールドに退避する
  2. `context.registerReceiver()` で `ACTION_MACRO_RESULT` を受信する `BroadcastReceiver` を動的登録する

- [x] T009 [US1] `AutomationOrchestrator.kt` に **BroadcastReceiver の結果受信処理**を実装する。インテントから `EXTRA_IS_SUCCESS`, `EXTRA_TARGET_SOC_RESULT`, `EXTRA_ERROR_MESSAGE`, `EXTRA_PRE_EXEC_SOC`, `EXTRA_PRE_EXEC_MODE` を取り出し、`ExecutionCalendarEvent` を組み立て `CalendarLogWriter.writeExecutionResult()` を呼び出す

- [x] T010 [US1] `AutomationOrchestrator.kt` に **BroadcastReceiver のメモリリーク防止処理**を実装する（ユーザー必須要件①）。受信後の `onReceive()` の末尾、および既存の `catch(e: Exception)` ブロック内、さらに `finally` ブロック内で **必ず** `context.unregisterReceiver(receiver)` を呼び出す。`isReceiverRegistered` フラグを設け二重解除を防ぐ

- [x] T011 [US1] `AutomationOrchestrator.kt` に **マクロタイムアウト（ハングアップ）対策**を実装する（ユーザー必須要件②）。`BroadcastReceiver` 登録後の待機処理を `withTimeoutOrNull(180_000L) { ... }` (3分) でラップし、タイムアウト時（`null` 返却時）は `context.unregisterReceiver()` を呼び出したうえで `ExecutionCalendarEvent(isSuccess = false, errorMessage = "マクロ応答タイムアウト (3分)")` を組み立て `CalendarLogWriter.writeExecutionResult()` を呼び出す

- [x] T012 [US1] `AutomationOrchestrator.kt` の `catch(e: Exception)` ブロック（マクロ起動前の例外）にもカレンダーへ「❌ EPCUBE設定失敗」イベントを書き込む処理を追加する。この時点では `preExecSoc / preExecMode` は未取得のため `null` として渡す

**Checkpoint**: 成功ケースで「✅ EPCUBE設定完了」がカレンダーに登録される。US1 独立検証可能。

---

## Phase 4: User Story 2 - 失敗時のエラー内容をカレンダーで確認する (Priority: P2)

**Goal**: 自動実行がエラー（ネットワーク障害・マクロタイムアウト等）で終了した場合でも「❌ EPCUBE設定失敗」の予定がカレンダーに記録され、説明欄にエラー理由が記載される。

**Independent Test**: ネットワークを遮断した状態で `ACTION_EXECUTE_NIGHTLY` を手動送信し、Googleカレンダーに「❌ EPCUBE設定失敗」の予定が1件作成されることを確認。

### 実装: User Story 2

- [ ] T013 [US2] `app/src/main/java/com/ghihin/epcubeoptimizer/automation/AutomationOrchestrator.kt` の失敗パス（`catch(e: Exception)` 内のフォールバック処理）を見直す。マクロ起動コードの前で例外が発生した場合（天気取得失敗等）はカレンダーに「❌」イベントを書き込んで正常終了する（T012 の検証・補強）

- [ ] T014 [US2] T011 で実装したタイムアウト時のカレンダー書き込み処理について、エラー理由が「マクロ応答タイムアウト (3分)」として説明欄に正しく表示されることをログで確認できるよう `Log.w(Config.LOG_TAG, ...)` を追加する

- [ ] T015 [US2] `app/src/main/java/com/ghihin/epcubeoptimizer/calendar/CalendarLogWriter.kt` に `WRITE_CALENDAR` 権限が未付与の場合のガード処理を追加する。`ContextCompat.checkSelfPermission()` で権限確認し、未付与なら `Log.w` を出力して `false` を返す（例外は投げない）

**Checkpoint**: 各種失敗シナリオで「❌ EPCUBE設定失敗」がカレンダーに登録される。US2 独立検証可能。

---

## Phase 5: User Story 3 - 過去の実行履歴を時系列で振り返る (Priority: P3)

**Goal**: 毎日の自動実行ごとに1件ずつイベントが記録され、Googleカレンダーの週表示・月表示で時系列履歴として確認できる。

**Independent Test**: 7日間のアラームを手動でトリガーし、カレンダーの週表示に7件のEPCUBEイベントが時系列で存在することを確認。重複チェック（T004）により同日に2件以上登録されないことを確認。

### 実装: User Story 3（重複防止の確実化）

- [ ] T016 [US3] `app/src/main/java/com/ghihin/epcubeoptimizer/calendar/CalendarLogWriter.kt` の `isDuplicateEvent()` メソッドをデバッグログ付きで動作確認する。重複が検出された場合は `Log.i(Config.LOG_TAG, "重複イベント検出: INSERT をスキップします")` を出力し、ADBログで確認できるようにする

- [ ] T017 [US3] `AutomationOrchestrator.kt` の受信処理内で、カレンダー書き込み結果（`writeExecutionResult()` の戻り値）をログに出力する: 成功 → `Log.i`、重複スキップ → `Log.i`、失敗 → `Log.e`

**Checkpoint**: 重複登録の防止が確認可能。週表示での履歴確認が可能。全 US 独立検証可能。

---

## Phase 6: 権限フロー（ランタイム権限要求）

**Purpose**: FR-008 — アプリ起動時の WRITE_CALENDAR 権限要求フロー。

- [ ] T018 `app/src/main/java/com/ghihin/epcubeoptimizer/MainActivity.kt` を修正する。`ActivityResultContracts.RequestMultiplePermissions()` を使い `PermissionHelper.CALENDAR_PERMISSIONS` を一括要求するランチャーを定義し、`onCreate()` 内で `PermissionHelper.hasCalendarPermissions(this)` が `false` の場合に起動する

- [ ] T019 `app/src/main/java/com/ghihin/epcubeoptimizer/presentation/main/MainScreen.kt` を修正する。権限が拒否された場合はSnackbarで「カレンダー権限が必要です。設定から許可してください。」と表示する。ただし権限なしでもアプリ本体（SOC設定）は継続動作する

---

## Phase 7: ユニットテスト

**Purpose**: `CalendarLogWriter` の核心ロジックを自動テストで保護する。

- [ ] T020 [P] `app/src/test/java/com/ghihin/epcubeoptimizer/calendar/CalendarLogWriterTest.kt` を新規作成する。MockK + Robolectric を使い以下の5ケースをテストする:
  1. **重複チェック検出時**: `isDuplicateEvent()` が `true` を返すとき `insert()` が呼ばれないこと
  2. **成功イベント書き込み**: `isSuccess=true` のとき `insert()` の引数に `TITLE = "✅ EPCUBE設定完了"` が含まれること
  3. **失敗イベント書き込み**: `isSuccess=false` のとき `TITLE = "❌ EPCUBE設定失敗"` が含まれること
  4. **グリーンモード説明欄**: `targetSoc=null` のとき `DESCRIPTION` に `(深夜充電なし)` が含まれること
  5. **部分データ説明欄**: `preExecSoc=null` のとき `DESCRIPTION` に `取得不可` が含まれること

---

## Phase 8: ポリッシュ・仕上げ

**Purpose**: コードレビュー観点の最終確認と未対応の横断関心事。

- [ ] T021 [P] 全変更ファイルのコードコメント（KDoc）を日本語で記述していることを確認する（`GEMINI.md` の言語要件を遵守）
- [ ] T022 [P] `CalendarLogWriter.kt` の説明欄文字列長が400文字を超えないことを単体で確認する（SC-004）
- [ ] T023 `plan.md` の Verification Plan に記載されたADBコマンドによる手動検証を実施し、成功・失敗・重複・権限拒否の4シナリオを確認する

---

## Dependencies & Execution Order

### フェーズ依存関係

```
Phase 1 (セットアップ)
    ↓ ブロッカー
Phase 2 (基盤: CalendarLogWriter / PermissionHelper / AppModule)
    ↓ ブロッカー
Phase 3 (US1: 成功ケース) ─── MVP ここまでで動作検証可能
    ↓
Phase 4 (US2: 失敗ケース) ─── Phase 3 との依存なし（並列可）
    ↓
Phase 5 (US3: 重複防止・履歴) ─── Phase 3/4 との依存なし（並列可）
    ↓
Phase 6 (権限フロー) ── Phase 2 完了後なら並列可
    ↓
Phase 7 (ユニットテスト) ── Phase 2 完了後なら並列可
    ↓
Phase 8 (ポリッシュ)
```

### タスク内依存関係（重要）

- **T008 → T009 → T010 → T011**: `AutomationOrchestrator` の変更は順番に実装する（BroadcastReceiver 登録 → 結果処理 → リーク防止 → タイムアウト）
- **T004 → T005**: `CalendarLogWriter` の書き込みメソッドより先に `buildDescription()` を実装する
- **T004, T005, T006 → T007**: `AppModule` への追加は各クラス実装後に行う
- **T006 → T018**: `PermissionHelper` 実装後に `MainActivity` のフロー追加を行う
- **T004 → T020**: テストは `CalendarLogWriter` 実装後に作成する

### 並列実行可能なタスク

| グループ | タスク | 備考 |
|--------|-------|-----|
| Phase 1 並列組 | T002 / T003 | T001 完了後に並列実行可 |
| Phase 2 並列組 | T004 / T005 / T006 | 独立したファイル |
| Phase 3-5 並列組 | T013 / T016 / T018 / T019 / T020 | Phase 2 完了後は並列可 |
| Phase 8 並列組 | T021 / T022 | 独立したチェック作業 |

---

## Implementation Strategy

### MVP First (User Story 1 のみ)

1. Phase 1 完了 (T001〜T003)
2. Phase 2 完了 (T004〜T007) ← **CalendarLogWriter が動く状態**
3. Phase 3 完了 (T008〜T012) ← **成功ケースがカレンダーに記録される**
4. **STOP & VALIDATE**: ADB で `ACTION_EXECUTE_NIGHTLY` を手動トリガーし、カレンダーに「✅ EPCUBE設定完了」が1件作成されることを確認
5. 承認後、Phase 4〜8 を段階的に追加

### Full Delivery（全ストーリー）

```
Phase 1 → Phase 2 → Phase 3 (MVP検証) → Phase 4 → Phase 5 → Phase 6 → Phase 7 → Phase 8
```

---

## Notes

- **[P] タスク** = 異なるファイルへの変更で相互依存なし。並列実行可能。
- **T010 (BroadcastReceiver 解除)** と **T011 (タイムアウト)** はユーザー必須要件のため絶対にスキップ不可。
- コミットは各フェーズ完了後に行う（例: `git commit -m "feat(005/phase3): 成功ケースのカレンダーロギング実装"`）。
- `EpCubeAccessibilityService.kt` は AccessibilityService の制約上、ユニットテストが困難。手動ADB検証で確認する。

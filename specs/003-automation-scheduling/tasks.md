# Tasks: Automation & Scheduling (Ver. 2.0)

**Input**: Design documents from `specs/003-automation-scheduling/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, quickstart.md

**Organization**: 各ユーザーストーリー（US）が独立して実装およびテストできるようにタスクを分割しています。

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: プロジェクトの初期設定、ベースライン権限の追加など

- [x] T001 `AndroidManifest.xml` に `READ_CALENDAR`, `WAKE_LOCK`, `USE_EXACT_ALARM`, `SYSTEM_ALERT_WINDOW` の権限を追加する
- [x] T002 プロジェクト共通の定数・設定値管理クラスを `src/main/java/com/example/epcube/automation/Config.kt` に作成する

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: すべてのユーザーストーリーの前提となるデータモデル定義

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [x] T003 [P] `DailySchedule` エンティティと検証ルールを `src/main/java/com/example/epcube/calendar/DailySchedule.kt` に実装する
- [x] T004 [P] 自動実行ログを記録する `AutomationExecutionLog` モデルを `src/main/java/com/example/epcube/automation/AutomationExecutionLog.kt` に実装する

**Checkpoint**: Foundation ready - 各ユーザーストーリーの並行開発が可能

---

## Phase 3: User Story 2 - Googleカレンダーからの出社判定とシミュレーション (Priority: P1) 🎯 MVP part 1

**Goal**: Googleカレンダーのローカルプロバイダへアクセスし、「【出社】」キーワードに基づいて外出モード/在宅モードを正確に判定する。

**Independent Test**: `DailySchedule` リストを取得する関数を単体テストまたはログ出力により、正しく出社/在宅が判定されるか検証可能。

### Implementation for User Story 2

- [x] T005 [P] [US2] `CalendarContract` を用いて指定期間の予定をフェッチする `CalendarRepository` を `src/main/java/com/example/epcube/calendar/CalendarRepository.kt` に実装する
- [x] T006 [US2] 予定タイトルから「【出社】」キーワードを抽出し、`DailySchedule` リストを生成する `ScheduleAnalyzer` を `src/main/java/com/example/epcube/calendar/ScheduleAnalyzer.kt` に実装する
- [x] T007 [P] [US2] `ScheduleAnalyzer` の出社/在宅判定に関する単体テストを `tests/unit/java/com/example/epcube/calendar/ScheduleAnalyzerTest.kt` に追加する

**Checkpoint**: カレンダー判定ロジックが独立して正常動作する

---

## Phase 4: User Story 1 - 深夜の全自動SOC最適化と設定反映 (Priority: P1) 🎯 MVP part 2

**Goal**: スリープ状態から端末を確実に復帰させ、天候取得→SOC計算→マクロ実行（20秒再確認）を全自動で完了させる。

**Independent Test**: 指定時刻のアラーム発火時に画面が点灯し、マクロが正しくUIを操作して値を設定・確認して完了できるかを手動ですぐに確認可能。

### Implementation for User Story 1

- [x] T008 [P] [US1] 毎晩の指定時刻（23:30）にアラームをセットする `AlarmScheduler` を `src/main/java/com/example/epcube/automation/AlarmScheduler.kt` に実装する
- [x] T009 [P] [US1] スリープ復帰とロック画面解除（WakeLock・Keyguard等）を担当する透明Activity `WakeActivity` を `src/main/java/com/example/epcube/automation/WakeActivity.kt` に作成する
- [x] T010 [US1] AlarmManagerからのインテントを受け取り、処理を開始する `NightlyAlarmReceiver` を `src/main/java/com/example/epcube/automation/NightlyAlarmReceiver.kt` に実装する
- [x] T011 [US1] マクロ実行全体のオーケストレーション（スケジュール取得→計算→設定）を行う `AutomationOrchestrator` を `src/main/java/com/example/epcube/automation/AutomationOrchestrator.kt` に実装する
- [x] T012 [US1] 既存の `EpCubeAccessibilityService` を拡張し、自動化トリガーからの要求を受け付けてSOC設定と「20秒待機後の値再確認」を実行する処理を組み込む

**Checkpoint**: User Story 1 & 2 結合テスト可能。毎晩の完全自動化が機能する状態。

---

## Phase 5: User Story 3 - 1週間先のスケジュール・予測SOCビューアー (Priority: P2)

**Goal**: 本日から1週間先までのスケジュールと予測SOCをアプリのメイン画面で一覧表示する。

**Independent Test**: Composeプレビューまたは実機起動時に、カレンダーから取得された1週間分のデータが正しくリスト表示されるかで確認可能。

### Implementation for User Story 3

- [x] T013 [P] [US3] UI状態を管理し `CalendarRepository` からデータを取得・提供する `ScheduleViewModel` を `src/main/java/com/example/epcube/ui/schedule/ScheduleViewModel.kt` に実装する
- [x] T014 [US3] LazyColumnを利用して `DailySchedule` リストを描画する Compose UIコンポーネント `ScheduleViewerScreen` を `src/main/java/com/example/epcube/ui/schedule/ScheduleViewerScreen.kt` に実装する
- [x] T015 [US3] アプリのメイン画面（`MainActivity`）に `ScheduleViewerScreen` を統合しナビゲーションを構成する
- [x] T016 手動実行用ボタン（デバッグ・テスト用）を `MainActivity` に追加する
- [x] T017 エラー時（天候取得失敗等）の安全なSOCフォールバック（Safety First）ロジックが統合されているか全体レビュー・修正を行う
- [x] T018 `quickstart.md` の動作確認手順に従い、結合テストを実施する
- [x] T019 [Bug] HiltとBroadcastReceiverの組み合わせによる `transformDebugClassesWithAsm` ビルドエラーを解消し、ユニットテストが正常に実行できるようにする

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 即時着手可能
- **Foundational (Phase 2)**: Phase 1完了後。各UI・ロジックに影響するため最優先
- **User Stories (Phase 3+)**: Setup & Foundational 完了後
- **Polish (Final Phase)**: 全ストーリー実装後

### User Story Dependencies

- **US2 (P1)**: 他ストーリーに依存しない。US1にカレンダー判定結果を提供する。
- **US1 (P1)**: US2の判定データ (`DailySchedule`) を必要とするため、US2の実装後またはモックデータを利用して並行実装可能。
- **US3 (P2)**: UI表示専用であり、US2が提供するリポジトリデータを利用する独立機能。

### Parallel Opportunities

- T003, T004: 互いに依存しないデータモデルのため同時着手可能
- T005, T008, T009: カレンダーアクセス、アラーム登録、透明Activity作成などは機能ドメインが異なるため完全並行着手可能
- UI実装 (Phase 5) と マクロ連動 (Phase 4) は別開発者で並行実装可能

---

## Implementation Strategy

### MVP First (US1 & US2 combined)

1. Phase 1 & 2 を完了させる
2. Phase 3 (US2: カレンダー連携) を実装・テストする
3. Phase 4 (US1: スケジュール実行とUI自動操作) を実装・テストする
4. **STOP and VALIDATE**: ディスプレイ消灯状態からの全自動マクロ実行を確実にテストしMVPとして確定する。

### Incremental Delivery

1. Foundation 完了後、US2を先行実装し、ログ出力のみで「判定ロジック」の妥当性を運用テスト・確認する。
2. 次に、US1による自動マクロ発火機構を結合し、実運用を開始する。
3. 最後にUS3のビューアーUIを追加し、エンドユーザーへの可視化を提供する。

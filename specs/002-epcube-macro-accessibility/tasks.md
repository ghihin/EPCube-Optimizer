---
description: "EP CUBE マクロアクセシビリティ実装のためのタスクリスト"
---

# タスク: EP CUBE マクロアクセシビリティ

**入力**: `/specs/002-epcube-macro-accessibility/` からの設計ドキュメント
**前提条件**: plan.md, spec.md, research.md, data-model.md, contracts/intent-contract.md

**構成**: 各ストーリーの独立した実装とテストを可能にするため、タスクはユーザーストーリーごとにグループ化されています。

## Phase 1: Setup (共有インフラストラクチャ)

**目的**: プロジェクトの初期化と基本構造

- [x] T001 `app/src/main/java/com/ghihin/epcubeoptimizer/core/accessibility/` に `core/accessibility` パッケージ構造を作成する
- [x] T002 [P] `com.eternalplanetenergy.epcube.jp` 用のAccessibilityServiceを設定するため、`app/src/main/res/xml/` に `accessibility_service_config.xml` を作成する

---

## Phase 2: Foundational (ブロックする前提条件)

**目的**: ユーザーストーリーを実装する前に完了しなければならないコアインフラストラクチャ

- [x] T003 data-model.md に基づき、`app/src/main/java/com/ghihin/epcubeoptimizer/core/accessibility/MacroState.kt` に `MacroState` enumクラスを作成する
- [x] T004 data-model.md に基づき、`app/src/main/java/com/ghihin/epcubeoptimizer/core/accessibility/MacroResult.kt` に `MacroResult` データクラスを作成する
- [x] T005 `app/src/main/java/com/ghihin/epcubeoptimizer/core/accessibility/EpCubeAccessibilityService.kt` に `AccessibilityService` を継承した `EpCubeAccessibilityService` クラスを作成する
- [x] T006 `app/src/main/AndroidManifest.xml` に `BIND_ACCESSIBILITY_SERVICE` 権限とメタデータを持つ `EpCubeAccessibilityService` を登録する

**チェックポイント**: 基盤の準備完了 - ユーザーストーリーの実装を開始できます

---

## Phase 3: User Story 1 - EP CUBEアプリの自動操作による目標SOC設定 (Priority: P1) 🎯 MVP

**目標**: ユーザーがEPCubeOptimizerアプリで算出した目標SOCを、EP CUBE公式アプリに自動的に反映させる。

**独立したテスト**: EPCubeOptimizerからインテントを発行し、EP CUBEアプリが起動して一連のUI操作が自動で行われ、最終的にEPCubeOptimizerに戻ってくることを確認する。

### User Story 1 の実装

- [x] T007 [US1] `EpCubeAccessibilityService.onStartCommand` で `EXTRA_TARGET_SOC` を受け取り、マクロステートマシンを開始するIntent処理を実装する。
- [x] T008 [US1] `PackageManager.getLaunchIntentForPackage("com.eternalplanetenergy.epcube.jp")` と `Intent.FLAG_ACTIVITY_NEW_TASK` を使用して、`EpCubeAccessibilityService` にEP CUBEアプリの起動ロジックを実装する (安全対策 3)。
- [x] T009 [US1] `rootInActiveWindow` のNullチェックと遅延を伴うリトライロジックを含む、堅牢なノードポーリングユーティリティ関数を `EpCubeAccessibilityService` に実装する (安全対策 1)。
- [x] T010 [US1] `WAITING_FOR_HOME` 状態のロジックを実装する: "スマートモード" テキストノードをポーリングし、対応する設定ボタンをクリックする。
- [x] T011 [US1] `WAITING_FOR_SETTINGS` 状態のロジックを実装する: "充電上限" テキストノードをポーリングし、現在のSOC値を読み取る。
- [x] T012 [US1] `ADJUSTING_SOC` 状態のロジックを実装する: 現在のSOCと目標SOCの差を計算し、クリック間に `delay(150)` を挟むループで "＋" または "ー" ボタンをクリックする (安全対策 2)。
- [x] T013 [US1] `SAVING` 状態のロジックを実装する: 画面下部の "設定" ボタンをクリックする。
- [x] T014 [US1] `WAITING_FOR_LOADING` および `WAITING_FOR_SUCCESS` 状態のロジックを実装する: "ローディング" ダイアログを検出し、"成功しました。" テキストを待機する。
- [x] T015 [US1] 復帰ロジックを実装する: `MacroResult` データと `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP` を持つ `ACTION_MACRO_RESULT` Intentを `MainActivity` に送信し、Optimizerをフォアグラウンドに戻す。
- [x] T016 [US1] Intent経由でマクロをトリガーするように `MainViewModel.kt` と `MainScreen.kt` を更新し、`MainActivity.kt` で結果Intentを処理する。

**チェックポイント**: この時点で、User Story 1 は完全に機能し、独立してテスト可能である必要があります。

---

## Phase 4: Polish & Cross-Cutting Concerns

**目的**: 複数のユーザーストーリーに影響する改善

- [x] T017 無限のハングを防ぎ `FAILED` 状態を返すために、`EpCubeAccessibilityService` のすべてのポーリングループにタイムアウト処理 (例: `withTimeout`) を追加する。
- [x] T018 `EpCubeAccessibilityService` の状態遷移とエラー条件に対する包括的なログ出力 (Logcat) を追加する。
- [x] T019 実機 (Redmi 9T) で quickstart.md の検証を実行する。

---

## 依存関係と実行順序

### フェーズの依存関係

- **Setup (Phase 1)**: 依存関係なし - すぐに開始可能
- **Foundational (Phase 2)**: Setupの完了に依存 - すべてのユーザーストーリーをブロックする
- **User Stories (Phase 3+)**: すべてFoundationalフェーズの完了に依存
- **Polish (Final Phase)**: 必要なすべてのユーザーストーリーの完了に依存

### ユーザーストーリーの依存関係

- **User Story 1 (P1)**: Foundational (Phase 2) の後に開始可能 - 他のストーリーへの依存関係なし

### 各ユーザーストーリー内

- 統合の前にコア実装を行う
- 次の優先順位に進む前にストーリーを完了する

### 並列実行の機会

- T003 と T004 は並列に実装可能。
- T001 と T002 は並列に実装可能。

---

## 実装戦略

### MVP First (User Story 1 のみ)

1. Phase 1: Setup を完了する
2. Phase 2: Foundational を完了する (重要 - すべてのストーリーをブロックする)
3. Phase 3: User Story 1 を完了する
4. **停止して検証**: 実機で User Story 1 を独立してテストする。
5. 準備ができたらデプロイ/デモを行う

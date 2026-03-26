---
description: "SOC Optimization and Automation (MVP) のためのタスクリスト"
---

# タスク: SOC Optimization and Automation (MVP)

**入力**: `/specs/001-soc-optimization/` の設計ドキュメント
**前提条件**: plan.md, spec.md, data-model.md

**構成**: 各ユーザーストーリーを独立して実装・テストできるように、タスクはユーザーストーリーごとにグループ化されています。

## フォーマット: `[ID] [P?] [Story] 説明`

- **[P]**: 並列実行可能（異なるファイル、依存関係なし）
- **[Story]**: このタスクが属するユーザーストーリー（例: US1, US2, US3）
- 説明には正確なファイルパスを含めること

## フェーズ 1: セットアップ (共有インフラストラクチャ)

**目的**: プロジェクトの初期化と基本構造の作成

- [x] T001 `app/build.gradle.kts` にてJetpack ComposeとKotlin Coroutinesを含むAndroidプロジェクトを初期化する
- [x] T002 `app/build.gradle.kts` にRetrofit, OkHttp, Kotlinx Serialization, DataStoreの依存関係を追加する
- [x] T003 [P] `app/src/main/java/com/ghihin/epcubeoptimizer/` に基本パッケージ構成（`domain`, `data`, `presentation`）を作成する

---

## フェーズ 2: 基盤構築 (ブロックされる前提条件)

**目的**: どのユーザーストーリーを実装する前にも完了していなければならないコアインフラストラクチャ

- [x] T004 [P] `app/src/main/java/com/ghihin/epcubeoptimizer/domain/model/WeatherForecast.kt` に `WeatherForecast` モデルを作成する
- [x] T005 [P] `app/src/main/java/com/ghihin/epcubeoptimizer/domain/model/UserSchedule.kt` に `UserSchedule` モデルを作成する
- [x] T006 [P] `app/src/main/java/com/ghihin/epcubeoptimizer/domain/model/TargetSOC.kt` に `TargetSOC` および `CalculationFactors` モデルを作成する

**チェックポイント**: 基盤の準備完了 - ユーザーストーリーの実装を開始できます

---

## フェーズ 3: ユーザーストーリー 1 & 2 - SOC Decision Engine (優先度: P1) 🎯 MVP

**目標**: 天気とスケジュールから目標SOCを計算するコアロジックを実装する（晴れの出社日は最小化、雨/曇りの在宅日は最大化）。

**独立テスト**: 天気APIのモックとユーザー設定を与えた際に、SOC Decision Engineが正しい目標充電率を算出することを確認する。

### ユーザーストーリー 1 & 2 のテスト (TDD優先)
- [x] T007 [US1] `app/src/test/java/com/ghihin/epcubeoptimizer/domain/usecase/CalculateTargetSocUseCaseTest.kt` に `CalculateTargetSocUseCaseTest` を作成する

### ユーザーストーリー 1 & 2 の実装
- [x] T008 [US1] `app/src/main/java/com/ghihin/epcubeoptimizer/domain/usecase/CalculateTargetSocUseCase.kt` に `CalculateTargetSocUseCase` を実装する

**チェックポイント**: SOC Decision Engine が完全に機能し、独立してテスト可能になりました

---

## フェーズ 4: ユーザーストーリー 1 & 2 - Data Layer (API & DataStore) (優先度: P1)

**目標**: OpenWeatherMap API連携とDataStoreによるスケジュール管理を実装する。

### Data Layer の実装
- [x] T009 [P] [US1] `app/src/main/java/com/ghihin/epcubeoptimizer/core/network/OpenWeatherMapApi.kt` に `OpenWeatherMapApi` インターフェースを作成する
- [x] T010 [P] [US1] `app/src/main/java/com/ghihin/epcubeoptimizer/domain/repository/WeatherRepository.kt` に `WeatherRepository` インターフェースを作成する
- [x] T011 [US1] `app/src/main/java/com/ghihin/epcubeoptimizer/data/repository/WeatherRepositoryImpl.kt` に `WeatherRepositoryImpl` を実装する
- [x] T012 [P] [US1] `app/src/main/java/com/ghihin/epcubeoptimizer/domain/repository/ScheduleRepository.kt` に `ScheduleRepository` インターフェースを作成する
- [x] T013 [US1] `app/src/main/java/com/ghihin/epcubeoptimizer/data/repository/ScheduleRepositoryImpl.kt` にDataStoreを用いて `ScheduleRepositoryImpl` を実装する

**チェックポイント**: Data Layer が SOC Decision Engine に実際のデータを提供する準備が整いました

---

## フェーズ 5: ユーザーストーリー 1 & 2 - Presentation Layer (Jetpack Compose UI) (優先度: P1)

**目標**: 判定結果と理由を分かりやすく表示する Jetpack Compose のUIを作成する。

### Presentation Layer の実装
- [x] T014 [US1] `app/src/main/java/com/ghihin/epcubeoptimizer/presentation/main/MainViewModel.kt` に Loading, Success, Error 状態を管理する `MainViewModel` を作成する
- [x] T015 [US1] `app/src/main/java/com/ghihin/epcubeoptimizer/presentation/main/MainScreen.kt` に SOC と要因を表示する `MainScreen` Compose UI を作成する
- [x] T016 [US1] `app/src/main/java/com/ghihin/epcubeoptimizer/MainActivity.kt` を更新し、`MainScreen` をホストする

**チェックポイント**: MVP UI が完成し、実際のデータまたはモックデータに基づいて計算された SOC を表示します

---

## フェーズ 6: 仕上げと横断的関心事

**目的**: 複数のユーザーストーリーに影響を与える改善

- [ ] T017 `MainViewModel` にエラーハンドリングとリトライロジックを追加する
- [ ] T018 `MainScreen` のUIレイアウトとスタイリングを調整する

---

## 依存関係と実行順序

### フェーズの依存関係

- **セットアップ (フェーズ 1)**: 依存関係なし - すぐに開始可能
- **基盤構築 (フェーズ 2)**: セットアップの完了に依存 - すべてのユーザーストーリーをブロックします
- **ユーザーストーリー (フェーズ 3-5)**: すべて基盤構築フェーズの完了に依存します
- **仕上げ (最終フェーズ)**: 必要なすべてのユーザーストーリーが完了していることに依存します

### 実装戦略

### MVP ファースト

1. フェーズ 1: セットアップ を完了する
2. フェーズ 2: 基盤構築 を完了する (重要 - すべてのストーリーをブロックします)
3. フェーズ 3: SOC Decision Engine (TDD) を完了する
4. フェーズ 4: Data Layer を完了する
5. フェーズ 5: Presentation Layer を完了する
6. **停止して検証**: 完全な MVP フローを独立してテストする
7. 準備ができたらデプロイ/デモを行う

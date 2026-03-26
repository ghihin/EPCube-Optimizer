# 実装計画: SOC Optimization and Automation (MVP)

**Branch**: `001-soc-optimization` | **Date**: 2026-03-26 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/001-soc-optimization/spec.md`

**Note**: この計画は、要求されたMVP（Minimum Viable Product）に焦点を当てています：「まずはAccessibility Serviceを使わずに、『明日の天気とスケジュールからSOCを計算してメイン画面に表示する』 というコア機能のMVPを作る計画」。

## 概要

EP CUBEの深夜充電最適化を実現するためのコアロジック（SOC Decision Engine）を実装し、Accessibility Serviceによる自動操作を省いたMVPを構築する。OpenWeatherMap APIから天気予報を取得し、ユーザーのスケジュール設定と組み合わせて目標SOCを計算し、その結果と根拠をAndroidアプリのメイン画面に表示する。

## 技術コンテキスト

**言語/バージョン**: Kotlin (最新安定版)
**主要な依存関係**: 
- Jetpack Compose (UI)
- Kotlin Coroutines / Flow (非同期処理・状態管理)
- Retrofit / OkHttp (OpenWeatherMap API通信)
- Kotlinx Serialization (JSONパース)
- DataStore (ユーザー設定の保存)
**ストレージ**: Preferences DataStore (スケジュール設定、APIキー等の保存)
**テスト**: JUnit 4/5, MockK, Coroutines Test
**ターゲットプラットフォーム**: Android (専用端末向け)
**プロジェクトタイプ**: Android Application (MVP)
**パフォーマンス目標**: API取得からSOC計算・画面表示まで1秒以内
**制約事項**: 
- Accessibility Serviceの実装はフェーズ2（以降）に先送りする。
- UIはPragmatismの原則に従い、シンプルでデバッグしやすいものとする。
**規模/スコープ**: 単一デバイスでの動作、1日1回のAPIコール（手動更新含む）

## Constitution Check (原則の確認)

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Safety First**: MVPでは実際の機器操作（Accessibility Service）を行わないため、機器への影響はゼロであり安全。
- **Observability**: 計算結果とその根拠（天気、スケジュール）を画面上に明示的に表示することで、ロジックの正当性をユーザーが確認可能にする。
- **Resilience**: API通信エラー時の例外処理とUIへのエラー表示を実装する。
- **Pragmatism**: 複雑な自動化の前に、コアとなる計算ロジックの確実性をMVPで検証するアプローチは極めて実用的である。

## プロジェクト構造

### ドキュメント (この機能)

```text
specs/001-soc-optimization/
├── plan.md              # このファイル
├── research.md          # フェーズ0の出力
├── data-model.md        # フェーズ1の出力
└── quickstart.md        # フェーズ1の出力
```

### ソースコード (リポジトリルート)

```text
app/src/main/java/com/ghihin/epcubeoptimizer/
├── MainActivity.kt
├── core/
│   ├── network/         # Retrofitクライアント、APIインターフェース
│   └── datastore/       # Preferences DataStoreマネージャー
├── domain/
│   ├── model/           # WeatherForecast, UserSchedule, TargetSOC
│   ├── repository/      # データアクセスのためのインターフェース
│   └── usecase/         # CalculateTargetSocUseCase
├── data/
│   ├── repository/      # Repositoryの実装
│   └── remote/          # OpenWeatherMap用のDTO
└── presentation/
    ├── main/            # MainViewModel, MainScreen (Compose)
    └── theme/           # Composeテーマ定義

app/src/test/java/com/ghihin/epcubeoptimizer/
└── domain/
    └── usecase/         # CalculateTargetSocUseCaseTest
```

**構造の決定**: Clean Architectureの原則（Constitution記載）に従い、`domain`（判定ロジック）、`data`（API/DataStore）、`presentation`（UI）に分離したAndroid標準のパッケージ構成を採用する。

## 複雑性の追跡

> **Fill ONLY if Constitution Check has violations that must be justified**

(違反なし。MVPアプローチにより初期実装が簡素化されている。)

# 実装計画: EP CUBE マクロアクセシビリティ

**ブランチ**: `002-epcube-macro-accessibility` | **日付**: 2026-03-31 | **仕様書**: [spec.md](spec.md)
**入力**: `/specs/002-epcube-macro-accessibility/spec.md` からの機能仕様

**注**: このテンプレートは `/speckit.plan` コマンドによって入力されます。実行ワークフローについては `.specify/templates/plan-template.md` を参照してください。

## 概要

EP CUBEの公式アプリには外部連携用のAPIが存在しないため、本アプリ（EPCubeOptimizer）で算出した「目標SOC（充電上限）」を、Androidのユーザー補助機能（AccessibilityService API）を用いてEP CUBEアプリのUIを自動操作することで反映させるマクロ機能を実装する。

## 技術コンテキスト

**言語/バージョン**: Kotlin 1.9+
**主要な依存関係**: Android AccessibilityService API, Kotlin Coroutines/Flow
**ストレージ**: N/A (状態はメモリ上で管理)
**テスト**: JUnit (ロジック部分の単体テスト)
**ターゲットプラットフォーム**: Android (Redmi 9T想定)
**プロジェクトタイプ**: Android App (Background Service)
**パフォーマンス目標**: 画面遷移や通信遅延に耐えうる確実なUI操作の実行
**制約事項**: 座標指定（X,Y）によるタップは禁止。必ず `AccessibilityNodeInfo` を用いたテキストやViewIDベースでの要素特定を行うこと。
**規模/スコープ**: 1つの対象アプリ（`com.eternalplanetenergy.epcube.jp`）の特定画面フローのみをサポート。

## Constitution Check (基本原則チェック)

*GATE: Phase 0 のリサーチ前にパスする必要がある。Phase 1 の設計後に再チェックする。*

- **Safety First**: 異常検知時（タイムアウトや予期せぬ画面遷移）は操作を中断し、元のアプリにエラーを返す設計とする。
- **Observability**: マクロの進行状態（MacroState）やエラー内容をログに出力し、最終結果をEPCubeOptimizerアプリに返す。
- **Resilience**: 画面の描画遅延や通信によるローディング時間を考慮し、各UI要素の特定にはコルーチンを用いたポーリング（リトライ・タイムアウト）を実装する。
- **Pragmatism**: 複雑な画像認識や座標計算は避け、Android標準のAccessibilityService APIを用いたテキストベースの確実な要素特定を採用する。

## プロジェクト構造

### ドキュメント (本機能)

```text
specs/002-epcube-macro-accessibility/
├── plan.md              # このファイル (/speckit.plan コマンドの出力)
├── research.md          # Phase 0 の出力 (/speckit.plan コマンド)
├── data-model.md        # Phase 1 の出力 (/speckit.plan コマンド)
├── quickstart.md        # Phase 1 の出力 (/speckit.plan コマンド)
├── contracts/           # Phase 1 の出力 (/speckit.plan コマンド)
└── tasks.md             # Phase 2 の出力 (/speckit.tasks コマンド - /speckit.plan では作成されない)
```

### ソースコード (リポジトリルート)

```text
app/src/main/java/com/ghihin/epcubeoptimizer/
├── core/
│   └── accessibility/
│       ├── EpCubeAccessibilityService.kt
│       ├── MacroState.kt
│       └── MacroResult.kt
├── presentation/
│   └── main/
│       └── MainViewModel.kt (マクロ実行トリガーの追加)
└── AndroidManifest.xml (AccessibilityServiceの登録)

app/src/main/res/
└── xml/
    └── accessibility_service_config.xml
```

**構造の決定**: 既存のAndroidアプリプロジェクト（`app/`）内に、アクセシビリティ関連の機能を提供する `core/accessibility/` パッケージを新設し、サービスクラスや状態管理クラスを配置する。

## 複雑性の追跡

> **Constitution Check に正当化が必要な違反がある場合のみ記入**

(違反なし)

# Implementation Plan: Automation & Scheduling (Ver. 2.0)

**Branch**: `003-automation-scheduling` | **Date**: 2026-03-31 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `specs/003-automation-scheduling/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command. See `.specify/templates/plan-template.md` for the execution workflow.

## Summary

本機能は、毎晩23:30に自動的にGoogleカレンダーの予定（出社/在宅）と気象データを取得し、蓄電池システムの最適SOCを計算してマクロ経由で設定する。手動操作を完全に排除すると共に、1週間先までのスケジュールと予測SOCをUI上で可視化する。
技術的アプローチとして、カレンダープロバイダへのアクセス（OAuth不要）、`AlarmManager` による正確なスケジュール実行、および画面点灯とキーガード解除のための `BroadcastReceiver` + 透明 `Activity` を組み合わせることで、スリープ状態からの確実なマクロ実行を実現する。

## Technical Context

**Language/Version**: Kotlin 1.9+  
**Primary Dependencies**: Jetpack Compose, Coroutines/Flow, Android AccessibilityService, AlarmManager, ContentResolver (CalendarContract)  
**Storage**: SharedPreferences または DataStore（日毎の判定結果やログの軽量保存用）  
**Testing**: JUnit, Espresso (UI・マクロ動作のモックテスト)  
**Target Platform**: Android (専用の余剰端末での24時間稼働・スリープ対応)  
**Project Type**: mobile-app (Automation/Macro extension)  
**Performance Goals**: N/A (バックグラウンドの深夜実行のため、速度より確実性を重視)  
**Constraints**: Dozeモードからの確実な復帰、およびロック画面の解除が必須  
**Scale/Scope**: 個人利用の1ユーザー、1日1回のバッチ処理および向こう1週間のデータビュー

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Safety First**: Googleカレンダーの読み取り失敗時や天候APIエラー時は、事前に定めた安全なSOC（エラーフォールバック値）を設定し、システムを異常終了させない。
- **Observability**: 毎晩の自動実行の結果（天候取得成功/失敗、カレンダー取得結果、実際のSOC設定成否）を `AutomationExecutionLog` としてローカル（またはFirebase）に記録し、後から確認可能にする。
- **Resilience**: ネットワークエラー時のリトライ処理の実装、およびマクロのUI操作が途中で失敗した場合（20秒後の値再確認フック）の再試行・エラースキップを組み込む。
- **Pragmatism**: 確実性と安定性を最優先し、複雑なOAuth2認証ではなくカレンダープロバイダ経由でのローカル読取を採用。定期実行もWorkManagerではなく確実なAlarmManagerを採用する。

## Project Structure

### Documentation (this feature)

```text
specs/003-automation-scheduling/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
└── tasks.md             # Phase 2 output (/speckit.tasks command)
```

### Source Code (repository root)

```text
src/
├── main/
│   ├── java/com/example/epcube/
│   │   ├── automation/      # AlarmReceiver, WakeLocker, Validation logic
│   │   ├── calendar/        # CalendarProvider access logic
│   │   ├── ui/
│   │   │   └── schedule/    # 7-day schedule viewer (Compose)
│   │   └── accessibility/   # (既存のEpCubeAccessibilityService拡張)
│   └── AndroidManifest.xml  # READ_CALENDAR, USE_EXACT_ALARM, WAKE_LOCK 権限の追加
tests/
├── unit/                    # 予定キーワード判定ロジックのテスト
└── integration/             # モックカレンダーデータを用いたシミュレーションテスト
```

**Structure Decision**: 既存の `002-epcube-macro-accessibility` ブランチの構造をベースとし、自動化トリガー (`automation/`) とカレンダー取得 (`calendar/`) の各ドメインパッケージを追加する、シングルプロジェクト構成を採用する。

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

(該当なし：Constitutionに完全に準拠直結したアーキテクチャであるため、正当化が必要な複雑な回避策は無い)

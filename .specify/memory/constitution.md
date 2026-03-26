<!--
Sync Impact Report
Version change: 0.0.0 -> 1.0.0
Modified principles:
  - [PRINCIPLE_1_NAME] -> Safety First
  - [PRINCIPLE_2_NAME] -> Observability
  - [PRINCIPLE_3_NAME] -> Resilience
  - [PRINCIPLE_4_NAME] -> Pragmatism
Added sections:
  - プロジェクトの概要と目的 (Project Overview and Purpose)
  - 技術的制約と動作環境 (Technical Constraints and Environment)
  - ユーザープロフィールとコンテキスト (User Profile and Context)
Removed sections:
  - [PRINCIPLE_5_NAME]
Templates requiring updates:
  - .specify/templates/plan-template.md (⚠ pending)
  - .specify/templates/spec-template.md (⚠ pending)
  - .specify/templates/tasks-template.md (⚠ pending)
Follow-up TODOs:
  - RATIFICATION_DATE: Set to today as it's the initial creation.
-->
# EPCube-Optimizer (ECO) Constitution

## Core Principles

### I. Safety First
生活基盤である電力に関わるため、異常検知時は操作を停止し、安全側に倒すこと。

### II. Observability
制御の根拠（天気データ等）と結果をログに残し、リモートから成否を確認可能にすること。

### III. Resilience
アプリのUI変更や通信エラーに対し、リトライや例外処理を徹底すること。

### IV. Pragmatism
複雑なUIよりも、深夜の定時実行の確実性と安定性を最優先すること。

## プロジェクトの概要と目的

名称: EPCube-Optimizer (ECO)

目的: 家庭用蓄電池「EP CUBE」の深夜充電設定を、翌日の天気予報とユーザーの生活スケジュールに合わせて自動最適化する。

背景: 太陽光発電（2.040kW）と蓄電池（6.6kWh）の効率を最大化し、電気代の削減とエネルギーの自給自足率向上を目指す。既存アプリにない「インテリジェントな自動制御」を外付けで実現する。

## 技術的制約と動作環境

プラットフォーム: Android (専用の余剰端末での24時間稼働を想定)

制御手法: Android Accessibility Service（ユーザー補助機能）を用いた公式アプリのUI自動操作。

主要技術: Kotlin, Jetpack Compose, Coroutines/Flow, Firebase (Log/通知), OpenWeatherMap API。

アーキテクチャ: Clean Architectureの考え方に基づき、判定ロジック（Domain）と外部操作（UI/API）を分離。

## Governance

本Constitutionはプロジェクトのすべての開発・運用活動において最優先される。
変更を加える場合は、バージョンを更新し、変更理由を明確にすること。
すべての実装および設計は、上記のCore Principles（特にSafety FirstとPragmatism）に準拠しているかレビューされる必要がある。

**Language Requirement**: All project documentation, specifications, plans, and tasks MUST be written in Japanese (日本語). AI agents must output all generated artifacts in Japanese.

**Version**: 1.0.1 | **Ratified**: 2026-03-26 | **Last Amended**: 2026-03-26

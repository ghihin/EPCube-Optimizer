# Quickstart: EP CUBE Macro Accessibility

## 概要

本機能は、AndroidのAccessibilityService APIを利用して、EP CUBE公式アプリのUIを自動操作し、EPCubeOptimizerで算出した目標SOC（充電上限）を反映させるマクロ機能です。

## 開発環境のセットアップ

1. **Android Studio**: 最新版をインストールしてください。
2. **実機テスト**: 本機能はAccessibilityServiceを使用するため、エミュレータではなく実機（Redmi 9T推奨）でのテストが必要です。
3. **EP CUBEアプリ**: テスト用端末にEP CUBE公式アプリ（`com.eternalplanetenergy.epcube.jp`）をインストールし、ログイン済みの状態にしておいてください。

## 実行手順

1. アプリをビルドし、実機にインストールします。
2. 端末の「設定」>「追加設定」>「ユーザー補助」>「ダウンロードしたアプリ」から、「EPCubeOptimizer」のユーザー補助機能を**オン**にします。
3. EPCubeOptimizerアプリを起動し、目標SOCを算出します。
4. 「EP CUBEに反映」ボタン（実装予定）をタップすると、マクロが開始されます。
5. EP CUBEアプリが自動的に起動し、UI操作が行われる様子を確認します。
6. 操作完了後、自動的にEPCubeOptimizerアプリに戻り、結果が表示されます。

## デバッグ方法

- **Logcat**: `EpCubeAccessibilityService` タグでログを出力し、マクロの進行状態（`MacroState`）やエラー内容を確認できます。
- **UI要素の特定**: EP CUBEアプリのUI構造が変わった場合は、Android Studioの「Layout Inspector」を使用して、対象画面のテキストやViewIDを確認し、コードを修正してください。

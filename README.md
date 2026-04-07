# EPCube-Optimizer (ECO) 🔋☀️

**EPCube-Optimizer (ECO)** は、天気予報と家庭の電力消費スケジュールに基づき、EP CUBE蓄電池の深夜充電設定を自動で最適化するAndroidアプリケーションです。

## 🌟 Vision
「技術で家計と地球をスマートに守る」
既存のハードウェアにインテリジェントな制御レイヤーを被せ、太陽光エネルギーの自家消費率を最大化します。

## 🛠 Features
- **AI-Driven SOC Optimization:** 翌日の天気（日照予測）と出社スケジュールから、最適な深夜充電目標値（Target SOC）を算出。
- **UI Automation Control:** Androidの「ユーザー補助機能（Accessibility Service）」を利用し、公式アプリのUIを自動操作して設定を更新。
- **Dedicated Controller Support:** 余剰Android端末を「専用サーバー」として常時稼働させる安定運用。
- **Execution Logging:** 実行結果（成功・失敗、目標SOC、当時の気象など）を Google カレンダーへ毎晩自動的に記録し可視化。

## 🏗 Tech Stack
- **Language:** Kotlin 1.9+
- **Framework:** Jetpack Compose, Kotlin Coroutines & Flow
- **Android APIs:** AccessibilityService API, AlarmManager, WakeLock, KeyguardManager, ContentResolver (CalendarContract)
- **External Integration:** OpenWeatherMap API, Google Calendar

## 🚀 Technical Highlights & Key Challenges

本プロジェクトは、公式APIを持たないクローズドなスマート家電（蓄電池）を、Androidのコア機能を駆使して**「完全に無人で、かつ賢く」**制御するために数々の技術的ハードルを乗り越えています。

### 1. 公式APIを使わない「超堅牢な」UIオートメーション
EP CUBEの公式APIが非公開であるため、Androidの `AccessibilityService` を活用した **ネイティブRPA（UIオートメーション）** を自作しました。
単なる座標タップではなく、画面上のテキストや正規表現ベース（例: `\(\d+%\)`）でDOMノードをポーリング検索し、描画遅延や不意の確認ダイアログにも動的に対応できる「状態遷移ベースのスクレイピング＆操作エンジン」を自前実装しています。

### 2. 「放置専用端末」を確実に叩き起こすディープな制御
クローゼットに眠る「画面ロックされた余剰Android端末」を24時間常駐の専用サーバーとして再利用。
`AlarmManager` による深夜の正確なスケジューリング、`WakeLock` と `KeyguardManager` を組み合わせた**「指定時刻に画面を強制点灯させ、ロックを解除して処理を完遂し、再びスリープに戻る」**という完全無人運用を実現しました。

### 3. データ駆動のハイブリッドSOC（充電量）決定アルゴリズム
「翌日の日照予報」と「現在の物理バッテリー残量」を組み合わせたハイブリッドアルゴリズムを搭載。
OpenWeatherMapから翌日の雲量を取得しつつ、実行直前に公式アプリから**現在のリアルタイムSOCをスクレイピングして判定材料に統合**。太陽光で賄いきれない場合にのみ深夜電力による充電スケジュール（Smart Mode / Green Mode）を最適に決定します。

### 4. Googleカレンダーを「ダッシュボード」化する逆転の発想
専用の運用ダッシュボードやバックエンドサーバー（Firebase等）を構築・維持するコストをゼロにするため、Androidの `CalendarContract` (ContentResolver) を限界まで活用。
マクロの実行結果、成否状態、実行時の天候データやバッテリー前状態をすべて**「ユーザーの普段使いのGoogleカレンダーに過去の予定（ログ）として書き込む」**というサーバーレス・オブザーバビリティを実現しました。利用者は毎朝カレンダーを見るだけで、昨晩のAIの判断と成否を一目で監査できます。

### 5. 徹底した「べき等性」とフェイルセーフ
寝ている間に動くシステムとして、何度起動しても問題を起こさない「べき等性」を保証。すでに目標の設定モードになっていた場合はUI走査をスキップし、カレンダーログの重複も防止。さらにマクロが特定の画面でスタックした場合は90秒のタイムアウトで安全に強制終了しカレンダーへ「エラーイベント」として落とすフォールバック機構を備えています。

## ⚠️ Disclaimer
本プロジェクトは個人開発であり、公式アプリのUIをエミュレートするものです。利用は自己責任で行ってください。

---
Created by [ghihin](https://github.com/ghihin)  
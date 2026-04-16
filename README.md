# EPCube-Optimizer (ECO) 🔋☀️

**EPCube-Optimizer (ECO)** は、気象データの「予想日射量」とGoogleカレンダーの「個人の生活スケジュール」を掛け合わせ、EP CUBE（家庭用蓄電池）の深夜充電設定を**完全自動で最適化**するAndroidアプリケーションです。

## 🌟 Vision
**「技術で家計と地球をスマートに守る」**
公式APIを持たないクローズドなスマート家電（蓄電池）に対し、余剰のAndroid端末を用いたインテリジェントな制御レイヤーを被せることで、太陽光エネルギーの自家消費率を最大化し、無駄な深夜電力の購入を限りなくゼロに近づけます。

## 🛠 Features (The Magic)

### 1. ☀️ Open-Meteo APIによる「短波放射量（W/m²・h）」ダイレクト分析
無料かつ最強の気象APIである **Open-Meteo API** を採用。「1日の平均雲量」というアバウトな指標ではなく、翌日の太陽光発電のゴールデンタイム（08:00〜15:59）に地上へ降り注ぐ**物理的な太陽エネルギー（短波放射量）を合算**して評価。
これにより「夜間はドン曇りだが昼間は快晴」という日に、従来の「平均雲量が悪いので深夜に満充電してしまう」というトラップを完全に打破しました。APIキー不要で誰でもすぐに使い始められます。

### 2. 📅 Googleカレンダーとのシームレスな同期（Instances API）
「明日は出社か？在宅か？」という消費電力のブレを、普段お使いの**Googleカレンダーから自動で読み取ります**。
Androidの `CalendarContract.Instances` URIを活用し、単発の予定だけでなく「毎週金曜日は出社」といった複雑な繰り返しイベント（ Recurring Events ）も正確にパース。「季節 × 予想日射量 × カレンダー予定」の独自の判定マトリクスにより、1%刻みで完璧な目標SOC（充電量）を決定します。

### 3. 🤖 超堅牢な「ネイティブRPA」による無人操作化
EP CUBEの公式アプリを操作するため、Androidの `AccessibilityService` （ユーザー補助機能）を利用した **ネイティブUIオートメーション** を自作。
「設定成功ダイアログの遅延」や「クラウドラグによる状態の巻き戻り」といったUI特有の罠に対して、フェイルセーフ機構、タイムアウト処理、および画面着地確認ループ（`returnToHome()`）を実装し、**「クローゼットで放置された余剰スマホ」が毎晩確実に設定を完遂**する驚異的な安定性を担保しています。

### 4. 🗂 サーバーレスの最強ダッシュボード（Calendar Log Writer）
専用のバックエンドサーバー（Firebase等）は一切不要。マクロが毎晩実行した結果（目標SOC、算出根拠となったW/m²・h、成功可否とエラーメッセージ）を、すべて**「Googleカレンダーの過去の予定」として書き戻します**。
ユーザーは朝起きてスマホのカレンダーを見るだけでシステムが正しく働いたかを監査でき、完全無料のオブザーバビリティ（運用監視）を実現しました。

## 🏗 Tech Stack
- **Language:** Kotlin 1.9+
- **Architecture & Framework:** Jetpack Compose, MVVM + Clean Architecture, Kotlin Coroutines / Flow
- **Dependency Injection:** Hilt / Dagger
- **Android APIs:** AccessibilityService API, AlarmManager, WakeLock, KeyguardManager, ContentResolver (CalendarContract)
- **External Integration:** Open-Meteo API (Retrofit / Kotlinx-Serialization)

## ⚠️ Disclaimer
本プロジェクトは個人開発であり、公式アプリのUIをエミュレートし操作を自動化するものです。ご利用は完全に自己責任で行ってください。

---
Created and maintained by [ghihin](https://github.com/ghihin)
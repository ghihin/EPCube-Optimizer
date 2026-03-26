# EPCube-Optimizer (ECO) 🔋☀️

**EPCube-Optimizer (ECO)** は、天気予報と家庭の電力消費スケジュールに基づき、EP CUBE蓄電池の深夜充電設定を自動で最適化するAndroidアプリケーションです。

## 🌟 Vision
「技術で家計と地球をスマートに守る」
既存のハードウェアにインテリジェントな制御レイヤーを被せ、太陽光エネルギーの自家消費率を最大化します。

## 🛠 Features (Planned)
- **AI-Driven SOC Optimization:** 翌日の天気（日照予測）と出社スケジュールから、最適な深夜充電目標値（Target SOC）を算出。
- **UI Automation Control:** Androidの「ユーザー補助機能（Accessibility Service）」を利用し、公式アプリのUIを自動操作して設定を更新。
- **Dedicated Controller Support:** 余剰Android端末を「専用サーバー」として常時稼働させる安定運用。
- **Monitoring & Notification:** Firebaseと連携し、設定変更の結果をメイン端末へ通知。

## 🏗 Tech Stack
- **Language:** Kotlin
- **Framework:** Jetpack Compose, Android Accessibility Service
- **Logic:** Antigravity (Gemini API) / OpenWeatherMap API
- **Backend:** Firebase (Logging / Notification)

## ⚠️ Disclaimer
本プロジェクトは個人開発であり、公式アプリのUIをエミュレートするものです。利用は自己責任で行ってください。

---
Created by [ghihin](https://github.com/ghihin)  
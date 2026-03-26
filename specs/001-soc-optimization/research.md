# リサーチ: SOC Optimization and Automation (MVP)

## OpenWeatherMap API の統合

**決定事項**: `One Call API 3.0`（または無料枠が必要な場合は `Forecast API`）を使用して、対象地域（埼玉県鶴ヶ島市）の `clouds.all` と `pop` を取得する。
**根拠**: 仕様書で `clouds.all`（雲量パーセンテージ）と `pop`（降水確率）が明示的に要求されているため。One Call APIは、これらの正確なフィールドを含む日次予報を提供する。
**検討した代替案**: 気象庁（JMA）API。仕様書でOpenWeatherMapとその特定のデータフィールド（`clouds.all`, `pop`）が明示的に指定されているため却下。

## ユーザースケジュールの保存

**決定事項**: Android Jetpackの `Preferences DataStore` を使用して、週間のデフォルトスケジュールと日付固有の上書き設定を保存する。
**根拠**: SharedPreferencesに代わるモダンで非同期、かつ型安全な代替手段であり、プロジェクトで使用されているKotlin Coroutines/Flowと完全に適合するため。
**検討した代替案**: Room Database。データ構造（シンプルな週間マップと少数の日付上書き）が単純すぎて、完全なSQLiteデータベースのオーバーヘッドに見合わないため却下。

## UI フレームワーク

**決定事項**: メイン画面に Jetpack Compose を使用する。
**根拠**: プロジェクトのConstitution（原則）でJetpack Composeが指定されているため。計算されたSOCとその要因を表示するMVP UIの迅速な開発が可能になる。
**検討した代替案**: XML Layouts。レガシーなアプローチであり、プロジェクトの技術的制約に反するため却下。

## アーキテクチャ

**決定事項**: MVVM (Model-View-ViewModel) を伴う Clean Architecture。
**根拠**: ConstitutionでドメインロジックをUI/APIから分離することが義務付けられているため。Clean Architectureを伴うMVVMは、モダンなAndroid開発においてこれを達成するための標準的な方法である。`CalculateTargetSocUseCase` がFR-003で定義されたコアロジックをカプセル化する。

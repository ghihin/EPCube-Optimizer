# 実装計画: Weather Logic Accuracy Upgrade (Open-Meteo Integration)

## 概要
OpenWeatherMapからOpen-Meteo APIへの気象データ取得元移行と、それに伴う「短波放射量（W/m²）」ベースの判定ロジック（現在SOC無視）の導入を行います。

## Proposed Changes

### 1. API Client Layer
#### [DELETE] `app/src/main/java/com/ghihin/epcubeoptimizer/core/network/OpenWeatherMapApi.kt`
* OpenWeatherMap関連のAPI呼び出しクラスを削除。

#### [NEW] `app/src/main/java/com/ghihin/epcubeoptimizer/core/network/OpenMeteoApi.kt`
* `latitude`, `longitude`, `hourly=shortwave_radiation`, `timezone=Asia/Tokyo` をパラメータに持つエンドポイントの再定義。
* `WeatherResponse` などのデータクラスは短波放射量の配列を受け取る形に再設計。

### 2. Repository Layer
#### [MODIFY] `app/src/main/java/com/ghihin/epcubeoptimizer/data/repository/WeatherRepositoryImpl.kt`
* `OpenWeatherMapApi` から `OpenMeteoApi` への参照変更。
* APIキーの使用箇所を削除し、固定位置パラメータ (lat: 35.93, lon: 139.40) を使用してリクエスト。
* レスポンスから、「翌日の対象日付」に一致し、かつ「08:00〜16:00」の時間帯にあたる `hourly.time` と `hourly.shortwave_radiation` を抽出し、その合計値（積算日射量）を算出。
* ドメインモデル `WeatherForecast` にこの `shortwaveRadiationSum` を含めるようマッピング。

#### [MODIFY] `app/src/main/java/com/ghihin/epcubeoptimizer/core/di/AppModule.kt`
* `OpenWeatherMapApi` 提供に関連する Retrofit の Base URL と APIキーを `Open-Meteo` 仕様（APIキー無し、BaseUrl変更）に更新。

### 3. Domain Model Layer
#### [MODIFY] `app/src/main/java/com/ghihin/epcubeoptimizer/domain/model/WeatherForecast.kt`
* プロパティから `cloudiness` と `probabilityOfPrecipitation` を廃止するか、または `shortwaveRadiationSum` プロパティを追加。

#### [MODIFY] `app/src/main/java/com/ghihin/epcubeoptimizer/domain/model/CalculationFactors.kt`
* `cloudiness`, `pop` 等を `shortwaveRadiationSum`（Int等）に置き換え。

### 4. UseCase Layer (判定ロジック)
#### [MODIFY] `app/src/main/java/com/ghihin/epcubeoptimizer/domain/usecase/CalculateTargetSocUseCase.kt`
* 現在SOC（や現状の複雑な条件分岐）への依存を廃止し、季節ごとのマトリクスを新たに定義する。
* **APIエラー時のフォールバック**: API取得例外時は無条件で目標SOCを `100%` に固定する安全側(フェイルセーフ)の設計に更新。
* **マクロ互換性のためのフラグマッピング**: `TargetSocResult.isSunnyTomorrow` は「積算日射量が `THRESHOLD_SUNNY` 以上かどうか」だけを返すようにし、既存の `AutomationOrchestrator` と `EpCubeAccessibilityService` がグリーンモードへ切り替える判定をそのまま活かす。
* **閾値判定と季節×スケジュールのマトリクス実装**:
  * 雲量による `isSunny` 判定を廃止し、2つの積算日射量閾値 (`THRESHOLD_SUNNY`, `THRESHOLD_CLOUDY`) を用いた判定に置き換える。
  * `targetDate.month` により【中間期】【夏季】【冬季】の3パターンに分岐する仕組みは維持。
  * 各季節用の SOC定義定数を追加 (例: `TARGET_SOC_SUMMER_SUNNY_HOME`, `TARGET_SOC_WINTER_RAIN_COMMUTE` 等)。
  * **中間期**: 積算日射量（晴れ/くもり/雨）× スケジュール で 60%, 70%, 80%, 100% を決定。
  * **夏季 / 冬季**: 積算日射量（晴れ / 非晴れ）× スケジュール で 60%, 70%, 80%, 90%, 100% を決定。

### 5. Logging / Analytics Layer
#### [MODIFY] `app/src/main/java/com/ghihin/epcubeoptimizer/calendar/CalendarLogWriter.kt`
* 説明欄に出力している雲量情報を `[予想日射量] ${forecast.shortwaveRadiationSum} W/m²` （または Wh/m²）に書き換え。

#### [MODIFY] `app/src/main/java/com/ghihin/epcubeoptimizer/automation/AutomationOrchestrator.kt`
* システムログ等に出力しているパラメータ情報の変更。

### 6. Tests Layer
#### [DELETE] `app/src/test/java/com/ghihin/epcubeoptimizer/data/repository/WeatherRepositoryImplTest.kt` (一部)
* OpenWeatherMap固有のテスト削除、OpenMeteo用テストの追加。

#### [MODIFY] `app/src/test/java/com/ghihin/epcubeoptimizer/domain/usecase/CalculateTargetSocUseCaseTest.kt`
* 「積算日射量が閾値以上なら強制的に60%、閾値未満ならスマートモード」の新しい単体テストを追加。

## Verification Plan
### Automated Tests
* `WeatherRepositoryImplTest` にて、対象時間帯（08:00〜16:00）のみ抽出して合計できているかを検証。
* `CalculateTargetSocUseCaseTest` にて、現在SOCなどを渡さなくても「日射量の合計値」のみでモードが決定されていることを検証。

### Manual Verification
* ビルドし、`ScheduleViewer` 画面にてカレンダーおよび予測日射量が適切に表示され、クラッシュしないことを確認する。
* マクロが正しく `green_mode` 又は `smart_mode` を選別できているかを `AutomationOrchestrator` のデバッグ出力で確認する。

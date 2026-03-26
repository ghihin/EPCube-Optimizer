# データモデル: SOC Optimization and Automation (MVP)

## エンティティ

### WeatherForecast
対象日（明日）の天気予報を表す。

- `date`: LocalDate (予報の日付)
- `cloudiness`: Int (0-100%, `clouds.all` からマッピング)
- `probabilityOfPrecipitation`: Float (0.0-1.0, `pop` からマッピング)

### UserSchedule
ユーザーの通勤スケジュールを表す。

- `defaultWeeklySchedule`: Map<DayOfWeek, Boolean> (出社ならTrue、在宅ならFalse)
- `dateOverrides`: Map<LocalDate, Boolean> (週間のデフォルトを上書きする特定の日付)

### TargetSOC
計算結果とその要因。

- `value`: Int (20-100%, 計算された目標SOC)
- `factors`: CalculationFactors (計算に使用された入力値)

### CalculationFactors
コンテキストロギングのデータ構造 (FR-008)。

- `cloudiness`: Int
- `pop`: Float
- `isCommuteDay`: Boolean

## バリデーションルール

- `WeatherForecast.cloudiness` は 0 から 100 の間でなければならない。
- `WeatherForecast.probabilityOfPrecipitation` は 0.0 から 1.0 の間でなければならない。
- `TargetSOC.value` は 20 から 100 の間でなければならない (FR-003の下限値に基づく)。

## 状態遷移 (メイン画面)

- `Loading`: 天気データとユーザースケジュールを取得中。
- `Success`: 計算された `TargetSOC` と `CalculationFactors` を表示中。
- `Error`: エラーメッセージ（例：ネットワーク障害、APIキーの欠落）と再試行ボタンを表示中。

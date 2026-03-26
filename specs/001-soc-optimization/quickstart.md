# クイックスタート: SOC Optimization and Automation (MVP)

## 前提条件

- Android Studio (最新の安定版)
- Kotlin 1.9+
- OpenWeatherMap API Key (無料枠で十分です)

## セットアップ

1. リポジトリをクローンします。
2. Android Studioでプロジェクトを開きます。
3. プロジェクトのルートに `local.properties` ファイルを作成します（存在しない場合）。
4. `local.properties` にOpenWeatherMapのAPIキーを追加します:
   ```properties
   OPEN_WEATHER_MAP_API_KEY="your_api_key_here"
   ```
5. Gradleファイルとプロジェクトを同期します。

## MVPの実行

1. Androidデバイスを接続するか、エミュレーターを起動します。
2. `app` 構成を実行します。
3. メイン画面には、埼玉県鶴ヶ島市の天気予報とデフォルトのスケジュール（UIで変更可能）に基づいて計算された、明日の目標SOCが表示されます。

## テスト

ドメインロジックのユニットテストを実行します:

```bash
./gradlew testDebugUnitTest
```

コアロジックは `CalculateTargetSocUseCaseTest.kt` に配置されています。

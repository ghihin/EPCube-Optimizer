# 関連チェックリスト (Tasks for 006-open-meteo-integration)

## フェーズ1: 準備と設計 (Specify)
- [x] 新規ブランチ (`feature/open-meteo-integration`) の作成
- [x] `spec.md` (仕様書) および `tasks.md` (チェックリスト) の作成
- [x] 【現在地】作成した仕様書に対するユーザーレビュー・承認の取得

## フェーズ2: 計画策定 (Plan) - ※ユーザーの承認後に着手
- [x] APIの実装設計とドメインモデル変更の明確化
- [x] `CalculateTargetSocUseCase` の判定ロジック（日照時間と閾値）の設計
- [x] カレンダーおよびログ書き込み用のフォーマット更新の設計

## フェーズ3: 実装 (Implement)
- [x] 既存の OpenWeatherMap 関連クラス・APIキーなどの不要なリソースを削除
- [x] Open-Meteo API (`hourly=shortwave_radiation`) 用の Retrofit インターフェース作成
- [x] 新しい `WeatherRepositoryImpl` の実装と、「翌日08:00〜16:00」間の積算日射量算出ロジックの追加
- [x] ドメインモデル・ユースケース層での「APIエラー時のフェイルセーフ（目標SOC100%固定）」の実装
- [x] `CalculateTargetSocUseCase` 改修: 四季 (中間期/夏季/冬季) × 積算日射量閾値 × スケジュールの判定マトリクス実装
- [x] `CalculateTargetSocUseCase` 改修: マクロ互換性維持のための `isSunnyTomorrow` フラグの算出（閾値ベース）実装
- [x] `CalendarLogWriter` および `AutomationOrchestrator` の気象ログ情報を「予想日射量」に変更
- [x] 各クラスのアーキテクチャ変更に伴うユニットテストの修正・追加

## フェーズ4: 検証 (Verify)
- [x] `WeatherRepositoryImplTest` の実行 (対象時間帯の抽出・合計、フェイルセーフの確認)
- [x] `CalculateTargetSocUseCaseTest` の実行 (全季節とスケジュールごとのマトリクス通りにSOC算出されるか確認)
- [x] UI画面 (`ScheduleViewer`) およびカレンダー出力などでクラッシュがないかの手動確認
- [x] `app` モジュールの全ユニットテストのパス確認

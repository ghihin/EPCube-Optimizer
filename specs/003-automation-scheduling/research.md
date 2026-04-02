# Phase 0: Research & Decisions

## 1. Googleカレンダー連携手段 (Google Calendar Integration)
- **Decision**: Androidのカレンダープロバイダ (`CalendarContract`) を利用してローカルの予定を取得する。
- **Rationale**: 専用のAndroid端末にはすでにユーザーのGoogleアカウントが同期されている前提（Pragmatismの原則に合致）とし、複雑なOAuth2認証やGoogle Cloud ConsoleでのAPI設定を省略する。`READ_CALENDAR` 権限を付与するだけで、標準のGoogleカレンダー同期情報にローカルからアクセスできるため、非常にシンプルかつ堅牢。
- **Alternatives considered**: Google Calendar API (REST) を使用する方法。しかし、OAuthフローが必要となり設定の手間が大きいため不採用。

## 2. スリープ解除とロック画面突破 (WakeLock & Screen Unlock Strategy)
- **Decision**: `AlarmManager` で起動する `BroadcastReceiver` から、画面点灯用の透明なActivityを起動し、そのActivity内で `WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON | FLAG_KEEP_SCREEN_ON` (またはAPIレベルに応じた最新の `setShowWhenLocked`, `setTurnScreenOn`) と `KeyguardManager.requestDismissKeyguard` を使用する。
- **Rationale**: AccessibilityServiceによるマクロ操作は画面が点灯しており、かつロックが解除されている状態でのみ動作する。専用端末を利用するため、ロック画面は「なし」か「スワイプ」のみに設定してもらうことを前提（Pragmatism）とし、標準APIで確実に画面を点灯させてからマクロを発火する。
- **Alternatives considered**: `PowerManager.WakeLock` 単体での制御。しかしWakeLockだけでは画面点灯やキーガード（ロック画面）の解除が確実に行えないため、Activityを経由するアプローチを採用。

## 3. 深夜の全自動実行トリガー (Nightly Execution Trigger)
- **Decision**: `AlarmManager.setExactAndAllowWhileIdle()` を使用して毎晩23:30にトリガーを発火させる。
- **Rationale**: `WorkManager` はバッテリー最適化（Dozeモード）の影響を強く受け、指定時刻（23:30）ピッタリの実行が保証されない。本要件では時刻通りに動くことが重要であるため、Dozeモード中でもスケジュール通りの時刻に発火できる `AlarmManager` の利用が必須である。
- **Alternatives considered**: `WorkManager` の `setExpedited`。正確な時間指定には不向きなため不採用。

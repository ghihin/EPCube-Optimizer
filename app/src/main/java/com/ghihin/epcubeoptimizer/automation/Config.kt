package com.ghihin.epcubeoptimizer.automation

object Config {
    // 毎晩の全自動実行を行う時刻の設定
    const val AUTO_EXECUTION_HOUR = 21
    const val AUTO_EXECUTION_MINUTE = 0

    // 天候取得やカレンダー取得に失敗した場合の安全側のデフォルトSOC設定（例として60%）
    const val FALLBACK_SAFE_SOC = 60

    // 値再確認のための待機時間（ミリ秒）
    const val VERIFICATION_WAIT_TIME_MS = 20_000L

    // デバッグ・テスト用のログタグ
    const val LOG_TAG = "EcoAutomation"
}

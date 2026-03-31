# Intent Contract: EP CUBE Macro Accessibility

## 1. EPCubeOptimizer -> AccessibilityService (マクロ実行開始)

EPCubeOptimizerアプリからAccessibilityServiceに対して、目標SOCを渡してマクロの実行を指示する。

**Action**: `com.ghihin.epcubeoptimizer.ACTION_START_MACRO`
**Component**: `com.ghihin.epcubeoptimizer.service.EpCubeAccessibilityService` (またはBroadcastReceiver経由)
**Extras**:
- `EXTRA_TARGET_SOC` (Int): 設定したい目標SOCの値 (0〜100)

## 2. AccessibilityService -> EPCubeOptimizer (マクロ実行結果)

AccessibilityServiceからEPCubeOptimizerアプリに対して、マクロの実行結果を返し、アプリをフォアグラウンドに復帰させる。

**Action**: `com.ghihin.epcubeoptimizer.ACTION_MACRO_RESULT`
**Component**: `com.ghihin.epcubeoptimizer.MainActivity`
**Flags**: `Intent.FLAG_ACTIVITY_NEW_TASK` | `Intent.FLAG_ACTIVITY_CLEAR_TOP`
**Extras**:
- `EXTRA_IS_SUCCESS` (Boolean): マクロが成功したかどうか
- `EXTRA_TARGET_SOC` (Int): 設定しようとした目標SOC
- `EXTRA_ERROR_MESSAGE` (String?): 失敗時のエラーメッセージ（成功時はnullまたは未設定）

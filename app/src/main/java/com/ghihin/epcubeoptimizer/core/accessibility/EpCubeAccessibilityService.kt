package com.ghihin.epcubeoptimizer.core.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.*

class EpCubeAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "EpCubeAccessibility"
        const val ACTION_START_MACRO = "com.ghihin.epcubeoptimizer.ACTION_START_MACRO"
        const val EXTRA_TARGET_SOC = "EXTRA_TARGET_SOC"
        const val EXTRA_IS_SUNNY_TOMORROW = "EXTRA_IS_SUNNY_TOMORROW"
        
        const val ACTION_MACRO_RESULT = "com.ghihin.epcubeoptimizer.ACTION_MACRO_RESULT"
        const val EXTRA_IS_SUCCESS = "EXTRA_IS_SUCCESS"
        const val EXTRA_ERROR_MESSAGE = "EXTRA_ERROR_MESSAGE"
        const val EXTRA_TARGET_SOC_RESULT = "EXTRA_TARGET_SOC_RESULT"
        
        const val EXTRA_PRE_EXEC_SOC = "EXTRA_PRE_EXEC_SOC"
        const val EXTRA_PRE_EXEC_MODE = "EXTRA_PRE_EXEC_MODE"
        
        private const val TARGET_PACKAGE = "com.eternalplanetenergy.epcube.jp"
    }

    private var currentState = MacroState.IDLE
    private var targetSoc: Int = -1
    private var isSunnyTomorrow: Boolean = false
    private var currentSocScraped: Int = -1
    private var currentModeScraped: String? = null
    private var macroJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var successToastDetected = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Service connected")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand received: ${intent?.action}")
        if (intent?.action == ACTION_START_MACRO) {
            targetSoc = intent.getIntExtra(EXTRA_TARGET_SOC, -1)
            isSunnyTomorrow = intent.getBooleanExtra(EXTRA_IS_SUNNY_TOMORROW, false)
            if (targetSoc in 0..100) {
                successToastDetected = false // Reset flag
                startMacro()
            } else {
                Log.e(TAG, "Invalid target SOC: $targetSoc")
                returnMacroResult(false, "Invalid target SOC: $targetSoc")
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        
        // Detect Toast messages (like "成功しました。")
        if (event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            val text = event.text.joinToString(" ")
            Log.d(TAG, "Notification/Toast detected: $text")
            if (text.contains("成功しました")) {
                successToastDetected = true
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted")
        currentState = MacroState.IDLE
        macroJob?.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun startMacro() {
        if (currentState != MacroState.IDLE && currentState != MacroState.COMPLETED && currentState != MacroState.FAILED) {
            Log.w(TAG, "Macro already running. Current state: $currentState")
            return
        }

        Log.d(TAG, "Starting macro for target SOC: $targetSoc")
        currentState = MacroState.STARTING
        
        macroJob?.cancel()
        macroJob = serviceScope.launch {
            try {
                withTimeout(90_000) { // 90 seconds overall timeout
                    executeMacroSteps()
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Macro timed out at state: $currentState")
                currentState = MacroState.FAILED
                returnMacroResult(false, "Timeout at state: $currentState")
            } catch (e: Exception) {
                Log.e(TAG, "Macro failed with exception", e)
                currentState = MacroState.FAILED
                returnMacroResult(false, "Error: ${e.message}")
            }
        }
    }

    private suspend fun executeMacroSteps() {
        // 1. Launch App
        launchEpCubeApp()
        delay(4000) // アプリ起動・ホーム画面読み込み待ちタイムアウトを少し長めに
        
        // 2. Read Phase (Scraping current SOC from Home)
        currentState = MacroState.WAITING_FOR_HOME
        var currentSoc: Int? = null
        try {
            val socNode = pollForNodeByRegex("\\(\\d+%\\)", timeoutMs = 8000)
            if (socNode != null) {
                val match = Regex("\\((\\d+)%\\)").find(socNode.text?.toString() ?: "")
                if (match != null) {
                    currentSoc = match.groupValues[1].toInt()
                    currentSocScraped = currentSoc // 値を退避
                    Log.d(TAG, "Scraped current SOC from home: $currentSoc%")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to scrape current SOC, will fallback to SMART mode.", e)
        }

        // 3. Mode Decision
        val currentMode = findCurrentModeOnHome()
        currentModeScraped = currentMode // 値を退避
        val isGreenModeNext = (currentSoc != null && currentSoc >= 60 && isSunnyTomorrow)
        Log.d(TAG, "Mode decision: currentSoc=$currentSoc, isSunnyTomorrow=$isSunnyTomorrow, currentMode=$currentMode => GreenMode? $isGreenModeNext")

        // Idempotency Check
        if (isGreenModeNext && currentMode?.contains("グリーンモード") == true) {
            Log.d(TAG, "Already in Green Mode. Idempotency achieved. Skipping UI actions.")
            currentState = MacroState.COMPLETED
            returnMacroResult(true, null)
            return
        }

        // 4. Action Phase (Tap existing mode button on Home to enter Settings)
        val modeButton = pollForOperationModeButtonOnHome()
        modeButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        delay(2000) // 遷移待ち

        if (isGreenModeNext) {
            // --- GREEN MODE ACTION ---
            val greenRadio = pollForNodeByText("グリーンモード")
            clickNodeOrParent(greenRadio)
            delay(500)
            
            // 確認ダイアログ対策（無ければスルーする）
            try {
                val confirmButton = pollForNodeByText("確認", timeoutMs = 2000)
                clickNodeOrParent(confirmButton)
                delay(500)
            } catch (e: Exception) {
                // do nothing
            }

            val saveButton = pollForNodeByText("設定")
            clickNodeOrParent(saveButton)
            
            Log.d(TAG, "Waiting ${com.ghihin.epcubeoptimizer.automation.Config.VERIFICATION_WAIT_TIME_MS / 1000} seconds for settings to apply...")
            delay(com.ghihin.epcubeoptimizer.automation.Config.VERIFICATION_WAIT_TIME_MS)

            returnToHome()

        } else {
            // --- SMART MODE ACTION (Fallback includes this) ---
            val isAlreadySmart = currentMode?.contains("スマートモード") == true
            
            if (!isAlreadySmart) {
                // Not in Smart Mode -> do 2-stage flow to assure UI
                val smartRadio = pollForNodeByText("スマートモード")
                clickNodeOrParent(smartRadio)
                delay(500)

                // 確認ダイアログ対策（無ければスルーする）
                try {
                    val confirmButton = pollForNodeByText("確認", timeoutMs = 2000)
                    clickNodeOrParent(confirmButton)
                    delay(500)
                } catch (e: Exception) {
                    // do nothing
                }

                // Step 1: まず設定ボタンでスマートモードを確定させる
                val firstSaveButton = pollForNodeByText("設定")
                clickNodeOrParent(firstSaveButton)
                delay(3000) // ホーム画面または設定反映を待つ

                // Androidシステムによる確実な「戻る」アクションで確実にホームを待つ
                returnToHome()

                // Step 2: リセットされたホーム画面から再度モードボタンを押してスライダーを操作するため設定画面に入る
                val modeButtonAgain = pollForOperationModeButtonOnHome()
                modeButtonAgain.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                delay(2000)
            } else {
                Log.d(TAG, "Already in Smart Mode, adjusting slider directly...")
            }

            // ホーム画面のUIが通信ラグ等で古かった場合、再進入時にグリーンモードが選ばれた状態に戻ってしまうことがあるため、
            // スライダーを操作する直前にもう一度確実に「スマートモード」ラジオボタンを押下する
            val finalSmartRadio = pollForNodeByText("スマートモード")
            clickNodeOrParent(finalSmartRadio)
            delay(500)
            try {
                val confirmButton = pollForNodeByText("確認", timeoutMs = 2000)
                clickNodeOrParent(confirmButton)
                delay(500)
            } catch (e: Exception) {
                // do nothing
            }

            currentState = MacroState.ADJUSTING_SOC
            adjustSocValue() // 既存のスライダー調整ロジック

            currentState = MacroState.SAVING
            val finalSaveButton = pollForNodeByText("設定")
            clickNodeOrParent(finalSaveButton)

            Log.d(TAG, "Waiting ${com.ghihin.epcubeoptimizer.automation.Config.VERIFICATION_WAIT_TIME_MS / 1000} seconds for settings to apply...")
            delay(com.ghihin.epcubeoptimizer.automation.Config.VERIFICATION_WAIT_TIME_MS)

            // 調整結果の検証
            currentState = MacroState.WAITING_FOR_SUCCESS
            val newSoc = readCurrentSoc()
            if (newSoc != targetSoc) {
                throw Exception("検証失敗。期待値: $targetSoc, 実際: $newSoc")
            }
            Log.d(TAG, "Verification successful! New SOC is $newSoc")

            returnToHome()
        }

        currentState = MacroState.COMPLETED
        returnMacroResult(true, null)
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo) {
        if (node.isClickable) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return
        }
        var parent = node.parent
        for (i in 0..3) {
            if (parent == null) break
            if (parent.isClickable) {
                parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return
            }
            parent = parent.parent
        }
        throw Exception("Clickable node not found for text ${node.text}")
    }

    private suspend fun pollForNodeByRegex(pattern: String, timeoutMs: Long = 10000): AccessibilityNodeInfo? {
        Log.d(TAG, "Polling for regex: $pattern")
        val regex = Regex(pattern)
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val root = rootInActiveWindow
            if (root != null) {
                val node = findNodeByRegex(root, regex)
                if (node != null) return node
            }
            delay(500)
        }
        return null
    }

    private fun findNodeByRegex(node: AccessibilityNodeInfo, regex: Regex): AccessibilityNodeInfo? {
        val text = node.text?.toString()
        if (text != null && regex.containsMatchIn(text)) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                val found = findNodeByRegex(child, regex)
                if (found != null) return found
            }
        }
        return null
    }

    private suspend fun pollForOperationModeButtonOnHome(timeoutMs: Long = 8000): AccessibilityNodeInfo {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val button = findOperationModeButtonOnHome()
            if (button != null) return button
            delay(500)
        }
        throw Exception("ホーム画面で運転モード再設定ボタンが見つかりませんでした")
    }

    private suspend fun returnToHome() {
        for (i in 0..4) {
            performGlobalAction(GLOBAL_ACTION_BACK)
            delay(1500)
            val root = rootInActiveWindow
            if (root != null) {
                // ホーム画面特有のテキストが存在すればホームに戻ったと判定
                if (root.findAccessibilityNodeInfosByText("エネルギー自給率").isNotEmpty() ||
                    root.findAccessibilityNodeInfosByText("系統").isNotEmpty() ||
                    root.findAccessibilityNodeInfosByText("本日の売電量").isNotEmpty()
                ) {
                    Log.d(TAG, "Successfully returned to Home screen.")
                    return
                }
            }
        }
        Log.e(TAG, "Failed to confirm return to Home screen.")
    }

    private fun findCurrentModeOnHome(): String? {
        val root = rootInActiveWindow ?: return null
        val modes = listOf("グリーンモード", "スマートモード", "売電モード", "蓄電優先モード")
        for (mode in modes) {
            if (root.findAccessibilityNodeInfosByText(mode).isNotEmpty()) {
                return mode
            }
        }
        return null
    }

    private fun findOperationModeButtonOnHome(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val candidates = mutableListOf<AccessibilityNodeInfo>()
        
        candidates.addAll(root.findAccessibilityNodeInfosByText("グリーンモード"))
        candidates.addAll(root.findAccessibilityNodeInfosByText("スマートモード"))
        candidates.addAll(root.findAccessibilityNodeInfosByText("売電モード"))
        candidates.addAll(root.findAccessibilityNodeInfosByText("蓄電優先モード"))
        
        for (node in candidates) {
            var parent = node
            for (level in 0..5) {
                if (parent == null) break
                if (parent.isClickable) {
                    return parent
                }
                parent = parent.parent
            }
        }
        return null
    }

    private suspend fun readCurrentSoc(): Int {
        val chargeLimitNode = pollForNodeByText("充電上限")
        
        var text = chargeLimitNode.text?.toString() ?: ""
        var match = Regex("\\d+").find(text)
        
        if (match == null) {
            val parent = chargeLimitNode.parent
            if (parent != null) {
                for (i in 0 until parent.childCount) {
                    val child = parent.getChild(i)
                    val childText = child?.text?.toString() ?: ""
                    val childMatch = Regex("\\d+").find(childText)
                    if (childMatch != null) {
                        match = childMatch
                        break
                    }
                }
            }
        }
        return match?.value?.toIntOrNull() ?: throw Exception("現在のSOC値を読み取れませんでした: $text")
    }

    private fun findBackButton(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        
        // 1. Try finding by content description "戻る" or "Navigate up"
        val backButtons = root.findAccessibilityNodeInfosByText("戻る")
        if (backButtons.isNotEmpty()) return backButtons.first()
        
        val navigateUpButtons = root.findAccessibilityNodeInfosByText("Navigate up")
        if (navigateUpButtons.isNotEmpty()) return navigateUpButtons.first()

        // 2. Try finding by class name (ImageView or ImageButton) in the top-left corner
        val rect = android.graphics.Rect()
        val potentialBackButtons = mutableListOf<AccessibilityNodeInfo>()
        
        fun collectTopLeftButtons(node: AccessibilityNodeInfo) {
            if (node.isClickable && (node.className?.contains("ImageView") == true || node.className?.contains("ImageButton") == true)) {
                node.getBoundsInScreen(rect)
                // Check if it's in the top-left corner (e.g., x < 200, y < 300)
                if (rect.left < 200 && rect.top < 300) {
                    potentialBackButtons.add(node)
                }
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    collectTopLeftButtons(child)
                }
            }
        }
        
        collectTopLeftButtons(root)
        
        if (potentialBackButtons.isNotEmpty()) {
            // Return the one closest to the top-left
            return potentialBackButtons.minByOrNull { 
                it.getBoundsInScreen(rect)
                rect.left + rect.top 
            }
        }
        
        return null
    }

    private fun launchEpCubeApp() {
        Log.d(TAG, "Launching EP CUBE app")
        val launchIntent = packageManager.getLaunchIntentForPackage(TARGET_PACKAGE)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launchIntent)
        } else {
            throw Exception("EP CUBE app not found")
        }
    }

    private suspend fun pollForNodeByText(text: String, timeoutMs: Long = 10000): AccessibilityNodeInfo {
        Log.d(TAG, "Polling for text: $text")
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val root = rootInActiveWindow
            if (root != null) {
                val nodes = root.findAccessibilityNodeInfosByText(text)
                if (nodes.isNotEmpty()) {
                    Log.d(TAG, "Found text: $text")
                    return nodes[0]
                }
            } else {
                Log.w(TAG, "rootInActiveWindow is null, retrying...")
            }
            delay(500)
        }
        throw Exception("テキスト「$text」が見つからずタイムアウトしました")
    }

    private fun findSettingsButtonNear(referenceNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val clickableNodes = mutableListOf<AccessibilityNodeInfo>()
        var current = referenceNode
        var parent = current.parent
        
        val refRect = android.graphics.Rect()
        referenceNode.getBoundsInScreen(refRect)
        val refCenterY = refRect.centerY()
        
        // Go up the tree, but stop if the parent becomes too large (e.g., > 400px height)
        for (level in 0..5) {
            if (parent == null) break
            
            val parentRect = android.graphics.Rect()
            parent.getBoundsInScreen(parentRect)
            if (parentRect.height() > 400) {
                Log.d(TAG, "Parent too large (height=${parentRect.height()}), stopping upward search")
                break
            }
            
            // Check if the parent itself is clickable (e.g., the whole row is a button)
            if (parent.isClickable) {
                clickableNodes.add(parent)
            }
            
            for (i in 0 until parent.childCount) {
                val child = parent.getChild(i)
                if (child != null && child != current) {
                    collectPotentialButtons(child, clickableNodes)
                }
            }
            current = parent
            parent = current.parent
        }

        // Filter nodes that are horizontally aligned with the reference node
        val alignedNodes = clickableNodes.filter { node ->
            val rect = android.graphics.Rect()
            node.getBoundsInScreen(rect)
            val centerY = rect.centerY()
            Math.abs(centerY - refCenterY) < 100 // Within 100 pixels vertically
        }

        // Find the right-most clickable node among the aligned ones
        var rightMostNode: AccessibilityNodeInfo? = null
        var maxRight = -1
        val rect = android.graphics.Rect()

        for (node in alignedNodes) {
            node.getBoundsInScreen(rect)
            if (rect.right > maxRight) {
                maxRight = rect.right
                rightMostNode = node
            }
        }

        return rightMostNode
    }

    private fun collectPotentialButtons(node: AccessibilityNodeInfo, list: MutableList<AccessibilityNodeInfo>) {
        // Collect nodes that are clickable, or are ImageViews (often used for custom buttons)
        if (node.isClickable || node.className?.contains("ImageView") == true || node.className?.contains("ImageButton") == true) {
            list.add(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                collectPotentialButtons(child, list)
            }
        }
    }

    private fun dumpNodeHierarchy(node: AccessibilityNodeInfo?, depth: Int = 0) {
        if (node == null) return
        val indent = "  ".repeat(depth)
        val rect = android.graphics.Rect()
        node.getBoundsInScreen(rect)
        Log.d(TAG, "$indent[${node.className}] text='${node.text}' desc='${node.contentDescription}' bounds=$rect clickable=${node.isClickable}")
        for (i in 0 until node.childCount) {
            dumpNodeHierarchy(node.getChild(i), depth + 1)
        }
    }

    private suspend fun adjustSocValue() {
        Log.d(TAG, "Adjusting SOC to $targetSoc")
        
        // Find the "充電上限" text node
        val chargeLimitNode = pollForNodeByText("充電上限")
        
        // Read current SOC from the text (e.g., "充電上限: 90 %") or its siblings
        var text = chargeLimitNode.text?.toString() ?: ""
        var match = Regex("\\d+").find(text)
        
        if (match == null) {
            // If the number is not in the same node, check siblings
            val parent = chargeLimitNode.parent
            if (parent != null) {
                for (i in 0 until parent.childCount) {
                    val child = parent.getChild(i)
                    val childText = child?.text?.toString() ?: ""
                    val childMatch = Regex("\\d+").find(childText)
                    if (childMatch != null) {
                        match = childMatch
                        break
                    }
                }
            }
        }
        
        val currentSoc = match?.value?.toIntOrNull() ?: throw Exception("現在のSOC値を読み取れませんでした: $text")
        
        Log.d(TAG, "Current SOC: $currentSoc, Target SOC: $targetSoc")
        
        if (currentSoc == targetSoc) {
            Log.d(TAG, "SOC is already at target value")
            return
        }

        // Find the "+" and "-" buttons
        // They are likely siblings or cousins of the text node
        val clickableNodes = mutableListOf<AccessibilityNodeInfo>()
        var parent = chargeLimitNode.parent
        
        val refRect = android.graphics.Rect()
        chargeLimitNode.getBoundsInScreen(refRect)
        val refCenterY = refRect.centerY()
        
        for (level in 0..5) {
            if (parent == null) break
            
            if (parent.isClickable) {
                clickableNodes.add(parent)
            }
            
            for (i in 0 until parent.childCount) {
                val child = parent.getChild(i)
                if (child != null) {
                    collectPotentialButtons(child, clickableNodes)
                }
            }
            parent = parent.parent
        }

        // Filter nodes that are below the reference node (text) but not too far
        // The buttons are below the "充電上限" text.
        val alignedNodes = clickableNodes.filter { node ->
            val rect = android.graphics.Rect()
            node.getBoundsInScreen(rect)
            val centerY = rect.centerY()
            val yDiff = centerY - refCenterY
            // The buttons should be below the text (yDiff > 0) and within 300 pixels
            // Also, filter out large containers (width > 300) to only get the small buttons
            yDiff in 0..300 && rect.width() < 300
        }

        if (alignedNodes.size < 2) {
            Log.e(TAG, "Could not find + and - buttons. Dumping hierarchy of parent:")
            var dumpParent = chargeLimitNode.parent
            for (i in 0..3) {
                if (dumpParent?.parent != null) dumpParent = dumpParent.parent
            }
            dumpNodeHierarchy(dumpParent)
            throw Exception("SOC調整用の「＋」「ー」ボタンが見つかりませんでした (見つかった数: ${alignedNodes.size})")
        }

        // Sort by X coordinate to identify - (left) and + (right)
        val rect = android.graphics.Rect()
        val sortedNodes = alignedNodes.sortedBy { 
            it.getBoundsInScreen(rect)
            rect.left 
        }
        
        val minusButton = sortedNodes.first()
        val plusButton = sortedNodes.last()
        
        val diff = targetSoc - currentSoc
        
        if (diff > 0) {
            for (i in 0 until diff) {
                plusButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Log.d(TAG, "Clicked +")
                delay(150) // Safety Measure 2
            }
        } else if (diff < 0) {
            for (i in 0 until -diff) {
                minusButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Log.d(TAG, "Clicked -")
                delay(150) // Safety Measure 2
            }
        }
    }

    private fun returnMacroResult(isSuccess: Boolean, errorMessage: String?, preExecSoc: Int = currentSocScraped, preExecMode: String? = currentModeScraped) {
        Log.d(TAG, "Returning result: success=$isSuccess, error=$errorMessage")
        
        // Show Toast for debugging
        val toastMsg = if (isSuccess) "マクロ成功" else "マクロ失敗: $errorMessage"
        // Toast needs to be shown on main thread
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(applicationContext, toastMsg, Toast.LENGTH_LONG).show()
        }

        val intent = Intent(this, Class.forName("com.ghihin.epcubeoptimizer.MainActivity"))
        intent.action = ACTION_MACRO_RESULT
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        intent.putExtra(EXTRA_IS_SUCCESS, isSuccess)
        intent.putExtra(EXTRA_TARGET_SOC_RESULT, targetSoc)
        intent.putExtra(EXTRA_PRE_EXEC_SOC, preExecSoc)
        if (preExecMode != null) {
            intent.putExtra(EXTRA_PRE_EXEC_MODE, preExecMode)
        }
        
        if (errorMessage != null) {
            intent.putExtra(EXTRA_ERROR_MESSAGE, errorMessage)
        }
        startActivity(intent)
        
        // AutomationOrchestrator が BroadcastReceiver で受信できるようにブロードキャストも送信
        val broadcastIntent = Intent(ACTION_MACRO_RESULT).apply {
            putExtra(EXTRA_IS_SUCCESS, isSuccess)
            putExtra(EXTRA_TARGET_SOC_RESULT, targetSoc)
            putExtra(EXTRA_PRE_EXEC_SOC, preExecSoc)
            if (preExecMode != null) {
                putExtra(EXTRA_PRE_EXEC_MODE, preExecMode)
            }
            if (errorMessage != null) {
                putExtra(EXTRA_ERROR_MESSAGE, errorMessage)
            }
            setPackage(applicationContext.packageName) // 明示的に自身に制限
        }
        sendBroadcast(broadcastIntent)
        
        currentState = MacroState.IDLE
        
        // Reset scraped values for next run
        currentSocScraped = -1
        currentModeScraped = null
    }
}

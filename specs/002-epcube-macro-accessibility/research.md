# Research: EP CUBE Macro Accessibility

## 1. AccessibilityServiceを用いた他アプリのUI操作

**Decision**: `AccessibilityService` を継承したサービスクラスを実装し、`AccessibilityEvent` の監視と `AccessibilityNodeInfo` の探索・操作を行う。

**Rationale**:
- EP CUBEアプリには外部APIがないため、UIを直接操作するしかない。
- 座標タップは端末依存（解像度、アスペクト比）で壊れやすいため、Android標準のユーザー補助機能APIを用いて、テキストやViewIDベースで要素を特定し、`ACTION_CLICK` や `ACTION_SCROLL` などのアクセシビリティアクションを発行するのが最も確実（Pragmatism, Resilience）。

**Alternatives considered**:
- `adb shell input tap` コマンド: root権限またはPC接続が必要であり、単体スマホでの自動化には不向き。
- 画像認識（OpenCV等）によるタップ: 実装が複雑になり、バッテリー消費も激しいため不採用。

## 2. UI要素の特定と待機処理（リトライ・タイムアウト）

**Decision**: Kotlin Coroutinesの `delay` と `withTimeout` を活用し、目的の `AccessibilityNodeInfo` が見つかるまで一定間隔（例: 500ms）で画面ツリー（`rootInActiveWindow`）をポーリング検索する仕組みを実装する。

**Rationale**:
- 画面遷移や通信によるローディング遅延があるため、イベント駆動（`onAccessibilityEvent`）だけで状態遷移を管理すると、イベントの取りこぼしや順序の逆転でステートマシンが破綻しやすい。
- コルーチンを用いたポーリング方式であれば、「画面Aを待機」→「ボタンAをクリック」→「画面Bを待機」という直感的なシーケンスを同期的に記述でき、可読性と堅牢性が高まる（Resilience）。

**Alternatives considered**:
- `onAccessibilityEvent` 内での複雑なステートマシン実装: 状態管理が煩雑になり、バグの温床になりやすいため回避。

## 3. スライダーまたは「＋」「ー」ボタンによる値の変更

**Decision**: 「充電上限」の現在値をテキストノードから読み取り、目標値との差分を計算。差分が0になるまで「＋」または「ー」ボタンのノードを特定して `ACTION_CLICK` を繰り返し発行する。

**Rationale**:
- スライダー（`SeekBar`）の直接操作（`ACTION_SET_PROGRESS`）は、アプリ側の実装によってはサポートされていない場合がある。
- 「＋」「ー」ボタンのタップであれば、確実に1ステップずつ値を変更でき、現在値の再確認と組み合わせることで正確な設定が可能（Safety First）。

**Alternatives considered**:
- スライダーの座標計算によるタップ: 端末依存のため却下。

## 5. 完了判定と元のアプリ（EPCubeOptimizer）への復帰

**Decision**: 「設定」ボタン押下後、20秒間待機してから再度「充電上限」の値を読み取り、目標値と一致していれば成功とみなす。その後、左上の「戻る」ボタンを押してホーム画面に戻り、EPCubeOptimizerのMainActivityを起動する明示的なIntentを発行してフォアグラウンドに戻す。

**Rationale**:
- 「成功しました。」のトースト通知はAndroidシステム側で表示されるため、AccessibilityServiceからの検知が不安定になりやすい。
- 画面上の設定値を直接再確認することで、通信の成功・失敗に左右されず、最も信頼性の高い完了判定となる（Safety First, Resilience）。
- ユーザーの介入なしに全自動で処理を完結させるため（Pragmatism）。
- 処理結果（成功・失敗）をIntentのExtraに含めて渡すことで、Optimizer側で結果を記録・通知できる（Observability）。

**Alternatives considered**:
- トースト通知（`TYPE_NOTIFICATION_STATE_CHANGED`）の検知: 検知漏れが発生しやすく、タイムアウトエラーの温床となったため不採用。
- 「ローディング」ダイアログの検知: 通信環境によって表示時間が大きく変動し、状態管理が複雑になるため不採用。

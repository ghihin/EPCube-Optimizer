# トラブルシューティングと開発ガイド

このドキュメントは、EPCube-Optimizerプロジェクトの開発中に発生した問題とその解決策、および開発環境のセットアップに関する重要な情報を記録したものです。

## 1. Gradleビルドとテストの実行に関する問題

### 1.1. `gradle` または `gradlew` コマンドが見つからない
**症状**: ターミナルで `gradle test` や `./gradlew test` を実行すると、「コマンドが見つかりません」というエラーが発生する。
**原因**: 
- システムにGradleがインストールされていない、または環境変数（PATH）が設定されていない。
- プロジェクト内にGradle Wrapper（`gradlew`, `gradlew.bat`, `gradle/` フォルダ）が生成されていない。
**解決策**:
1. Android Studioでプロジェクトフォルダを開く。
2. Android Studioが自動的にGradleの同期を行い、プロジェクトルートに `gradlew` などの必要なファイルを生成するのを待つ。
3. 生成された `./gradlew test` を使用してテストを実行する。

### 1.2. `Incompatible Gradle JVM version` エラー
**症状**: Android Studioでの同期時やビルド時に「The project's Gradle version X.X is incompatible with the Gradle JVM version XX」というエラーが発生する。
**原因**: プロジェクトで指定されているGradleのバージョンと、Android Studioが使用しているJava（JDK）のバージョンに互換性がない。
**解決策**:
- Android Studioのエラーメッセージに表示される青いリンク「Apply compatible Gradle JDK configuration and sync」をクリックし、互換性のあるJDK（通常はJDK 17）を自動適用させる。

### 1.3. `Configuration ':app:debugRuntimeClasspath' contains AndroidX dependencies...` エラー
**症状**: ビルド時にAndroidX関連の依存関係エラーが発生する。
**原因**: プロジェクトでJetpack ComposeなどのAndroidXライブラリを使用しているが、プロジェクト設定でAndroidXが有効になっていない。
**解決策**:
プロジェクトのルートディレクトリに `gradle.properties` ファイルを作成（または編集）し、以下の設定を追加する。
```properties
android.useAndroidX=true
android.enableJetifier=true
```

### 1.4. `AAPT: error: resource mipmap/ic_launcher not found` エラー
**症状**: `processDebugMainManifest` タスクなどで、アプリアイコンが見つからないというエラーが発生する。
**原因**: `AndroidManifest.xml` で `@mipmap/ic_launcher` などのアイコンを指定しているが、実際の画像ファイルがプロジェクト内に存在しない。
**解決策**:
- 開発初期段階（UIやリソースが揃っていない状態）でテストのみを実行したい場合は、一時的に `AndroidManifest.xml` から `android:icon` と `android:roundIcon` の指定を削除する。

### 1.5. 外部ターミナル（VSCode等）から `./gradlew` を実行する際の `JAVA_HOME` エラー
**症状**: VSCodeのターミナルなどで `./gradlew test` を実行すると、「ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.」というエラーが発生する。
**原因**: 外部ターミナルがJava（JDK）のインストール場所を認識できていない。
**解決策**:
- **推奨**: Android Studioに内蔵されているターミナル（画面下部の「Terminal」タブ）を使用する。ここには自動的に適切な環境変数が設定されている。
- **代替案**: 外部ターミナルで実行する前に、一時的に環境変数 `JAVA_HOME` をAndroid Studio内蔵のJDKパスに設定する。
  - PowerShellの例: 
    ```powershell
    $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
    ./gradlew test
    ```
  - または、1行で実行する場合:
    ```powershell
    powershell -Command "$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'; ./gradlew test"
    ```

### 1.6. AIエージェントが `JAVA_HOME` をPowerShellやcmdで設定しても `gradlew.bat` が失敗する
**症状**: AIエージェント（Antigravity等）が以下のようなコマンドを実行しても `ERROR: JAVA_HOME is set to an invalid directory` で失敗する。
```powershell
# 失敗例1: powershell -Command 内での環境変数設定（$が展開されてしまう）
powershell -Command "$env:JAVA_HOME = 'C:\Program Files\...\jbr'; ./gradlew test"

# 失敗例2: cmd /c "set "JAVA_HOME=..." && gradlew.bat"（ダブルクォートのネストによりパスが切れる）
cmd /c "set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr" && gradlew.bat test"
```
**原因**:
- PowerShellの `powershell -Command "..."` 内では `$` が変数展開され、`$env:JAVA_HOME` が意図どおりに解釈されない。
- `cmd /c "set "KEY=value with spaces" && ..."` はダブルクォートのネストを正しく処理できず、スペースを含むパスが途中で切れる。

**✅ 恒久解決策（適用済み）**: `gradlew.bat` の先頭に `if not defined JAVA_HOME set JAVA_HOME=...` を追記することで、外部から`JAVA_HOME`を設定しなくても `cmd /c gradlew.bat test` が単独で動作するようになった。詳細は「2. 今後のテスト実行手順」を参照。

> **補足（AIエージェント向け）**: 上記の恒久解決策が適用されていない環境では、バッチファイルを一時作成する回避策が有効。
> ```bat
> @echo off
> set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
> call gradlew.bat test 2>&1
> ```
> `set KEY=value`（引用符なし）はcmd内でスペース含むパスを正しく設定できる。

---

## 2. 今後のテスト実行手順

AIエージェント（Roo、Antigravity等）にテストの実行を依頼する場合、または手動で実行する場合は、以下の手順を推奨します。

### AIエージェント・外部シェルから実行（推奨）
`gradlew.bat` に `JAVA_HOME` のデフォルト値を設定済みのため、以下のコマンドを実行するだけでテストが動く:
```
cmd /c gradlew.bat test
```

### Android Studio のターミナルから実行
```bash
./gradlew test
```

### VSCode ターミナル（PowerShell）から実行
PowerShell内で2行に分けて実行する（1行の `-Command` 構文は避ける）:
```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat test
```

### 結果の確認
テストが完了すると、以下のパスにHTMLレポートが生成されるので、ブラウザで開いて結果を確認する。
- `app/build/reports/tests/testDebugUnitTest/index.html`
- `app/build/reports/tests/testReleaseUnitTest/index.html`


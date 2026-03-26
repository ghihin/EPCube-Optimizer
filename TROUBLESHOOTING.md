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

## 2. 今後のテスト実行手順

AIエージェント（Roo等）にテストの実行を依頼する場合、または手動で実行する場合は、以下の手順を推奨します。

1. **Android Studioのターミナルを使用する**: 環境変数の問題を避けるため、Android Studio下部の「Terminal」タブを開く。
2. **コマンドの実行**: 以下のコマンドを実行してユニットテストを走らせる。
   ```bash
   ./gradlew test
   ```
3. **VSCodeのターミナルから実行する場合**:
   ```powershell
   powershell -Command "$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'; ./gradlew test"
   ```
4. **結果の確認**: テストが完了すると、以下のパスにHTMLレポートが生成されるので、ブラウザで開いて結果を確認する。
   - `app/build/reports/tests/testDebugUnitTest/index.html` (または `testReleaseUnitTest/index.html`)

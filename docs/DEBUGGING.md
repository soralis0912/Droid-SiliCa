# デバッグとテスト手順 (Debugging and Testing Guide)

このドキュメントでは、Droid-SilliCaアプリのデバッグとテスト手順について説明します。

## エミュレータでのテスト (Testing with Emulator)

### 前提条件 (Prerequisites)

1. Android SDKがインストールされていること
2. `ANDROID_SDK_ROOT` または `ANDROID_HOME` 環境変数が設定されていること
3. ネットワーク接続が利用可能であること

### エミュレータのセットアップ (Emulator Setup)

#### 1. 必要なコンポーネントのインストール

```bash
# SDKマネージャーを使用してコンポーネントをインストール
export PATH=$PATH:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin

# エミュレータとシステムイメージをインストール
sdkmanager "emulator" "platform-tools" "system-images;android-30;default;x86_64"
```

#### 2. AVD（Android Virtual Device）の作成

```bash
# AVDを作成
avdmanager create avd -n test_avd -k "system-images;android-30;default;x86_64" -d "pixel"
```

#### 3. エミュレータの起動

```bash
export ANDROID_AVD_HOME=$HOME/.android/avd
export PATH=$PATH:$ANDROID_SDK_ROOT/emulator:$ANDROID_SDK_ROOT/platform-tools

# ヘッドレスモードで起動（ハードウェアアクセラレーション無し）
emulator @test_avd -no-window -no-audio -no-boot-anim -gpu off -no-accel &

# 起動完了を待つ
adb wait-for-device
```

#### 4. ブート完了の確認

```bash
# ブート完了を確認（最大5分待機）
for i in {1..30}; do 
  BOOT=$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
  if [ "$BOOT" = "1" ]; then
    echo "Boot completed!"
    break
  fi
  echo "Waiting for boot... attempt $i/30"
  sleep 10
done
```

### ANR（Application Not Responding）の回避

エミュレータがハードウェアアクセラレーション無しで動作する場合、ANRダイアログが頻繁に表示されます。以下の設定で軽減できます：

#### アニメーションの無効化

```bash
# 開発者オプションを有効化
adb shell settings put global development_settings_enabled 1

# アニメーションを無効化してパフォーマンスを向上
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
```

#### ANR設定の調整

```bash
# ANR警告を無効化（root権限が必要な場合があります）
adb shell settings put global anr_show_background false
adb shell settings put global anr_debugging_mechanism 0
```

#### ANRダイアログが表示された場合

```bash
# 「Wait」ボタンをタップ（座標は画面解像度に依存）
adb shell input tap 540 1050

# または「Back」キーで閉じる
adb shell input keyevent BACK
```

### アプリのビルドとインストール

#### 1. デバッグキーストアの作成

```bash
cd app
keytool -genkey -v -keystore android.jks \
  -storepass android -alias android -keypass android \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=Android Debug,O=Android,C=US"
```

#### 2. APKのビルド

```bash
./gradlew assembleDebug
```

#### 3. アプリのインストール

```bash
adb install app/build/outputs/apk/debug/app-debug.apk

# 既にインストールされている場合は再インストール
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### アプリの起動とテスト

#### アプリの起動

```bash
# Mainアクティビティを起動
adb shell am start -n org.soralis.droidsillica/.ui.MainActivity

# または monkey を使用
adb shell monkey -p org.soralis.droidsillica -c android.intent.category.LAUNCHER 1
```

#### UI操作

```bash
# タップ操作（座標指定）
adb shell input tap X Y

# スワイプ操作
adb shell input swipe X1 Y1 X2 Y2

# テキスト入力
adb shell input text "テキスト"

# キー入力
adb shell input keyevent KEYCODE_BACK
```

### スクリーンショットの取得

```bash
# スクリーンショットを撮影
adb shell screencap -p /sdcard/screenshot.png

# PCにダウンロード
adb pull /sdcard/screenshot.png ./screenshot.png
```

### UI階層の確認

```bash
# UI階層をダンプ
adb shell uiautomator dump

# ダンプファイルを取得
adb pull /sdcard/window_dump.xml ./
```

## 実機でのテスト (Testing on Real Device)

### USBデバッグの有効化

1. 設定 → デバイス情報 → ビルド番号を7回タップ
2. 開発者オプションが有効になる
3. 開発者オプション → USBデバッグを有効化

### デバイスの接続確認

```bash
# 接続されているデバイスを確認
adb devices

# デバイスが表示されない場合
adb kill-server
adb start-server
adb devices
```

## トラブルシューティング (Troubleshooting)

### エミュレータが起動しない

1. KVM権限の確認（Linux）:
   ```bash
   ls -la /dev/kvm
   sudo usermod -aG kvm $USER
   ```

2. ディスク容量の確認:
   ```bash
   df -h
   ```

3. メモリの確認:
   ```bash
   free -h
   ```

### ビルドが失敗する

1. Gradleキャッシュのクリア:
   ```bash
   ./gradlew clean
   rm -rf ~/.gradle/caches/
   ```

2. Android SDKの更新:
   ```bash
   sdkmanager --update
   ```

### アプリがクラッシュする

1. ログの確認:
   ```bash
   adb logcat | grep -i "droidsillica"
   ```

2. クラッシュログの確認:
   ```bash
   adb logcat *:E
   ```

## CI/CDでのテスト (Testing in CI/CD)

GitHub Actions でのテスト実行については、`.github/workflows/` を参照してください。

### エミュレータのキャッシュ

CI環境でエミュレータの起動を高速化するため、システムイメージとAVDをキャッシュします：

```yaml
- name: Cache Android Emulator
  uses: actions/cache@v3
  with:
    path: |
      ~/.android/avd
      ~/.android/adb*
    key: avd-${{ runner.os }}-${{ hashFiles('**/avd-config') }}
```

### タイムアウト設定

エミュレータの起動とブートには時間がかかるため、適切なタイムアウトを設定します：

```yaml
- name: Run tests
  timeout-minutes: 30
```

## 参考資料 (References)

- [Android Debug Bridge (adb)](https://developer.android.com/studio/command-line/adb)
- [Android Emulator](https://developer.android.com/studio/run/emulator)
- [UI Automator](https://developer.android.com/training/testing/ui-automator)

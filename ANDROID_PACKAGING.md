# Android 打包说明

这个项目已经包含 Android 原生应用工程：`android-app/`。

## 推荐方式：Android Studio

1. 打开 Android Studio。
2. 选择 `Open`。
3. 打开项目中的 `android-app` 目录。
4. 等待 Gradle Sync 完成。
5. 连接安卓手机，并开启 USB 调试。
6. 点击 Run 安装测试。
7. 需要导出 APK 时，选择：

```text
Build > Build Bundle(s) / APK(s) > Build APK(s)
```

生成的调试 APK 通常在：

```text
android-app/app/build/outputs/apk/debug/app-debug.apk
```

## 命令行构建

在 Windows 终端进入 `android-app` 目录：

```powershell
cd "C:\Users\Administrator\Desktop\New project\New project\android-app"
.\gradlew.bat assembleDebug
```

构建前需要本机已经安装 Android SDK。Android Studio 通常会自动安装和配置。

## 语音识别说明

当前 Android 原生版主要使用百度实时语音识别：

- App 设置页填写 `AppID` 和 `API Key`。
- 点击“开始”后，应用会通过 WebSocket 上传麦克风 PCM 音频。
- 百度返回识别文字后，应用会匹配稿件并推进高亮。

代码里没有内置真实百度密钥。开源时请继续保持为空，不要把个人密钥提交到 GitHub。

## 哪些文件不应上传

这些文件已经被 `.gitignore` 排除：

```text
android-app/.gradle/
android-app/build/
android-app/app/build/
android-app/local.properties
*.apk
*.aab
```

`gradle/wrapper/gradle-wrapper.jar` 是 Gradle Wrapper 的一部分，可以保留在仓库中。

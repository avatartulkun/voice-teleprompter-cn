# 语音跟读提词器

一个面向演讲、直播、录课和口播练习的语音跟读提词器。项目包含 Android 原生应用和早期 Web 原型，核心功能是识别朗读内容，并把稿件中已经读过的文字自动高亮。

## 功能

- Android 原生提词器界面
- 中文语音识别跟读
- 已读文字变色高亮
- 当前朗读位置提示
- 自动滚动和手动前进/后退
- 稿件编辑和本地保存
- 字号、颜色、背景色、滚动速度设置
- 横屏/全屏提词模式
- 早期浏览器版原型，可直接打开 `index.html` 体验

## 项目结构

```text
.
├── android-app/              # Android 原生应用工程
│   ├── app/src/main/java/    # 主要 Java 代码
│   ├── app/src/main/res/     # 文案、颜色、图标等资源
│   └── gradlew.bat           # Windows 下的 Gradle 构建脚本
├── index.html                # Web 原型页面
├── app.js                    # Web 原型逻辑
├── styles.css                # Web 原型样式
├── ANDROID_PACKAGING.md      # Android 打包说明
├── LICENSE                   # MIT 开源协议
└── .gitignore                # Git 忽略规则
```

## Android 使用说明

Android 原生版位于 `android-app/`。

1. 用 Android Studio 打开 `android-app` 目录。
2. 等待 Gradle Sync 完成。
3. 连接安卓手机，并开启 USB 调试。
4. 点击 Android Studio 的 Run 按钮安装测试。
5. 在 App 中进入“设置”，填写自己的百度语音识别 `AppID` 和 `API Key`。
6. 返回首页，进入提词后点击“开始”朗读。

当前 Android 版的“开始”按钮默认使用百度实时语音识别。仓库不会内置任何真实密钥，使用者需要在 App 设置里自行填写。

## 构建 APK

可以用 Android Studio 菜单构建：

```text
Build > Build Bundle(s) / APK(s) > Build APK(s)
```

也可以在 Windows 终端进入 `android-app` 后构建：

```powershell
.\gradlew.bat assembleDebug
```

生成的调试 APK 通常在：

```text
android-app/app/build/outputs/apk/debug/app-debug.apk
```

`app-debug.apk`、`build/`、`.gradle/`、`local.properties` 等本机文件已经在 `.gitignore` 中排除，不建议上传到 GitHub。

## Web 原型

根目录下的 `index.html` 是早期 Web 原型，可以直接用浏览器打开。由于浏览器语音识别和麦克风权限限制，建议用本地服务访问：

```powershell
python -m http.server 4173 --bind 127.0.0.1
```

然后访问：

```text
http://127.0.0.1:4173
```

Web 原型依赖浏览器的 Web Speech API，在不同浏览器和 Android WebView 中支持情况不稳定。正式安卓使用建议以 `android-app/` 原生版本为准。

## 安全提醒

- 不要把百度 `AppID`、`API Key`、`Secret Key` 写死到代码里。
- 不要提交 `local.properties`，它包含本机 Android SDK 路径。
- 不要提交 `build/` 目录或 APK 文件，它们是本地生成产物。

## 开源协议

本项目使用 MIT License，详见 `LICENSE`。

# 语音跟读提词器 Android 版

一款面向中文用户的语音跟读提词器，支持语音识别、已读文字自动高亮、横屏提词、自动滚动和稿件练习，适合演讲、录课、短视频口播、直播话术和朗读训练。

## 主要功能

- 中文稿件编辑与保存
- 语音跟读识别
- 已读文字自动高亮
- 当前朗读位置提示
- 横屏/竖屏提词
- 全屏提词模式
- 自动滚动
- 麦克风测试
- 云端语音识别密钥配置
- 字号、颜色、滚动速度和变色速度设置

## 使用场景

- 中文演讲提词
- 短视频口播练习
- 直播话术跟读
- 录课提词
- 普通话朗读训练
- 稿件背诵辅助

## Android APK 下载

请在本仓库的 Releases 页面下载最新 APK：

[Releases](https://github.com/avatartulkun/voice-teleprompter-cn/releases)

## 当前版本

最新测试版本：

```text
v1.2.0
```

本次更新在已有完整工程基础上，优化了 Android 端首页和提词模式界面，调整了设置、麦克风测试、稿件导入和进入提词的操作流程，并完善了实时语音识别配置说明。

## 语音识别配置

应用支持配置云端语音识别服务密钥。首次使用前，请在应用内进入“设置识别密钥”，填写对应服务的：

```text
AppID
API Key
```

如果还没有开通实时语音识别服务，可以参考开通说明中的语音开通步骤：

```text
docs/实时语音识别开通说明.md
```

## 本地开发

Android 工程目录：

```text
android-app/
```

使用 Android Studio 打开该目录，等待 Gradle Sync 完成后即可运行或构建 APK。

Debug APK 常见输出位置：

```text
android-app/app/build/outputs/apk/debug/voice-teleprompter-cn-android-v1.2.0.apk
```

## 注意事项

- 请不要把真实语音识别密钥提交到 GitHub。
- `app/build/`、`.gradle/`、`.idea/` 等构建缓存不建议提交。
- 当前 APK 为测试版，适合功能测试和个人使用。

## 许可证

本项目仅允许非商业用途使用，基于 PolyForm Noncommercial License 1.0.0 授权。

未经作者书面许可，不允许将本项目用于商业用途、商业产品、付费服务或商业分发。

完整协议请查看 [LICENSE](LICENSE)。

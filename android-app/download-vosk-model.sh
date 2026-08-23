#!/bin/bash
# 下载 Vosk 中文小模型并解压到 assets 目录，供离线语音识别使用。
# 模型来源：https://alphacephei.com/vosk/models （Apache-2.0 兼容）
# 模型体积约 42MB，打包进 APK 后 APK 体积会相应增大。
set -e

ASSETS_DIR="app/src/main/assets"
MODEL_DIR="$ASSETS_DIR/vosk-model-small-cn"
URL="https://alphacephei.com/vosk/models/vosk-model-small-cn-0.22.zip"
TMP_ZIP="/tmp/vosk-model-small-cn.zip"
TMP_UNZIP="/tmp/vosk-model-small-cn-extract"

if [ -d "$MODEL_DIR" ] && [ -f "$MODEL_DIR/conf/model.conf" ]; then
  echo "模型已存在：$MODEL_DIR，跳过下载。如需重新下载，请先删除该目录。"
  exit 0
fi

echo "正在下载 Vosk 中文小模型（约 42MB）..."
curl -L --fail -o "$TMP_ZIP" "$URL"

echo "正在解压..."
rm -rf "$TMP_UNZIP"
mkdir -p "$TMP_UNZIP"
unzip -q "$TMP_ZIP" -d "$TMP_UNZIP"

# 解压出的目录名为 vosk-model-small-cn-0.22，统一重命名为代码引用的 vosk-model-small-cn
rm -rf "$MODEL_DIR"
mkdir -p "$ASSETS_DIR"
mv "$TMP_UNZIP/vosk-model-small-cn-0.22" "$MODEL_DIR"

rm -rf "$TMP_UNZIP" "$TMP_ZIP"

echo "完成。模型已放入：$MODEL_DIR"
echo "现在可以用 Android Studio 重新构建 APK，并在设置里勾选“离线模式”测试。"

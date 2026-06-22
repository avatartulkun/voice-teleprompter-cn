# 上传到 GitHub 的步骤

这份指南假设项目已经在本地整理好，并且当前目录是：

```text
C:\Users\Administrator\Desktop\New project\New project
```

## 1. 在 GitHub 新建仓库

1. 打开 GitHub。
2. 点击新建仓库。
3. 仓库名可以填写：

```text
voice-teleprompter-android
```

4. 选择 `Public`。
5. 不要勾选自动添加 README、LICENSE、.gitignore，因为项目里已经有了。
6. 创建仓库后，复制 GitHub 给你的 HTTPS 地址，类似：

```text
https://github.com/你的用户名/voice-teleprompter-android.git
```

## 2. 配置提交身份

第一次使用 Git 时，需要设置你的名字和邮箱。把下面的内容替换成你自己的：

```powershell
git config --global user.name "你的名字"
git config --global user.email "你的邮箱"
```

如果你不想公开邮箱，可以在 GitHub 账号设置里找到 GitHub 提供的 no-reply 邮箱。

## 3. 提交本地项目

进入项目目录后执行：

```powershell
git commit -m "Initial open source release"
```

## 4. 连接 GitHub 仓库并上传

把下面的仓库地址替换成你自己的 GitHub 仓库地址：

```powershell
git remote add origin https://github.com/你的用户名/voice-teleprompter-android.git
git push -u origin main
```

如果提示登录，按 GitHub 弹出的浏览器授权流程完成即可。

## 5. 上传后检查

打开 GitHub 仓库页面，确认能看到：

- `README.md`
- `LICENSE`
- `android-app/`
- `index.html`
- `app.js`
- `styles.css`

不要上传这些本机文件：

- `android-app/local.properties`
- `android-app/.gradle/`
- `android-app/.idea/`
- `android-app/build/`
- `android-app/app/build/`
- `app-debug.apk`

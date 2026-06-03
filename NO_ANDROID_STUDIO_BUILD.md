# 不安装 Android Studio 的 APK 构建方法

## 方法一：GitHub Actions 云端构建（推荐）

1. 登录 GitHub，新建一个空仓库，例如 `PonySolver`。
2. 把本工程文件上传到仓库根目录。仓库根目录下应直接看到：
   - `app/`
   - `build.gradle`
   - `settings.gradle`
   - `.github/workflows/build-apk.yml`
3. 进入仓库页面的 `Actions`。
4. 选择 `Build Android APK`。
5. 点击 `Run workflow`。
6. 等待构建完成。
7. 打开构建记录，在页面底部 `Artifacts` 下载 `PonySolver-debug-apk`。
8. 解压后得到 `app-debug.apk`，发送到 vivo 手机安装。

注意：第一次安装调试 APK 时，vivo 可能会提示“未知来源应用”。只给文件管理器或浏览器临时授权即可，安装完成后可以关闭。

## 方法二：本地命令行构建

该方法不需要 Android Studio 图形界面，但仍需要安装：

- JDK 17
- Android SDK Command-line Tools
- Gradle 8.x

进入工程根目录后执行：

```bash
gradle assembleDebug --no-daemon
```

生成 APK：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 方法三：我不建议的方式

不建议把工程上传到来路不明的“在线打包 APK”网站，因为工程和 APK 都可能被二次篡改。

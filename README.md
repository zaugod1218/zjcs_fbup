# 杖剑传说自动副本 - Android APK

通过 USB 连接电脑自动编译 APK，或直接在 GitHub 上编译。

## 快速获取 APK

1. 把此项目上传到 GitHub（新建仓库，push 上去）
2. 进入 GitHub 仓库 → Actions 标签页 → 自动开始编译
3. 编译完成后，在 Actions 页面下载 `app-debug.apk`
4. 手机打开下载的 APK 安装（可能需要允许"安装未知来源应用"）

## 首次使用

1. 打开 App → 提示开启**无障碍服务** → 去系统设置中开启
2. 点击**采集模板** → 授权**截屏权限**
3. 按顺序采集 7 个按钮模板（在游戏界面中点击按钮位置）
4. 采集完成后点击**开始运行**

## 手动编译（有 Android Studio 时）

```bash
./gradlew assembleDebug
```

APK 在 `app/build/outputs/apk/debug/app-debug.apk`

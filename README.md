# xxa-notepad

一个轻量原生 Android 记事本应用，纯 Java + XML 手写，不依赖任何第三方库，可直接在 AIDE（手机）或命令行 aapt2 + javac + d8 编译。

## 功能

- **普通记事**：自动记录系统时间、编辑内容、标签胶囊（可横滑、可删）、向下插入、删除进回收站
- **回收站**：误删恢复、多选永久删除
- **私密记录**：独立加密分区，支持数字密码 / 九宫格图案密码（仅存 SHA-256 摘要），数据与普通记事完全分开存储，删除即永久删除
- **设置**：修改密码（先验旧密码）、重置密码（二次确认，清空私密记录）
- **更新日志**：应用内查看版本更新记录
- **记事附图/附件**：底部「+」按钮可从相册选图、拍照，或上传任意文件；图片与文件随记事一起保存，文件条显示文件名、修改日期与大小
- **应用内更新**：双击抽屉里的版本号卡片，自动检查 GitHub 最新 Release 并下载安装

## 截图

待补充（首页 / 抽屉 / 私密记录页）。

## 工程结构（AIDE 旧模板）

```
├── AndroidManifest.xml
├── res/
│   ├── layout/        各页面布局
│   ├── drawable/      圆角背景、标签样式
│   ├── values/        strings / styles
│   └── raw/changelog.txt   内置更新日志
└── src/com/example/notebook/   全部 Java 源码
```

## 编译

### 方式一：手机 AIDE
新建「新的 Android App」工程，包名 `com.example.notebook`，按工程结构逐文件粘贴，点 Run 即可。

### 方式二：命令行（build-tools）
```bash
aapt2 compile --dir res -o compiled.zip
aapt2 link -o base.apk -I android.jar --manifest AndroidManifest.xml \
  --java gen --min-sdk-version 21 --target-sdk-version 34 \
  --version-code 18 --version-name "4.8.5" compiled.zip
javac --release 8 -cp android.jar -d classes gen/... R.java src/**/*.java
d8 --lib android.jar --output dexout $(find classes -name "*.class")
# 打包 classes.dex → zipalign → apksigner sign
```

最低支持 Android 5.0（API 21），目标 SDK 34。

## 数据位置

- 普通记事：`/data/data/com.example.notebook/history/notebook_history.txt`
- 密码摘要：`/data/data/com.example.notebook/password/password.dat`
- 私密记录：`/data/data/com.example.notebook/private/private_notes.txt`

全部数据只存在本机，不上传任何网络。

## 开源协议

[MIT License](LICENSE)，Copyright (c) 2026 xxa。

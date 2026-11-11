# goJapan Android

Kotlin + Jetpack Compose 客户端。最低 Android 8.0（API 26），当前编译与目标 API 36。

## 行程规划（DeepSeek + Google Maps）

应用底部的“行程”入口会读取 `app/src/main/assets/trips/xiaohongshu_tokyo_guide.json`，并将用户编辑的攻略和生成结果存入本机 DataStore。首次启动无需联网即可查看预置东京路线。

在 `local.properties` 中加入以下配置（该文件已被 git 忽略）：

```properties
MAPS_API_KEY=你的_Google_Maps_SDK_Android_密钥
DEEPSEEK_API_KEY=你的_DeepSeek_API_密钥
DEEPSEEK_BASE_URL=https://api.deepseek.com
DEEPSEEK_MODEL=deepseek-flash
```

Google Cloud 项目需启用 **Maps SDK for Android**，并将密钥限制为 Android 应用 `com.gojapan` 及本机签名证书。未配置地图密钥时行程列表仍可用，并提供跳转 Google Maps 的按钮。

> 当前为本地原型，DeepSeek 密钥会进入调试 APK，可被逆向提取。正式发布时应让 Android 客户端请求自有后端，由后端保管大模型密钥、做用户鉴权、限流和费用控制。

## 本地验证

```bash
./gradlew lintDebug testDebugUnitTest assembleDebug compileReleaseKotlin
```

M0 包含单 Activity 应用壳、固定顺序的四个主 Tab、KANA LINK 占位首页、手机底部导航和平板侧边导航。业务数据、游戏和 Room 从 M1 开始实现。

## 固定发布签名

发布必须使用 [SIGNING.md](SIGNING.md) 中登记的固定证书和签名构建脚本；release 不再允许临时签名或无签名发布。私钥与密码不得提交到仓库。

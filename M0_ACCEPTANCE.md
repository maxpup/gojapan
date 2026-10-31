# KANA LINK M0 验收记录

对应里程碑：`TASK_BREAKDOWN.md` 的 M0。

## 交付范围

- Android 目录与官方 Compose 工程骨架；
- `minSdk 26`、`compileSdk/targetSdk 36`；
- 单 Activity goJapan 应用壳；
- “学日语、KANA LINK、搜索、行程”四个固定一级入口；
- 手机使用底部导航，宽度达到 600dp 时使用侧边导航；
- KANA LINK 默认高亮并展示占位首页；
- 其他三个模块展示占位状态；
- 单元测试、Compose UI 测试和 GitHub Actions 验证工作流。

## 设计基线

- 使用 Material 3 导航组件，触控目标不低于 48dp；
- 主色使用日式朱红，颜色由主题集中管理；
- 页面不依赖颜色单独表达导航选中状态；
- M0 不创建学习数据库，不伪造进度数据；
- KANA LINK 使用 `feature/kanalink` 包边界，后续页面接入嵌套导航。

## 验收命令

```bash
cd android
./gradlew lintDebug testDebugUnitTest assembleDebug assembleRelease
```

存在模拟器或真机时运行 UI 测试：

```bash
./gradlew connectedDebugAndroidTest
```

## 验收结果（2026-08-23）

| 检查项 | 结果 | 证据 |
|---|---|---|
| JDK | 通过 | Eclipse Temurin 17.0.20.1 |
| Android 编译环境 | 通过 | compileSdk/targetSdk 36，minSdk 26 |
| Kotlin/Compose 编译 | 通过 | Debug、Release 均完成 Kotlin 编译 |
| JVM 单元测试 | 通过 | `testDebugUnitTest` |
| Android Lint | 通过 | `lintDebug`，HTML 报告已生成 |
| Debug APK | 通过 | `app-debug.apk`，applicationId 为 `com.gojapan` |
| Release APK | 通过 | `app-release-unsigned.apk` |
| 启动入口 | 通过 | `com.gojapan.MainActivity` 可从 Launcher 启动 |
| 模拟器 UI 测试 | 通过 | `medium_phone(AVD)`，2/2 通过、0 跳过、0 失败 |

构建命令最终结果：`BUILD SUCCESSFUL`，97 个任务中 34 个执行、63 个复用。

设备自动化验收已覆盖四 Tab 显示、KANA LINK 默认高亮，以及切换至“搜索”后展示对应占位页面。

# M4 发布候选验收记录

日期：2026-08-30  
范围：E12 自适应/无障碍、E13 稳定性/性能/发布

## 已通过

| Story | 结果 | 证据 |
|---|---|---|
| KL-120 | 通过 | 手机尺寸与 `1600×2560@240dpi` 平板尺寸实机检查；平板切换 NavigationRail，核心页面无截断 |
| KL-121 | 通过 | 模拟器 `font_scale=1.5` 检查首页和游戏核心流程，验收后恢复 1.0 |
| KL-122～124 | 通过 | 卡片提供内容和状态语义；选中显示 `●`、提示显示“提示”、完成显示 `✓`；Compose 动画遵循系统动画缩放 |
| KL-125 | 通过 | 简体中文、周一周首日、本地日/月边界有单元测试与界面检查 |
| KL-130 | 通过 | Room v1→v2 MigrationTest 验证学习进度和完整轮次不丢失 |
| KL-131 | 通过（荣耀 ELP-AN00） | Debug 包两次有效冷启动 `TotalTime` 分别为 811ms、697ms，均达到首页首次进入 `<1s` 目标；第三次复用前台实例，不计入样本 |
| KL-037 | 通过 | 启动时执行 SQLite 完整性检查；损坏文件及 WAL/SHM 复制到恢复目录后回退为空历史并提示；定向设备测试 1/1 通过 |
| KL-132 | 通过（模拟器） | 16 个设备测试覆盖快速点击/重复结算等状态；随机交互后日志无 `FATAL EXCEPTION`/GoJapan ANR |
| KL-133 | 通过 | 关闭 Wi‑Fi/移动数据并清空应用数据后，成功加载 104 音、完成 5 对配对、进入结算页 |
| KL-135 | 通过 | `PRIVACY.md` 与实际本地存储、通知权限、导出/删除行为一致；APK 无 INTERNET 权限 |

完整流水线：

```text
./gradlew testDebugUnitTest connectedDebugAndroidTest lintDebug assembleRelease bundleRelease
16/16 connected tests passed
BUILD SUCCESSFUL in 6m 48s
```

产物：

- `android/app/build/outputs/apk/release/app-release-unsigned.apk`
- `android/app/build/outputs/bundle/release/app-release.aab`

Release 已开启 R8 与资源压缩。

## 尚未达到发布门槛

| 项目 | 状态 | 关闭条件 |
|---|---|---|
| KL-026/KL-027 真人发音 | 阻塞 | 提供并审核 104 个真人读音，建立一对一资源映射；Release 不使用 Debug TTS fallback |
| KL-134 正式签名 | 阻塞 | 提供发布 keystore/CI 密钥后生成并验证正式签名 AAB/APK |
| KL-136 全量关闭 | 未通过 | 上述 P0/P1 清零后重新执行本矩阵 |

真机稳定性补充：对 `com.gojapan` 注入 300 次随机快速交互，命令正常退出，日志中无 GoJapan `FATAL EXCEPTION` 或 ANR。

结论：M4 的客户端代码、迁移、损坏库回退、离线、适配、无障碍、性能、稳定性、R8 和构建验证已完成；当前产物是未签名候选包，不应提交商店。

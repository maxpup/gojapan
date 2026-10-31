# M2 学习闭环验收记录

验收日期：2026-08-23  
设备：Android Emulator `medium_phone`，Android 16 / API 36  
应用：`com.gojapan`，Debug / Release

## 结论

M2 代码闭环已通过：用户可在无网络条件下完成“首页 → 5 音 10 卡配对 → 原子结算 → 结算页 → 首页/再练一轮”，完成记录写入本机 Room，首页通过 Flow 自动刷新。

真人发音文件属于外部内容交付（KL-026/KL-027），当前不计入 M2 代码验收：Debug 使用日语 TTS 降级，Release 已通过 `ENABLE_TTS_FALLBACK=false` 禁用该降级，正式发布前必须补齐 104 个审核后的内置真人音频。

## 功能证据

| 验收项 | 结果 | 证据 |
|---|---|---|
| 首页展示探索、巩固、天数、轮数、到期数 | 通过 | 模拟器首次安装为 `0 / 104`、`0 轮`；完成两轮后为 `10 / 104`、`学习 1 天`、`完成 2 轮` |
| 默认选题最多 5 音 | 通过 | 每轮生成 5 个 `KanaItem` 和 10 张卡片；调度算法单测覆盖到期、新音、薄弱音 |
| 正确配对、错误双归属、连击 | 通过 | `GameViewModelTest` 覆盖错误归属、连击与最后一对 |
| 提示 3 次、重排 2 次 | 通过 | 状态机单测与模拟器 UI 均验证预算显示和消耗 |
| 计时与最高连击 | 通过 | 模拟器结算快照显示 `用时 00:56 · 最高连击 5` |
| 中途退出确认且不落库 | 通过 | Compose 测试验证确认文案；Repository 只在最后一对完成后调用 |
| 最后一对只提交一次 | 通过 | 单测验证重复点击后 Repository 调用次数仍为 1 |
| 写入失败可用同一 sessionId 重试 | 通过 | 单测模拟首次失败，第二次提交沿用同一 ID |
| Room 原子写入与幂等 | 通过 | 连接测试验证三表写入、重复提交、级联删除和 500 轮保留 |
| 三类记忆结果与复习间隔 | 通过 | JVM 测试覆盖独立/困难/重学及 1/3/7/15/30/60 天 |
| 结算快照、最早复习日 | 通过 | 模拟器显示 5 音词汇、释义、累计次数及 `建议 8月24日` |
| 多选自评记熟 | 通过 | Compose 测试验证选择后按钮可用；Room 测试验证不改变 level、到期时间、次数 |
| 再练一轮 | 通过 | 从结算页按最新 Room 进度重新执行调度并生成新轮次 |
| 返回首页/重复点击 KANA LINK | 通过 | 返回首页后统计即时刷新；应用壳重复点击当前 KANA LINK Tab 会强制回首页 |
| 配置变化与进程策略 | 通过 | Activity 级 Hilt ViewModel 保留棋盘；进程重启重新建立 Home flow，不持久化未完成棋盘 |
| Debug 发音降级 | 通过 | 点击平假名卡调用日语 TTS，罗马音卡静音；Release 常量关闭 TTS |

## 自动验证

在 `/Users/tan/projects/gojapan/android` 执行：

```bash
./gradlew testDebugUnitTest connectedDebugAndroidTest lintDebug assembleDebug assembleRelease
```

结果：`BUILD SUCCESSFUL`，共运行 8 个 Android 连接测试，0 skipped、0 failed；Debug 与 Release APK 均生成成功。

产物：

- Debug：`android/app/build/outputs/apk/debug/app-debug.apk`
- Release：`android/app/build/outputs/apk/release/app-release-unsigned.apk`

## 数据位置

模拟器/设备私有目录：`/data/user/0/com.gojapan/databases/kana-link.db`。数据库包含 `kana_progress`、`study_session`、`session_item`，外部应用不可直接访问。

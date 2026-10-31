# M3 完整 MVP 验收记录

验收日期：2026-08-23  
设备：Android Emulator `medium_phone`，Android 16 / API 36

## 结论

M3 客户端功能已形成完整 MVP：自定义练习、三周期学习报告、自评记熟、设置持久化、版本化记录导出、二次确认清除和本机复习通知均已接入 KANA LINK 首页，并保持离线运行。

真人音频 KL-026/KL-027 仍是发布前外部内容依赖；Debug 使用日语 TTS，Release 不依赖 TTS。该内容门禁继续保留到 M4 发布验收。

## 功能验收

| 范围 | 结果 | 验收证据 |
|---|---|---|
| 三种自定义模式 | 通过 | 平假名↔罗马音、片假名↔罗马音、平假名↔片假名均生成正确卡片类型；轮次记录保存真实 mode 和 CUSTOM source |
| 四类课程范围 | 通过 | 清音、浊音、半浊音、拗音支持独立选择、全选、清空；空范围禁止开始；少于 5 音时调度器使用全部候选 |
| 自定义偏好恢复 | 通过 | 模拟器选择“平假名↔片假名”并清空范围，返回后重新进入仍恢复该模式、0 个范围和禁用状态；DataStore 连接测试覆盖非空/空范围及三个开关 |
| 语音和音效开关 | 通过 | 假名语音与正确/错误/完成反馈分别受独立开关控制，偏好由 DataStore 恢复 |
| 日/周/月报告 | 通过 | 使用本地自然日、周一为周首日、自然月；JVM 边界测试覆盖三种范围 |
| 报告统计 | 通过 | 直接聚合完整 session 日志，展示轮数、不同音、正确率、时长及上一周期对比；Room 测试验证范围过滤和 50% 正确率样例 |
| 报告空状态 | 通过 | 无完整轮次显示“本期还没有完整练习记录”，不由累计进度伪造历史 |
| 报告音列表与自评 | 通过 | 展示假名、罗马音、出现次数、词汇和释义；支持多选自评并从 Room 重新加载状态 |
| 首页已完成记忆 | 通过 | 区分“系统巩固完成”和“用户自评记熟”，并展示 5/20/40/60/80/104 里程碑 |
| JSON 导出 | 通过 | Storage Access Framework 创建用户选择的 JSON 文件，不申请存储权限；格式含版本、生成时间、进度、轮次及单音快照 |
| 清除历史 | 通过 | 二次确认后在单事务中清除 session、级联 item 和 progress；模式与开关偏好保留；连接测试验证三表状态 |
| 复习通知权限 | 通过 | API 33+ 仅在用户主动开启时出现系统 `POST_NOTIFICATIONS` 对话框；拒绝时开关保持关闭 |
| 到期检查 | 通过 | WorkManager 2.11.2 注册唯一 24 小时周期任务；模拟器 JobScheduler 确认 `com.gojapan/androidx.work.impl.background.systemjob.SystemJobService` 已注册 |
| 通知跳转 | 通过 | 点击通知以新任务打开 goJapan，落在 KANA LINK 首页并由首页 Flow 刷新到期数量 |

## 自动验证

```bash
cd /Users/tan/projects/gojapan/android
./gradlew testDebugUnitTest connectedDebugAndroidTest lintDebug assembleDebug assembleRelease
```

结果：`BUILD SUCCESSFUL`；14 个 Android 连接测试全部通过，0 skipped、0 failed；JVM 单测、lint、Debug 和 Release 构建通过。

主要测试覆盖：

- 三种配对模式及 CUSTOM 轮次来源；
- 日/周/月本地时间边界；
- 报告范围、正确率、时长与上一周期；
- DataStore 模式、空/非空范围和开关恢复；
- 导出快照及清除级联；
- 报告空态、自定义空范围、清除二次确认；
- M0～M2 原有课程、算法、数据库、状态机和完整闭环回归。


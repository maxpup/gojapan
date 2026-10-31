# KANA LINK M1 验收记录

对应里程碑：`TASK_BREAKDOWN.md` 的 M1 领域核心。

## 已交付

- 104 音版本化课程：46 清音、20 浊音、5 半浊音、33 拗音；
- `ji/ji-di`、`zu/zu-du` 稳定独立主键；
- 课程常用词、中文释义与音频资源键；
- `KanaItem`、`KanaProgress`、配对模式、轮次来源和三类复习结果领域模型；
- Room 三表：`kana_progress`、`study_session`、`session_item`；
- Room v1 schema export、索引、外键级联和完整轮次事务；
- 相同 `sessionId` 幂等提交；
- 最近 500 个完整轮次保留策略，不删除累计进度；
- DataStore 用户偏好模型；
- 到期、新音、课程顺序、错误次数和阶段权重选题；
- 独立完成、困难完成、需要重学判定；
- 1、3、7、15、30、60 天调度；
- 阶段进度与预测记忆率。

## 自动化验收

```bash
cd android
./gradlew testDebugUnitTest lintDebug assembleDebug
./gradlew connectedDebugAndroidTest
```

覆盖课程数量与分类、特殊 ID、固定随机源选题、所有记忆结果、全部阶段间隔、预测记忆率、三表事务、幂等写入、外键级联和 500 轮保留策略。

真人读音文件属于独立内容交付 KL-026；M1 已生成 104 个稳定 `audioKey`，实际音频不阻塞领域核心验收，但必须在发布里程碑前补齐。

# goJapan / KANA LINK 安卓客户端技术方案

版本：V1.1  
对应需求：[PRD.md](./PRD.md) V2.0  
目标阶段：Android MVP  
核心约束：KANA LINK 是 goJapan 客户端内的五十音模块，不是独立 App；该模块不依赖服务端、账号或网络

## 1. 方案结论

goJapan Android 首版中的 KANA LINK 采用原生技术栈实现：

- 开发语言：Kotlin；
- UI：Jetpack Compose + Material 3；
- 应用形态：复用 goJapan 单 Activity 应用壳，KANA LINK 作为主导航下的功能模块；
- 架构：分层架构、单向数据流；
- 本地存储：Room；少量设置使用 DataStore；
- 依赖注入：Hilt；
- 异步与状态：Kotlin Coroutines + Flow；
- 页面导航：Navigation Compose；
- 音频：真人标准读音文件随安装包发布，Android `MediaPlayer` 单实例播放；
- 后台提醒：WorkManager，仅做本机复习通知；
- 最低系统版本：Android 8.0（API 26）；目标版本使用发布时 Google Play 要求的最新稳定 API。

本期不为 KANA LINK 建设 API、数据库服务、登录、云同步、远程配置和运营后台。用户在离线、飞行模式和首次安装后无网络的情况下，也必须能完成从 KANA LINK 首页到结算、报告和历史恢复的完整流程。goJapan 其他模块未来是否联网，不改变 KANA LINK 的离线可用要求。

仓库内现有 `index.html` 是 Web Demo，仅作为视觉、交互和规则验证参考，不作为正式 Android 客户端的运行时或 WebView 页面。Android 工程建议新增在 `android/` 目录，应用 ID、主题、主导航和发布配置由 goJapan 应用壳统一管理，KANA LINK 不单独产生安装包。现有 Demo 暂时保留，待 Android 功能验收后再决定是否归档。

PRD 中仍有“响应式 Web”“浏览器配置文件”和 `localStorage` 等 Demo 口径；根据 Android 优先约束，正式实现分别解释为 Android 自适应布局、当前设备上的应用数据和 Room 本地数据库。产品流程与统计口径保持不变。

## 2. 范围

### 2.1 本期实现

- 104 音课程及常用词数据；
- goJapan 四 Tab 主导航中的 KANA LINK 入口及高亮状态；
- 首页、冒险进度、默认学习入口；
- 5 音 10 卡配对游戏；
- 平假名、片假名、罗马音三种配对关系；
- 提示、重排、连击、计时和音效；
- 1、3、7、15、30、60 天间隔复习；
- 单音学习状态、选题调度和自评记熟；
- 单轮结算及日、周、月报告；
- 本地设置、数据清除和学习记录导出；
- 可选的本机复习通知。

### 2.2 本期不实现

- 用户注册、登录及跨设备同步；
- 服务端接口、服务端数据库和后台管理；
- 在线 TTS、发音评分、语音识别；
- 远程题库、动态活动和 A/B 实验；
- iOS 客户端；
- “学日语”“搜索”“行程”三个 goJapan 模块的内部功能；本期仅对接导航目的地，独立 Demo 中可显示占位页。

## 3. 总体架构

```text
goJapan App Shell（单 Activity、四 Tab）
  │
KANA LINK Compose UI
  │ 用户操作 / UI State
  ▼
ViewModel
  │
  ├── StartRoundUseCase       选出下一轮 5 个音
  ├── CompleteRoundUseCase    原子提交完整轮次
  ├── GetHomeSummaryUseCase   首页统计
  ├── GetReportUseCase        日/周/月聚合
  └── MarkMasteredUseCase     自评记熟
            │
            ▼
       Repository 接口
       ├── Room：学习记录、轮次、单音结果
       ├── DataStore：偏好、开关、数据版本
       └── assets/res：课程 JSON、内置音频
```

采用一个 app module 起步，并在包内按功能边界拆分，避免 MVP 过早多模块化：

```text
android/app/src/main/java/com/gojapan/
├── app/               goJapan 应用壳、主题和四 Tab 主导航
└── feature/kanalink/
    ├── data/
    │   ├── course/       课程静态数据读取与校验
    │   ├── local/        Room、DAO、Entity、Migration
    │   ├── preferences/  DataStore
    │   └── repository/   Repository 实现
    ├── domain/
    │   ├── model/        领域模型
    │   ├── scheduler/    复习与选题算法
    │   └── usecase/      业务用例
    ├── ui/
    │   ├── home/
    │   ├── game/
    │   ├── result/
    │   ├── report/
    │   └── settings/
    ├── audio/             读音及游戏音效播放
    ├── notification/      本机复习提醒
    └── di/                KANA LINK 依赖配置
```

UI 不直接读写 DAO；选题、阶段变更和统计都放在 domain 层，以便 JVM 单元测试覆盖并为未来 iOS 或服务端复用规则定义。

实现遵循 Compose 单向数据流：页面只消费不可变 `UiState` 并上报 `UiAction`；ViewModel 负责状态生产和调用 UseCase；可复用 Composable 通过参数与事件回调完成状态提升，不直接持有 Repository。Flow 在 UI 层按生命周期收集，避免页面不可见时继续执行无意义的界面更新。

### 3.1 核心接口契约

```kotlin
interface KanaCourseRepository {
    suspend fun getCourse(): List<KanaItem>
}

interface LearningRepository {
    fun observeHomeSummary(now: Instant): Flow<HomeSummary>
    suspend fun getProgress(ids: Set<KanaId>): Map<KanaId, KanaProgress>
    suspend fun completeRound(command: CompleteRoundCommand): RoundResult
    suspend fun markMastered(ids: Set<KanaId>, masteredAt: Instant)
}

fun interface RoundScheduler {
    suspend fun select(request: RoundRequest): List<KanaId>
}
```

约束如下：

- `KanaId` 使用 value class 或等价强类型，禁止将 UI 罗马音直接当主键；
- UseCase 接收注入的 `Clock` 与随机源，业务代码不得直接读取系统时间或全局随机数；
- `completeRound` 是唯一正式学习写入口，并保证同一 `sessionId` 幂等；
- Repository 返回领域模型，不向 UI 暴露 Room Entity、DAO 或 DataStore key；
- 课程加载失败返回内置基线数据；学习记录读写失败返回可展示错误，不伪造成功结算。

## 4. 页面和状态流

goJapan 应用壳固定提供四个一级目的地，顺序为“学日语、KANA LINK、搜索、行程”。手机使用底部 `NavigationBar`；大屏可使用 `NavigationRail`，但顺序和语义保持一致。应用壳持有一级导航状态，KANA LINK 使用嵌套导航图。

Navigation Compose 路由：

```text
GoJapanRoot
├── LearnJapanese
├── KanaLinkGraph
│   ├── Home
│   ├── Game(roundId, source=daily|custom)
│   ├── Result(roundId)
│   ├── CustomPractice
│   ├── Report(period)
│   └── Settings
├── Search
└── Trip
```

KANA LINK 首页、游戏、设置、结算和报告均保留 goJapan 主导航，并高亮 KANA LINK。每次点击 KANA LINK Tab 都执行 `popUpTo(KanaLinkHome)` 并触发首页概览重新查询；点击其他 Tab 切换到对应模块。通过系统返回键回到 KANA LINK 时才恢复原导航状态。

### 4.1 游戏状态

`GameViewModel` 持有不可变 `GameUiState`：

- 本轮 5 个 `KanaItem` 快照；
- 打乱后的 10 张卡片；
- 第一张已选卡；
- 已完成配对集合；
- 每个音的错误次数、首次判断结果、提示使用状态和响应时间；
- 当前/最高连击、剩余提示、剩余重排、开始时间；
- `Playing`、`Evaluating`、`Completing`、`Completed` 状态。

判断第二张卡时短暂进入 `Evaluating`，期间禁用重复点击，防止快速连点导致重复结算。旋转屏幕等配置变化由 ViewModel 保留当前棋盘；未完成棋盘不写入 Room 或 DataStore。进程被系统回收后重新进入 KANA LINK 首页，不恢复未完成轮次。只有完成整轮时才写正式学习数据，中途退出或切换其他 Tab 不增加学习次数。

完成按钮不存在，最后一对配对成功后由 ViewModel 调用 `CompleteRoundUseCase`。该用例在一个 Room transaction 内写入轮次、单音结果和用户汇总，成功后才导航到结算页，确保不会出现只更新一半的数据。

状态迁移只允许：

```text
Playing --第二张卡--> Evaluating --仍有卡片--> Playing
                                  --最后一对--> Completing
Completing --事务成功--> Completed
           --事务失败--> CompleteFailed --重试--> Completing
```

`Evaluating`、`Completing` 和 `Completed` 均拒绝卡片点击；提交失败保留本轮证据和固定 `sessionId`，用户重试时不得重新选题或生成新 ID。导航参数只传 `sessionId`/筛选条件等稳定标识，不传完整对象；结算页从已提交轮次快照读取。

## 5. 数据设计

### 5.1 静态课程数据

104 音及常用词维护在版本化的 `assets/course/kana_v1.json` 中，安装包内只读。字段如下：

```json
{
  "id": "ji-di",
  "order": 57,
  "group": "DAKUON",
  "row": "DA",
  "hira": "ぢ",
  "kata": "ヂ",
  "romaji": "ji",
  "word": "鼻血（はなぢ）",
  "meaningZhCn": "鼻血",
  "audio": "kana_ji_di.m4a"
}
```

`id` 是永久稳定的业务主键，禁止以罗马音作为主键。特别保留：

- `じ = ji`；
- `ぢ = ji-di`；
- `ず = zu`；
- `づ = zu-du`。

Debug 构建和测试必须校验总数为 104、ID 唯一、音频存在、课程顺序唯一、分类数量为 46/20/5/33。

### 5.2 Room 表

#### `kana_progress`

| 字段 | 类型 | 说明 |
|---|---|---|
| `kana_id` | TEXT PK | 对应静态课程 ID |
| `seen_count` | INTEGER | 完整轮次中的出现次数 |
| `first_try_correct_count` | INTEGER | 首次即正确次数 |
| `wrong_count` | INTEGER | 参与错误判断次数 |
| `hint_count` | INTEGER | 使用提示完成次数 |
| `level` | INTEGER | -1～5 |
| `last_result` | TEXT | INDEPENDENT/DIFFICULT/RELEARN |
| `last_reviewed_at` | INTEGER? | UTC epoch millis |
| `next_review_at` | INTEGER? | UTC epoch millis |
| `manual_mastered` | INTEGER | Boolean |
| `manual_mastered_at` | INTEGER? | UTC epoch millis |

未学习的音可以不建行，读取时映射为 `level = -1`。不复制平假名、片假名等静态字段，避免课程升级后出现两份数据不一致。

#### `study_session`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | TEXT PK | UUID |
| `completed_at` | INTEGER | 完成时刻 |
| `local_date` | TEXT | 完成时所在时区的 yyyy-MM-dd 快照 |
| `timezone_id` | TEXT | 完成时 IANA 时区 |
| `duration_seconds` | INTEGER | 有效用时 |
| `mode` | TEXT | HIRA_ROMAJI/KATA_ROMAJI/HIRA_KATA |
| `source` | TEXT | DAILY/CUSTOM |
| `best_combo` | INTEGER | 最高连击 |
| `wrong_attempts` | INTEGER | 错误配对次数 |
| `hint_uses` | INTEGER | 本轮提示次数 |

#### `session_item`

| 字段 | 类型 | 说明 |
|---|---|---|
| `session_id` | TEXT FK | 所属轮次 |
| `kana_id` | TEXT | 音 ID |
| `wrong_count` | INTEGER | 本轮该音参与错误次数 |
| `first_try_correct` | INTEGER | Boolean |
| `hint_used` | INTEGER | Boolean |
| `response_ms` | INTEGER? | 单音累计响应时间 |
| `was_new` | INTEGER | 结算前是否未学习 |
| `level_before` | INTEGER | 结算前阶段 |
| `level_after` | INTEGER | 结算后阶段 |
| `next_review_at` | INTEGER | 本轮产生的复习时间 |
| `word_snapshot` | TEXT | 当时展示词汇 |
| `meaning_snapshot` | TEXT | 当时中文释义 |

主键为 `(session_id, kana_id)`。报告直接聚合完整轮次，避免由累计值反推历史。MVP 最多保留最近 500 个完整轮次；提交第 501 轮时，在同一事务中按完成时间删除最旧的 `study_session`，并通过外键级联删除对应 `session_item`，不得删除 `kana_progress` 聚合状态。

#### DataStore 设置

- `preferredMode`；
- `selectedGroups`；
- `voiceEnabled`；
- `soundEffectEnabled`；
- `reviewNotificationEnabled`；
- `courseDataVersion`；
- `schemaExportVersion`。

“学习天数”和“累计轮数”不单独持久化，直接从 `study_session` 聚合，避免冗余计数失真。数据量很小，相关日期和 `kana_id` 建索引即可。

## 6. 核心算法

### 6.1 下一轮选题

调度器输入当前时刻、候选课程、`kana_progress` 和可注入随机源，输出最多 5 个不重复的 `kana_id`。严格按 PRD 评分：

```text
score = random(0..10)
      + (未学习 ? 80 : 0)
      + (候选总数 - 课程序号，仅未学习项使用)
      + (已到期 ? 120 : 0)
      + min(逾期天数 × 10, 80)
      + wrongCount × 8
      - max(level, 0) × 5
```

实现约束：

- 到期判断使用 `nextReviewAt <= now`；
- 默认候选池为全部 104 音；
- 自定义范围少于 5 个时全部使用；
- 104 音均学过且无到期内容时，仍按错误和阶段得分选薄弱音；
- 随机源在测试中固定 seed，确保算法可复现；
- `manualMastered` 不改变选题权重。

### 6.2 单音结算

每个音按本轮证据独立计算：

```text
错误 0 次且未使用提示 -> INDEPENDENT
错误 1 次或使用过提示 -> DIFFICULT
错误 >= 2 次           -> RELEARN
```

阶段更新：

```text
INDEPENDENT: min(oldLevel + 1, 5)
DIFFICULT:   oldLevel == -1 ? 0 : oldLevel
RELEARN:     0
```

新阶段产生后，以结算时刻加 `[1, 3, 7, 15, 30, 60]` 天得到 `nextReviewAt`。这里的“天”按 24 小时计算调度时刻；日报、周报和学习天数按轮次保存的本地日历日期计算，两种口径不混用。

### 6.3 正确率与进度

```text
正确率 = 完成配对数 / (完成配对数 + 错误配对尝试数)
探索进度 = 有 progress 记录的音数 / 104
巩固进度 = level == 5 或 manualMastered 的音数 / 104
阶段进度 = level == -1 ? 0 : (level + 1) / 6
预测记忆率 = 0.9 ^ (距最近复习天数 / 当前阶段间隔天数)
```

预测记忆率只用于展示和调度说明，不包装成科学测量结果。

## 7. 音频方案

MVP 不使用系统 TTS 作为主要方案。104 个标准日语读音采用统一说话人录制，编码为单声道 AAC/M4A，存放于 `res/raw`，通过资源映射表定位。预计总大小可控制在数 MB。

- 点击平假名或片假名播放同一读音；
- 点击罗马音不播放；
- Android `MediaPlayer` 复用单个实例；
- 快速连续点击时停止上一段并播放最新一段；
- 音频缺失或播放异常只记录本地日志，不阻断配对；
- 游戏成功/失败音效与读音开关分离。

真人音频未准备完成前，开发构建可使用 Android `TextToSpeech` 日语 voice 作为临时 fallback，但发布构建必须通过完整资源校验。

## 8. 离线、时间与可靠性

- 所有课程、词汇、音频和算法均在安装包内；
- Room 是学习历史的唯一事实来源；
- 完成轮次使用单事务，重复提交通过 session UUID 和页面状态防重；
- 所有时刻存 UTC epoch millis，报告额外保存完成时的本地日期与时区；
- 用户修改系统时间可能影响到期判断，MVP 接受该限制，不请求联网校时；
- Room 开启 schema export，每次变更提供显式 Migration 和迁移测试；
- 静态课程解析失败时回退安装包内的基线课程并记录本地错误；Room 整库损坏时先保留损坏文件副本，再创建空历史并提示本机记录已重置，满足“回退为空历史、不阻断学习”；
- 设置页提供 JSON 导出。导出包含版本、生成时间、轮次和进度，为未来账号同步保留迁移入口。

## 9. 通知与权限

复习通知默认关闭。用户主动开启后，使用 WorkManager 每日检查本地到期数量并发送通知。Android 13 及以上只在用户开启时请求 `POST_NOTIFICATIONS`。

KANA LINK 不新增网络、定位、通讯录、麦克风和存储广泛访问权限。导出文件使用系统 Storage Access Framework，由用户选择保存位置。若 goJapan 其他模块需要 `INTERNET` 等权限，必须保证 KANA LINK 不调用相关接口且离线完整可用；以后接入崩溃分析或同步时需另行评审隐私与离线降级。

## 10. 测试方案

### 10.1 JVM 单元测试

- 104 音数量、分类、顺序、ID 和音频映射校验；
- `ji/ji-di`、`zu/zu-du` 独立存储；
- 三类单轮结果和 level 边界；
- 1/3/7/15/30/60 天计算；
- 到期、逾期、错误和课程顺序评分；
- 固定 seed 下选题稳定且不重复；
- 正确率、探索、巩固、预测记忆率；
- 日、周、月边界及跨时区数据。

### 10.2 数据库测试

- 完整轮次三表原子写入；
- 重复 session ID 不产生重复统计；
- 中途退出不写学习记录；
- Migration 保留全部进度和日志；
- 自评记熟不修改 level 和 nextReviewAt。

### 10.3 Compose UI / 仪器测试

- 新用户一次点击进入第一轮；
- 四个主 Tab 顺序正确，进入 KANA LINK 后所有子页面均保持正确高亮；
- 从游戏切换到其他 Tab 不写入轮次；点击 KANA LINK Tab 返回模块首页并刷新概览；
- 10 卡配对、错误锁定、提示和重排；
- 旋转屏幕后棋盘与计时状态恢复，进程重启后不恢复未完成棋盘；
- 最后一对完成后只结算一次；
- 返回首页立即刷新进度；
- 报告空状态及周期切换；
- 关闭音频后不播放且游戏可完成；
- 无网络条件下完成全链路。

关键业务目标是 domain 单元测试全覆盖；整体行覆盖率建议不低于 80%，不以 UI 像素代码覆盖率作为发布门槛。

## 11. 可访问性与性能

- 卡片触控区域至少 48dp；
- 为假名、罗马音、选中、正确和错误状态提供 TalkBack 描述；
- 状态不能只用颜色区分；
- 支持系统字体放大，超大字体时卡片可适度缩放文字而不截断；
- 支持深色模式可以延后，但颜色 token 从首版集中管理；
- KANA LINK 首页首次进入目标低于 1 秒（中端设备、无数据库迁移）；
- 配对点击到视觉反馈目标低于 100ms；
- 列表和报告查询不在主线程执行。

## 12. 开发阶段与交付

### 阶段一：工程与领域核心

- 建立 Android 工程、CI、代码规范；
- 建立 goJapan 应用壳、四 Tab 导航和 KANA LINK 嵌套导航图；
- 导入并校验 104 音和词汇；
- 完成 Room、DataStore、调度器和结算算法；
- 领域及数据库测试通过。

### 阶段二：核心学习闭环

- 首页、游戏、结算；
- 真人音频接入；
- 默认学习、到期复习、数据恢复；
- 离线端到端验收。

### 阶段三：完整 MVP

- 自定义练习、日/周/月报告、自评记熟；
- 设置、导出、清除数据和可选通知；
- 可访问性、性能、异常和迁移测试；
- 内测包与 Play 商店发布准备。

## 13. 发布门槛

- PRD 第 4 章 Android 场景全部通过；
- KANA LINK 作为 goJapan 内部模块交付，不存在独立桌面图标或独立安装包；
- 全新安装且无网络可完成学习闭环；
- 104 个读音资源完整、无错配；
- 无崩溃、ANR、重复结算和学习数据丢失的已知 P0/P1 问题；
- 数据库迁移测试通过；
- 至少在小屏手机、主流手机和平板三类尺寸验证；
- Release 构建开启 R8，并保留 Room/序列化所需规则；
- 隐私说明明确“学习记录仅保存在本机”，数据安全表按实际权限填写。

## 14. 未来演进边界

后续增加 iOS 或服务端时，不改变本期稳定 ID 和导出格式。建议先定义版本化同步事件，再引入账号：

- 客户端继续负责即时游戏状态和离线写入；
- 服务端同步完整轮次事件，而非简单覆盖聚合字段；
- 计数按未同步事件合并；
- 最近复习取较晚事件，并按该事件结果重算阶段和到期时间；
- 冲突解决必须保持 `manualMastered` 的用户意图。

在出现跨设备同步、内容动态更新、排行榜或付费权益之前，五十音核心功能没有引入服务端的必要。

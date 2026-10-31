# GoJapan 行程模块 Android 应用架构设计

版本：V1.0  
日期：2026-10-16  
输入：[TRIP_PLANNER_PRD.md](TRIP_PLANNER_PRD.md)  
范围：纯 Android 客户端；服务端、AI Gateway、云同步的内部实现不在本文范围内

## 1. 设计结论

行程模块采用 **单 Activity + Jetpack Compose + Navigation 3 + UDF + 分层架构 + Room 本地优先**。

- Room 是行程、日期、章节、地点顺序、草稿、手账及媒体元数据的唯一事实源；UI 不直接持有可丢失的业务真值。
- DataStore 只保存轻量偏好与一次性迁移标志，不再保存整份行程 JSON。
- 媒体文件存放在 app-specific storage，Room 仅保存文件引用和处理状态；通过系统 Photo Picker / SAF 导入。
- 地图、地点搜索、定位和 AI 都是可失败的外部能力。失败时，本地计划、编辑和手账必须继续可用。
- AI 结果先写入独立的导入草稿表；只有用户逐项确认后，才通过一个事务写入正式 `Stop`。
- 自动保存采用“内存即时更新 + 防抖持久化 + 离开前强制 flush + 可恢复草稿”，明确展示 `Saving/Saved/Failed`。
- P0 不实现账号、跨设备同步和协作，但数据模型预留稳定 UUID、版本号和更新时间，避免 P1 推倒重来。
- 当前工程先保持单 Gradle `:app` 模块，以 feature/core 包隔离；达到多人并行或构建瓶颈后再物理拆模块。

## 2. 目标与边界

### 2.1 本期目标

覆盖 PRD 的 P0：

1. 多行程和按目的地时区组织的每日文档。
2. 首页概览、每日摘要、文档式详情、可重命名索引树和背景色。
3. 地点管理、排序、地点内图文视频内容、今日事项和地点 Tips。
4. 按需打开的当日地图抽屉、地点选择联动、定位和 Google 地图跳转。
5. 照片与艺术文字手账。
6. 文本、链接、截图和 PDF 的 AI 导入核对闭环。
7. 弱网、进程重建、权限拒绝和外部服务失败下的数据可靠性。

### 2.2 不在 Android P0 内部实现

- 云端账号、跨设备同步、多人协作和冲突合并。
- AI Provider 路由、密钥托管、模型调用、地理编码服务端实现。
- 媒体云上传、转码、CDN、分享与导出。
- 自动路线优化或交通时间计算。

Android 只定义这些外部能力的客户端接口。生产版不得把平台 AI 密钥放进 APK；相关请求应调用自有 Gateway。当前 `DeepSeekTripGenerator` 仅视为原型代码，不能进入生产发布链路。

## 3. 质量属性与架构约束

| 属性 | 目标 | 设计手段 |
|---|---|---|
| 数据可靠性 | 已成功保存的编辑在崩溃、进程回收、重启后恢复 | Room 事务、外键、草稿表、原子媒体落盘 |
| 离线可用 | 无网时可查看和编辑已有行程与手账 | 本地事实源；远端能力返回显式不可用状态 |
| 一致性 | 文档、索引、地点卡片和地图顺序一致 | 所有投影都观察同一 `TripDayGraph` Flow |
| 响应性 | 编辑立即反馈，数据库和媒体工作不阻塞主线程 | Compose 本地输入态、协程、IO dispatcher、WorkManager |
| 可恢复性 | 保存/导入失败不丢原输入 | `EditorDraft`、`ImportSource`、可重试状态机 |
| 隐私 | 默认本地处理媒体，分析不含正文和精确位置 | app-specific storage、最小权限、Analytics allowlist |
| 可访问性 | 字体放大、TalkBack、键盘和系统返回均可操作 | 语义标签、焦点顺序、最小触控目标、抽屉统一关闭语义 |
| 可测试性 | 领域规则和状态机可脱离设备测试 | Repository 接口、UseCase、fake gateway、Room migration tests |

建议基线沿用现有工程：minSdk 26、target/compileSdk 36、Kotlin、Compose Material 3、Hilt、Room、WorkManager、Navigation 3、Maps Compose。

## 4. 总体架构

```text
┌──────────────── Android UI ────────────────┐
│ Compose Screen / Component                 │
│ 只渲染 UiState，发送 UiAction              │
└───────────────────┬────────────────────────┘
                    │
┌──────────────── Presentation ──────────────┐
│ ViewModel + SavedStateHandle               │
│ Reducer / UiState / Effect                 │
└───────────────────┬────────────────────────┘
                    │ UseCase
┌────────────────── Domain ──────────────────┐
│ Trip、TripDay、Section、Stop、Media、Import │
│ 排序/校验/确认/删除影响分析等业务规则       │
└──────────────┬────────────────┬────────────┘
               │                │
┌──────────── Data ─────────┐  ┌─ Platform / External ─────┐
│ Repository implementation │  │ Photo Picker / SAF         │
│ Room DAO / transaction     │  │ Fused Location（可替换）   │
│ DataStore preferences      │  │ Maps SDK / Maps URL        │
│ app-specific media files   │  │ Places / AI Gateway client │
└────────────────────────────┘  └────────────────────────────┘
```

依赖方向只能向内：UI → domain 接口；data/platform 实现 domain 接口。Compose、Android `Uri`、Google Maps 类型不能进入 domain model。

### 4.1 本地优先读写规则

```text
用户操作
  → ViewModel 校验并更新输入态
  → UseCase 写 Room 事务
  → Room Flow 发出新快照
  → 所有 UI 投影同时刷新

远端结果
  → Repository 转为本地 draft/entity
  → Room Flow 发出新快照
  → UI 不直接消费网络 DTO
```

禁止双事实源：不得同时维护一份 ViewModel 可变行程和一份数据库行程，再人工互相覆盖。

## 5. 工程组织

P0 继续使用 `:app`，按以下包边界组织：

```text
com.gojapan/
├── app/
│   ├── navigation/              # 顶层目的地与 route key
│   └── theme/
├── core/
│   ├── database/                # GoJapanDatabase、migration、converter
│   ├── media/                   # 导入、复制、缩略图、清理
│   ├── location/                # 权限无关的定位接口
│   ├── maps/                    # 地图模型转换、Maps URL
│   ├── network/                 # HTTP、认证、错误映射
│   ├── analytics/               # 事件白名单
│   └── common/                  # Clock、Dispatcher、Result 类型
└── feature/trip/
    ├── domain/
    │   ├── model/
    │   ├── repository/
    │   └── usecase/
    ├── data/
    │   ├── local/               # entity、DAO、transaction
    │   ├── remote/              # place/AI DTO 与 client
    │   ├── mapper/
    │   └── repository/
    └── ui/
        ├── list/
        ├── overview/
        ├── daydocument/
        ├── mapdrawer/
        ├── editor/
        ├── journal/
        └── importreview/
```

拆成 Gradle 模块的触发条件：行程功能有两个以上并行团队、`:app` 增量构建明显恶化、或需要独立 API/实现边界。届时建议 `:core:database`、`:core:media`、`:feature:trip:domain`、`:feature:trip:data`、`:feature:trip:ui`，而不是按每个页面碎片化。

## 6. 导航与自适应 UI

### 6.1 Navigation 3 目的地

```kotlin
@Serializable sealed interface TripRoute {
    @Serializable data object TripList : TripRoute
    @Serializable data class Overview(val tripId: String) : TripRoute
    @Serializable data class DayDocument(
        val tripId: String,
        val dayId: String,
        val anchor: String? = null,
    ) : TripRoute
    @Serializable data class ImportReview(val tripId: String, val sourceId: String) : TripRoute
}
```

Route 只传稳定 ID 和可选锚点，不传完整对象。ViewModel 使用 `SavedStateHandle` 恢复 ID、当前选中地点、抽屉类型和滚动锚点；业务内容始终从 Room 重新观察。

### 6.2 窗口布局

不使用设备名称判断布局，使用当前窗口宽度：

| 窗口宽度 | 索引 | 正文 | 地图 |
|---|---|---|---|
| Compact（手机） | Modal drawer | 单列 | Modal bottom sheet / 全高抽屉 |
| Medium | 可收起侧栏或 Modal drawer | 单列限宽 | 右侧 overlay sheet |
| Expanded | 固定侧栏 | 居中单列文档 | 右侧 overlay pane，不替代正文 |

目录抽屉和地图抽屉共享 `ActiveOverlay = None / Index / Map`，从状态上保证互斥。系统返回的优先级是：关闭编辑菜单 → 关闭抽屉 → 处理未保存编辑 → 返回上一页。

`LazyColumn` 的章节使用稳定 key；索引点击通过 section ID 查找当前列表索引后滚动。当前章节由可见项计算，只更新 UI 选中态，不高频写库。地点卡片使用 `LazyRow`，禁止整页 `horizontalScroll`。

## 7. 数据模型与 Room 设计

所有 ID 使用客户端生成的 UUID 字符串；日期保存 ISO `yyyy-MM-dd`，时区保存 IANA ID（如 `Asia/Tokyo`）；时间点保存 UTC epoch millis。

### 7.1 核心表

| 表 | 关键字段 | 约束/索引 |
|---|---|---|
| `trip` | `id`, `name`, `destination`, `start_date`, `end_date`, `zone_id`, `overview_title`, `version`, timestamps | 日期合法；`updated_at` 索引 |
| `trip_day` | `id`, `trip_id`, `local_date`, `sort_key`, `display_title`, `background_token`, `version`, timestamps | FK cascade；`unique(trip_id, local_date)` |
| `trip_section` | `id`, `day_id`, `type`, `display_title`, `sort_key`, `anchor_key` | FK cascade；类型与标题分离；`unique(day_id, anchor_key)` |
| `stop` | `id`, `day_id`, `place_ref_id?`, `sort_key`, `local_time?`, `note`, `status`, timestamps | FK cascade；`(day_id, sort_key)` 索引 |
| `place_ref` | `id`, `provider`, `provider_place_id?`, `name`, `address`, `lat?`, `lng?`, `resolution_status` | provider ID 可空；无坐标不生成标记 |
| `stop_content_block` | `id`, `stop_id`, `type`, `text?`, `media_id?`, `caption?`, `sort_key` | text/media 按类型互斥；FK cascade |
| `journal_entry` | `id`, `day_id`, `type`, `text?`, `media_id?`, `style_token?`, `sort_key`, timestamps | P0 顺序布局；变换字段预留给 P1 |
| `media_asset` | `id`, `owner_type`, `owner_id`, `local_path`, `mime_type`, size/duration/dimensions, `thumbnail_path?`, `state`, timestamps | 路径唯一；状态索引 |
| `advice` | `id`, `day_id`, `stop_id?`, `type`, `body`, `source_label?`, `source_url?`, `verified_at?`, `verification_status` | 动态信息必须有来源时间或待核实 |
| `trip_navigation_state` | `trip_id`, `expanded_node_ids_json` | 仅保存折叠偏好，不复制标题 |
| `editor_draft` | `scope_type`, `scope_id`, `field_key`, `content`, `updated_at` | 联合主键；成功写正式字段后删除 |

`overview_title` 属于 Trip；每日节点标题来自 `trip_day.display_title`；章节标题来自 `trip_section.display_title`。索引直接投影这些表，避免“重命名后多处同步”造成不一致。

### 7.2 AI 导入表

| 表 | 关键字段 | 说明 |
|---|---|---|
| `import_source` | `id`, `trip_id`, `type`, `display_name`, `local_uri?`, `raw_text_path?`, `status`, `error_code?`, timestamps | 保留原输入并可重试 |
| `import_job` | `id`, `source_id`, `remote_job_id?`, `idempotency_key`, `state`, `progress?`, `schema_version` | 客户端状态机 |
| `candidate_stop` | `id`, `source_id`, extracted fields, `place_ref_id?`, `confidence?`, `duplicate_of_stop_id?`, `review_state`, `target_day_id?` | 与正式 Stop 隔离 |
| `candidate_evidence` | `id`, `candidate_id`, `source_excerpt`, `page?`, `bounds?` | 支持来源追溯 |

确认操作使用单个 `@Transaction`：校验目标日期 → 创建/复用 `PlaceRef` → 按用户选择顺序插入 `Stop` → 标记候选已确认。重复点击用 candidate ID 保证幂等。

### 7.3 排序策略

P0 使用稀疏 `Long sort_key`（默认相邻间隔 1024），插入时取中值；无可用中值时仅对当前父节点的子项重新编号。移动地点、内容块、手账和章节均通过 Repository 的事务 API 完成，不允许 UI 自行写多个行号。

### 7.4 关系与删除

- 删除 `Trip` / `TripDay`：数据库子项级联删除，但媒体文件不能依赖 SQL 级联，应先生成待清理记录，再由 worker 幂等删除。
- 删除 `Stop`：确认对话展示其内容块和媒体数量；提交后在一个事务中删除关系并把媒体标成 `PENDING_DELETE`。
- 删除媒体：先解除引用，再延迟 10 秒执行物理删除，期间 Snackbar 可撤销；worker 仅删除已无引用的文件。
- `PlaceRef` 可被多个 Stop 使用，只有引用计数为零时才可清理。

## 8. Repository 与核心用例

```kotlin
interface TripRepository {
    fun observeTrips(): Flow<List<TripSummary>>
    fun observeTripOverview(tripId: TripId): Flow<TripOverview>
    fun observeDayDocument(dayId: TripDayId): Flow<TripDayDocument>
    suspend fun createTrip(command: CreateTrip): TripId
    suspend fun renameNode(command: RenameTripNode)
    suspend fun updateDayAppearance(dayId: TripDayId, background: BackgroundToken)
    suspend fun upsertStop(command: UpsertStop): StopId
    suspend fun moveStop(dayId: TripDayId, stopId: StopId, beforeId: StopId?)
    suspend fun confirmCandidates(sourceId: ImportSourceId, ids: Set<CandidateStopId>)
}
```

关键 UseCase：

- `CreateTripUseCase`：校验日期和时区，批量创建 `TripDay` 与固定类型章节。
- `RenameNavigationNodeUseCase`：按节点类型更新唯一字段，校验 trim 后 1..40 字符。
- `ObserveDayDocumentUseCase`：组合 day、section、stop、content、journal、advice 为不可变投影。
- `MoveStopUseCase`：事务调整顺序；地图和卡片自然随 Flow 更新。
- `AttachMediaUseCase`：校验 MIME/大小 → 复制临时文件 → 原子 rename → 写数据库。
- `DeleteStopUseCase`：返回影响摘要，确认后删除并调度孤儿媒体清理。
- `ConfirmImportCandidatesUseCase`：从草稿到正式实体的唯一入口。

Repository 将异常映射为稳定错误：`Validation`、`StorageFull`、`PermissionDenied`、`NetworkUnavailable`、`RemoteRejected`、`AmbiguousPlace`、`Unknown`。UI 不展示底层异常和服务端原文。

## 9. UI 状态与自动保存

### 9.1 状态模型

每个顶层页面只有一个 screen ViewModel：

```kotlin
data class DayDocumentUiState(
    val content: AsyncValue<DayDocumentUiModel>,
    val selectedStopId: String?,
    val activeOverlay: ActiveOverlay,
    val editor: EditorUiState,
    val saveState: SaveState,
    val pendingConfirmation: ConfirmationUiModel?,
)

sealed interface SaveState {
    data object Idle : SaveState
    data object Saving : SaveState
    data class Saved(val at: Instant) : SaveState
    data class Failed(val recoverable: Boolean) : SaveState
}
```

一次性动作（Snackbar、启动 Photo Picker、打开外部地图、请求权限）通过 effect channel 发出；持久状态不能放在 effect 中。

### 9.2 文本编辑策略

1. 键入时即时更新 Compose 输入态。
2. 每 400–700 ms 防抖写对应 `editor_draft`；停止输入后写正式字段并清除 draft。
3. `onStop`、切换日期、跳转章节和关闭编辑器前调用 `flush()`；flush 失败则保留 draft 并显示失败状态。
4. 进程重建时优先显示比正式字段更新的 draft，并标记“已恢复未完成编辑”。
5. 不使用“离开页面时一次性保存整份文档”。

列表排序、背景色、样式切换和删除等离散操作直接事务提交，不走文本防抖。

## 10. 媒体架构

### 10.1 导入流程

```text
Photo Picker / SAF 返回 Uri
  → 校验声明 MIME 与实际可解码类型
  → 复制到 cache/import/{uuid}.partial
  → 读取尺寸、时长、方向（限制资源消耗）
  → 生成缩略图/视频封面
  → 原子移动到 files/media/{mediaId}/original.ext
  → Room 事务写 MediaAsset + 所属内容块
```

不长期依赖第三方 `content://` 权限；选择完成后复制到应用私有目录。粘贴图片同样转成本地拥有的文件。数据库提交失败时删除新文件；文件提交成功但进程在写库前退出时，由孤儿扫描 worker 清理。

### 10.2 状态与限制

`MediaState = Importing / Ready / Failed / PendingDelete / Uploading / Uploaded`。P0 只要求前三个和待删除；上传字段用于 P1。

格式与大小上限在实现前配置为产品常量，UI 在选择前说明、选择后再次校验。视频不自动播放；列表只加载封面。缩略图和原图使用图像加载库的尺寸约束，避免在 Compose 中解码全尺寸图片。

### 10.3 工作调度

- 小图复制和元数据读取可在前台协程完成。
- 大视频复制、封面生成和未来上传使用 WorkManager；以 media ID 作为唯一工作名。
- WorkManager 不承担普通文本自动保存，也不用于精确计时。
- 存储不足时保留文字编辑，媒体项进入失败态并提供重试/移除。

## 11. 地图、地点与定位

### 11.1 领域边界

```kotlin
interface PlaceSearchRepository {
    suspend fun search(query: String, bias: SearchBias?): List<PlaceCandidate>
    suspend fun exploreNearby(origin: GeoPoint, category: String?): List<PlaceCandidate>
    suspend fun resolve(candidate: PlaceCandidate): PlaceResolution
}

interface LocationProvider {
    suspend fun currentLocation(): LocationResult
}
```

地点搜索结果必须先由用户确认，才转成 `PlaceRef + Stop`。无坐标或有歧义时保存为 `UNRESOLVED`，在地图列表显示“待定位”，绝不猜坐标。

### 11.2 地图抽屉状态

地图读取当前 `TripDayDocument.stops`，按 `sort_key` 生成 1..N 编号；编号不入库。`selectedStopId` 是文档与地图共享的 UI 状态：

- 点击 marker → 更新 ID → 卡片滚动到对应地点。
- 点击地点卡片 → 更新 ID → 地图相机移动到坐标。
- 关闭抽屉只改变 `activeOverlay`，不清空选择和文档列表状态。
- 地图 SDK 初始化/瓦片失败只替换地图区域；地点列表和“在 Google 地图打开”仍可用。

跳转使用 Google Maps URL，并同时提供浏览器/其他兼容 App 的 Intent chooser；优先带稳定 place ID，其次使用坐标和名称。不要把用户排序描述成路线优化结果。

### 11.3 权限

- 搜索目的地和打开已有地点不需要定位权限。
- 只有“以我为中心搜索”触发系统定位权限请求。
- 拒绝后记录本次说明已展示，不循环弹窗；用户可手选中心地点。
- 不申请后台定位；P0 不记录位置轨迹。

## 12. AI 导入客户端设计

### 12.1 输入处理

| 输入 | Android 处理 | 发送边界 |
|---|---|---|
| 文本 | 写私有草稿文件/Room 元数据 | 用户确认后发送必要文本 |
| 链接 | 校验 `https`、保留原 URL | Gateway 抓取；客户端不绕过登录/反爬 |
| 截图 | Photo Picker 导入并本地预览 | 明示后上传任务附件 |
| PDF | SAF `OpenDocument`，复制到私有存储 | 校验类型/大小后上传 |

### 12.2 状态机

```text
Draft → Queued → Uploading → Processing → ReviewReady
   └──────────────────────────────→ Failed(retryable?)
ReviewReady → PartiallyConfirmed → Confirmed
                         └────────→ Cancelled
```

每个任务生成稳定 `idempotencyKey`。网络重试复用同一 key，避免重复计费和重复候选。App 被杀后，通过 Room 中的 `remote_job_id` 继续轮询；P0 不无限后台重试，用户可主动恢复。

Remote DTO 必须经过 schema/version 校验再进入候选表。候选保留来源片段、置信/不确定标志、重复项和匹配地址。服务返回的内容永远不能直接覆盖正式行程。

### 12.3 安全边界

- 正式版只访问 goJapan Gateway；API key 不进入 `BuildConfig`、Room、DataStore 或日志。
- 上传前展示数据类型和目的；取消任务后停止后续上传/轮询。
- 崩溃、网络和分析日志禁止包含原文、候选备注、媒体内容、Authorization 和精确坐标。
- 客户端限制文件大小、任务并发和请求超时；服务端仍需再次校验。

## 13. 一致性、并发与恢复

- P0 单设备写入仍可能有多个协程；所有复合变更通过 Room `withTransaction` 串行保证原子性。
- Entity 带 `version` 和 `updated_at`。更新时可使用期望版本；版本不符则重新读取并提示“内容已更新”，不做静默 last-write-wins 覆盖。
- 日期调整必须在事务内重新计算 `TripDay.local_date/sort_key`，并保留 day ID，确保手账和地点关系不变。
- 后台 worker 只按 ID 读取最新状态，不能携带陈旧实体作为输入。
- 数据库 schema 必须导出并提交；每次版本升级提供显式 migration 和 instrumentation migration test，生产禁止 destructive migration。
- Android Auto Backup 对旅行媒体可能体积过大且隐私敏感：P0 默认排除媒体和导入原件；是否备份 Room 需在账号/同步策略确定后统一决策。

## 14. 性能策略

- 首页只查询 `TripSummary` 和 `TripDaySummary`，不加载正文和媒体。
- 日文档使用关系投影，但媒体只返回缩略图元数据；禁止把二进制写入 Room。
- 大列表使用稳定 key、不可变 UI model 和按章节分组的 Lazy 容器。
- 地图只在抽屉打开时组合和初始化；关闭时允许释放地图 UI 资源。
- 搜索输入 300 ms 防抖，取消上一请求；相同 query + bias 可短期缓存。
- Room 查询和文件 IO 不在 Main dispatcher；数据库索引覆盖所有 parent ID + sort key 查询。
- 目标：普通本地编辑可见反馈 < 100 ms；页面首个本地内容 < 500 ms；不以等待地图/AI 作为页面可用门槛。

## 15. 无障碍与系统交互

- marker 编号、地点卡片和索引节点提供“第 N 站、名称、状态”的完整语义。
- 展开/折叠、选中、保存失败、上传进度不能只靠颜色表达。
- 背景色只允许设计 token；根据 token 选择已验证对比度的前景色，不允许任意 RGB。
- 触控目标至少 48 dp；横向卡片可通过 TalkBack 和键盘逐项聚焦。
- 图片需要可编辑说明；视频提供封面说明、时长与明确播放按钮。
- IME 弹出时使用 window insets，保存/完成动作保持可达；抽屉有可读关闭按钮并响应系统返回。
- UI 测试至少覆盖 320、375、390、430 dp 等效宽度以及字体放大。

## 16. Analytics 与可观测性

使用强类型事件和字段 allowlist：

```text
trip_created(trip_id_hash)
day_document_opened(day_id_hash)
index_navigated(target_type)
map_overview_opened(stop_count, mapped_count)
media_attached(media_type, result)
save_result(scope_type, result, error_class)
import_state_changed(input_type, state, candidate_count)
candidate_confirmed(result, duplicate_flag)
```

只记录匿名/散列 ID、类型、计数、时长和结果。禁止记录名称、正文、地址、URL、来源片段、文件名、精确坐标、照片和视频。性能日志区分 local database、media、place、map、AI gateway，但不携带负载。

## 17. 测试策略

### 17.1 JVM 单元测试

- 日期/时区、标题校验、背景 token 和 section 类型约束。
- 稀疏排序的插入、移动、重编号和幂等。
- 保存状态 reducer、抽屉互斥、系统返回优先级。
- AI import 状态机、重复候选和确认规则。
- Maps URL 构造、无坐标分支、错误映射。

### 17.2 Room 与文件集成测试

- CRUD、外键、级联与 `TripDayDocument` 投影一致性。
- 迁移测试：旧版本数据库逐版迁到当前版本且内容不丢失。
- 确认候选、移动地点、删除地点与标记媒体为待清理的事务原子性。
- 进程中断模拟：`.partial`、孤儿文件、`PENDING_DELETE` 均可恢复或清理。

### 17.3 Compose UI 测试

- 首页 → 日期文档 → 索引切日/跳章节。
- 重命名同步、背景色恢复、保存失败后草稿仍在。
- 目录与地图抽屉互斥，Back 正确关闭并保留滚动/选择。
- marker/卡片双向选择，无坐标地点显示待定位。
- Photo Picker 使用 fake contract；媒体失败不会丢文本。
- TalkBack 语义、字体放大、横向卡片聚焦和 IME 场景。

### 17.4 端到端与故障注入

- 飞行模式、超时、HTTP 4xx/5xx、磁盘满、权限拒绝、地图 key 缺失。
- AI 任务重复提交、App 进程被杀、响应 schema 不兼容。
- 旋转、分屏、折叠/展开窗口和低内存恢复。
- Release 构建开启 R8 后的 Room、序列化、地图和媒体路径。

## 18. 从现有原型迁移

当前实现的主要差距：

| 现状 | 目标 | 迁移动作 |
|---|---|---|
| 单个 `TripPlan` JSON 存 DataStore | Room 多行程关系模型 | 增加数据库与一次性 importer |
| ViewModel 启动时一次性 `load()` | Repository `Flow` 持续观察 | 替换为 `observe*` 用例 |
| DeepSeek 结果直接覆盖整个计划 | 候选草稿逐项确认 | 移除 `saveSourceAndGenerate` 覆盖路径 |
| 手机端地图常驻 | 按钮打开地图抽屉 | 地图 UI 独立并延迟初始化 |
| 一个页面同时展示日期和地点 | 首页概览 + 每日文档 + 索引 | 引入明确 route 与 screen ViewModel |
| 无富媒体/手账持久层 | 私有媒体文件 + Room 元数据 | 建立 `MediaAsset` 管线 |
| API key 进入调试 APK | 生产只访问 Gateway | 将 `DeepSeekTripGenerator` 限制在 demo/debug 或删除 |

一次性迁移：

1. 新建 Room schema，版本独立于现有 KANA LINK 数据表但共用 `GoJapanDatabase` 或清晰的 trip database；优先共用单库、分 DAO。
2. 若 DataStore 存在 `active_trip_plan` 且 `trip_json_migrated=false`，读取并转换为一个 Trip、若干 TripDay/Stop/PlaceRef。
3. 在一个 Room 事务内写入；成功后设置迁移标志。不要立即删除旧 JSON，至少保留一个发布周期作为回滚读取源。
4. 映射失败时不覆盖旧值，记录不含正文的错误类别，并允许再次迁移。
5. 新 UI 只读 Room；验证稳定后再移除旧 Repository 与旧 key。

## 19. 分阶段交付

### M0：数据地基与只读体验

- Room schema、迁移、Repository、首页概览、每日文档只读、Navigation 3 route。
- 验收：旧原型数据成功导入；进程重启后内容一致。

### M1：编辑、索引与可靠保存

- 章节、重命名、背景、地点 CRUD/排序、draft 自动保存、错误反馈。
- 验收：切日、Back、进程回收不静默丢编辑；索引与正文同源。

### M2：媒体与手账

- Photo Picker/SAF、私有媒体、缩略图、地点内容块、照片/文字贴纸、删除撤销。
- 验收：多图、视频和文字在重启后恢复；存储失败可恢复。

### M3：地点、地图与定位

- Place 搜索/核对、周边、地图抽屉、marker 联动、Google Maps URL。
- 验收：权限拒绝和地图失败不影响文档；无坐标不显示假 marker。

### M4：AI 导入核对

- 四类来源、任务恢复、候选审查、来源证据、确认事务。
- 验收：未经确认永不写正式 Stop；重复请求和进程重建不重复写入。

## 20. 发布门槛

发布前必须满足：

- PRD P0 验收项全部映射到自动化测试或明确的人工测试用例。
- 所有 Room migration 从最早生产版本到当前版本通过。
- Release `lint`、单元测试、instrumentation 关键路径、`assembleRelease` 通过。
- APK/AAB 不含 AI 平台密钥；日志抽查不含用户正文、地址、精确坐标和媒体信息。
- 无网、定位拒绝、地图失败、AI 失败、存储不足下核心文档可打开且已有内容不丢。
- 320–430 dp、字体放大、TalkBack、IME、系统 Back 的关键路径通过。
- 图片/视频支持格式、大小、时长、保留与删除策略已由产品确认并写入 UI。

## 21. 待产品/技术决策

以下问题不阻塞架构，但会影响实现参数：

1. P0 是否允许完全无账号使用，以及未来登录后的本地数据归属。
2. 支持的图片/视频 MIME、单文件大小、视频时长和总存储上限。
3. Google Places/Maps 的预算、地区可用性和无 Google Play Services 设备策略。
4. AI Gateway 的认证、附件上传协议、任务轮询/SSE 形式和数据保留期。
5. Room 是否参与系统备份；媒体和导入原件建议默认不参与。
6. P1 同步的删除语义、冲突策略和端到端加密要求。

在这些决策完成前，接口与状态机按可替换实现设计，避免 UI 依赖具体 Provider SDK 或云端数据结构。

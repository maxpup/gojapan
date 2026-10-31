# GoJapan AI 多模型接入设计

版本：V1.0  
适用范围：Go Prepare、Go Plan / Go Today、Go Task、Go AI 与新增 AI Gateway  
设计参考：OpenClaw 的 `provider/model`、Agent 级模型策略、primary/fallback、utility model 与 provider adapter

产品定位：GoJapan 是面向日本自由行的 AI 旅行助手，不是日语学习软件；语言学习是旅行任务中的辅助能力。

## 1. 设计结论

goJapan 不直接照搬 OpenClaw 的配置界面，而是复用它的核心抽象：

1. 模型以 `provider/model` 唯一标识，禁止只存模型名。
2. 用户绑定的是“AI 服务”，模型是该服务下的可用能力。
3. 一个用户可以连接多个服务，但必须有且只有一个默认 AI 方案。
4. 模型选择分为“系统推荐”和“高级自定义”；普通用户不需要理解模型差异。
5. 每个业务 Agent 独立配置 primary、fallback 和 utility model。
6. fallback 只处理限流、超时、服务不可用等故障，不负责判断任务难度。
7. API Key 不落入 Room、DataStore、日志或分析事件；生产环境由服务端密钥库加密保存。
8. KANA LINK 的现有学习、复习、报告和音频继续离线可用，AI 是增强能力而非硬依赖。

首期建议优先上线两个用户可见入口：

- “分享至 GoJapan”：导入攻略、截图、链接或文字，生成可确认的旅行卡片。
- “AI 帮我排”：把候选卡片按地理位置、营业时间和已确定事项组成可编辑行程。

Go AI 不以独立聊天页作为产品中心。它首先存在于“导入、整理、编排、Today 和在途任务”这些工作流里；自由聊天是补充入口。

## 2. 产品概念映射

| OpenClaw 概念 | goJapan 产品表达 | 技术对象 |
|---|---|---|
| Provider | AI 服务 | `AiProvider` |
| `provider/model` | 服务下的模型 | `ModelRef` |
| Agent | 需要独立上下文与工具的旅行执行角色 | `AiAgent` |
| primary | 首选模型 | `ModelPolicy.primary` |
| fallbacks | 服务异常时备用 | `ModelPolicy.fallbacks` |
| utility model | 摘要、标题等轻任务模型 | `ModelPolicy.utility` |
| auth profile | 用户连接的服务凭证 | `ProviderConnection` |
| binding | 功能入口到 Agent 的固定映射 | `CapabilityBinding` |

面向用户避免出现 Agent、fallback chain、auth profile 等术语，统一使用“AI 方案”“首选模型”“备用模型”。

## 3. 用户动线

### 3.1 首次触发 AI

```text
从系统分享攻略/截图/链接至 GoJapan
  → 展示将被处理的数据与隐私提示
  → 选择使用方式
      ├─ goJapan AI（推荐）
      │    → 登录/创建账号
      │    → 自动选择模型
      │    → 解析并生成 Place Card 草稿
      └─ 使用自己的 API Key（高级）
           → 选择 AI 服务
           → 输入 Key / OAuth 授权
           → 测试连接
           → 获取模型列表
           → 选择“智能推荐”或指定模型
           → 解析并生成 Place Card 草稿
  → 用户核对名称、地点、来源和置信度
  → 确认加入候选池或某一天
```

产品原则：用户感知到的是“把攻略变成行程素材”，不是“选择一个模型聊天”。默认突出 goJapan AI，BYOK 放在“更多方式”中；模型选择不应成为核心闭环的前置漏斗。

### 3.2 绑定自己的 AI 服务

入口：`我的 → AI 设置 → 已连接的 AI 服务 → 添加服务`

步骤：

1. 选择 Provider：OpenAI、Anthropic、Google；后续可增加 OpenRouter、兼容 OpenAI API 的自定义服务和本地 Ollama。
2. 展示该服务会收到的数据类型、计费归属和隐私说明。
3. 通过 OAuth 或 API Key 建立连接；能用 OAuth 时优先 OAuth。
4. 客户端仅将凭证通过 TLS 发送到 Gateway；成功后只显示掩码与凭证尾号。
5. Gateway 使用轻量请求验证凭证，并抓取/合并模型目录。
6. 默认选择“智能推荐”；高级用户可选择 primary 和 0–3 个 fallback。
7. 保存后回到服务列表，展示状态、默认模型和最近验证时间。

连接状态：`验证中 / 可用 / 需要重新授权 / 额度不足 / 暂时不可用`。

### 3.3 高级用户切换模型

AI 设置及任务运行详情显示方案，例如“智能推荐”或“Claude Sonnet”。点击后打开底部面板：

- 智能推荐：由当前 AI 能力的策略选择模型。
- 最近使用：只展示已连接且当前可用的模型。
- 全部模型：按 Provider 分组。
- 仅本次任务使用：默认选项。
- 设为该能力默认：二次确认后修改 Agent 策略。

任务正在运行时不可直接切换；取消或完成后切换。对已生成结构化结果保留“使用模型、Prompt 版本和来源”，使结果可追溯。

### 3.4 多模型失败体验

```text
primary 请求
  ├─ 成功 → 展示回答
  ├─ 可重试错误 → 同一凭证短重试
  ├─ 凭证/额度错误 → 同 Provider 的另一可用连接
  └─ Provider/模型不可用 → 按顺序尝试 fallback
```

自动切换成功时不弹窗打断，仅在消息详情显示“首选模型暂不可用，本次由备用模型完成”。

所有模型均失败时展示：

- “AI 服务暂时不可用”，保留用户输入；
- “重试”与“更换模型”；
- BYOK 凭证问题提供“更新连接”；
- 不把 HTTP 状态码、Provider 原始报错或密钥信息直接暴露给用户。

### 3.5 旅行工作流与能力绑定

| 入口 | Workflow / Agent | 自动附带上下文 |
|---|---|---|
| 系统分享/粘贴导入 | `import_travel_note` | 原始来源、文本/图片、目标 Trip |
| 候选池“AI 帮我排” | `trip_planning` | Trip、日期、候选 Block、酒店、预约、偏好 |
| 拖动 Block 后即时反馈 | `route_evaluation` | 前后 Block、时间窗、距离和交通计算结果 |
| Today | `today_briefing` | 当前时间位置、今日 Block、附近收藏、异常信息 |
| Go Task“听对方说” | `scene_assistant` | Scene、Task、ASR 文本、预期回答类型 |
| 课程或表达“问 AI” | `language_helper` | 表达、场景、用户日语水平 |

客户端只发送完成任务所需的最小上下文，不默认上传完整 Room 数据或全部学习历史。

## 4. 信息架构与界面

### 4.1 导航建议

现有“首页、学习、复习、搜索、收藏”导航属于旧学习产品形态，应在整体产品 IA 设计中重新评估。AI 层本身不要求新增独立 Tab：

- Go Prepare 提供导入和候选池入口。
- Go Plan / Today 提供编排、路线反馈和当天摘要。
- Go Task 提供情境任务、播报和听取对方回答。
- Go AI 以跨页面动作、建议卡片和状态反馈出现，不强制成为聊天 Tab。
- “我的/设置”提供 AI 服务与模型管理。

### 4.2 AI 设置页

```text
AI 设置
├─ 默认方案
│   ├─ 智能推荐（默认）
│   └─ 固定模型
├─ 已连接的 AI 服务
│   ├─ goJapan AI · 正常
│   ├─ OpenAI · sk-••••1234
│   └─ + 添加服务
├─ 各能力模型（高级）
│   ├─ 攻略导入
│   ├─ 行程编排
│   ├─ Today 助手
│   └─ 场景语言
└─ 数据与隐私
    ├─ AI 数据使用说明
    ├─ 清除 AI 对话
    └─ 管理服务凭证
```

删除 Provider 连接前，先列出受影响的默认/备用模型；用户确认后将这些引用从策略中移除，并回退到 goJapan 默认方案。删除操作不影响本地学习数据。

## 5. 总体技术架构

```text
Android App
├─ Compose UI / ViewModel
├─ Travel Domain（Trip / Day / Block / Place / Source / Task）
├─ AI Workflow Client
│   ├─ ImportTravelNote
│   ├─ PlanTrip
│   ├─ EvaluateRoute
│   └─ AssistScene
├─ Local Store
│   ├─ Room：会话、消息、非敏感模型缓存
│   └─ DataStore：界面偏好、最近选择
└─ AiGatewayClient（SSE/HTTPS）
                │
                ▼
goJapan AI Gateway（Cloudflare Worker）
├─ Auth & User Resolver
├─ Capability Binding Resolver
├─ Model Policy Resolver
├─ Credential Vault Adapter
├─ Provider Registry / Model Catalog
├─ Failover Engine / Circuit Breaker
├─ Workflow Orchestrator
├─ Prompt & Context Builder
├─ Structured Output Validator
├─ Place Resolution / Geocoding Adapter
├─ Safety / Quota / Rate Limit
├─ Usage & Audit（无明文 prompt）
└─ Provider Adapters
    ├─ Anthropic Messages
    ├─ OpenAI Responses
    ├─ Google Gemini
    └─ OpenAI-compatible
                │
                ▼
       各模型供应商 API
```

部署建议：

- Worker：无状态 API、SSE 转发、策略解析。
- D1：用户、旅行领域数据、连接元数据、Workflow 策略、AI Job 与用量账本。
- 加密凭证：使用独立 secrets/KMS 服务封装；数据库只存密文、key version 和尾号。Worker 环境密钥只能用于包裹/解包，不能返回客户端。
- Durable Object（可选）：按 Trip/Job 串行化更新、断线恢复与并发控制。
- R2（可选）：语音或较大附件；默认不保存原始附件。

Android 不直接调用第三方云模型。否则 APK 无法保护平台密钥，BYOK 也难以实现统一撤销、审计、fallback 和协议转换。仅本地模型连接可在设备内直连，并明确标记为实验能力。

## 6. 核心领域模型

```kotlin
@JvmInline
value class ModelRef(val value: String) // "anthropic/claude-sonnet-4-6"

enum class AiCapability {
    STRUCTURED_OUTPUT, VISION, REASONING, EMBEDDING,
    TRANSCRIPTION, SPEECH, TRANSLATION
}

enum class AiWorkflow {
    IMPORT_TRAVEL_NOTE, TRIP_PLANNING, ROUTE_EVALUATION,
    TODAY_BRIEFING, SCENE_ASSISTANT, LANGUAGE_HELPER
}

data class ModelPolicy(
    val primary: ModelRef,
    val fallbacks: List<ModelRef>,
    val utility: ModelRef?,
)

data class ProviderConnection(
    val id: String,
    val providerId: String,
    val status: ConnectionStatus,
    val credentialHint: String?,
    val lastVerifiedAt: Instant?,
)
```

约束：

- `ModelRef` 必须包含且仅包含受支持的 provider/model 组合。
- 模型展示名、能力和价格来自服务端 catalog，不能作为业务主键。
- App 保存的 policy 是服务端 policy 的只读缓存；服务端是授权与路由真源。
- Provider 删除、模型下线后，Gateway 在 catalog 校验阶段修复无效引用并返回可解释状态。

## 7. 服务端数据模型

### 7.1 关键表

```text
users
- id, auth_subject, region, created_at

provider_connections
- id, user_id, provider_id, auth_type
- encrypted_credential, key_version, credential_hint
- status, last_verified_at, created_at, revoked_at

model_catalog
- provider_id, model_id, display_name
- capabilities_json, context_window, lifecycle_status
- discovered_at, expires_at

workflow_policies
- id, user_id, workflow
- primary_model_ref, fallback_refs_json, utility_model_ref
- mode(managed|byok|custom), version, updated_at

ai_jobs
- id, user_id, trip_id, workflow, status
- input_refs_json, prompt_version, output_schema_version
- created_at, started_at, completed_at

ai_artifacts
- id, job_id, artifact_type, payload_json
- confidence_json, source_refs_json, review_status, created_at

ai_runs
- id, user_id, job_id, requested_model_ref, resolved_model_ref
- attempt_count, fallback_reason, input_tokens, output_tokens
- latency_ms, error_class, created_at
```

`provider_connections` 对 `(user_id, provider_id, id)` 建索引；所有读取必须同时带 `user_id`，防止越权。模型目录是系统级缓存，凭证可见模型目录需要额外按 connection scope 缓存。

### 7.2 凭证安全

- API Key 写入后不可读取，只允许替换或撤销。
- 数据库不保存明文；使用 envelope encryption：每条凭证数据密钥加密，主密钥由 KMS/Secrets 管理。
- 日志、崩溃上报、Analytics、Sentry breadcrumb 禁止记录 Authorization、prompt、response 和完整模型请求体。
- 凭证解密仅发生在 Provider Adapter 发请求前的最短生命周期内。
- 提供撤销、轮换、最后使用时间和异常使用提醒。
- 自定义 Base URL 默认禁止内网、metadata IP、重定向到私网，避免 SSRF。

## 8. Gateway 请求与路由

### 8.1 客户端接口

```http
GET  /v1/ai/providers
POST /v1/ai/provider-connections
POST /v1/ai/provider-connections/{id}/verify
DELETE /v1/ai/provider-connections/{id}
GET  /v1/ai/models
GET  /v1/ai/policies
PUT  /v1/ai/policies/{workflow}
POST /v1/ai/import-jobs
GET  /v1/ai/jobs/{id}
POST /v1/ai/jobs/{id}:confirm
POST /v1/ai/trips/{id}:plan
POST /v1/ai/routes:evaluate
POST /v1/ai/scenes:assist
```

创建导入任务示例：

```json
{
  "tripId": "trip_123",
  "sourceId": "source_456",
  "workflow": "IMPORT_TRAVEL_NOTE",
  "modelOverride": null,
  "idempotencyKey": "uuid"
}
```

客户端只传领域 ID。Gateway 根据授权读取对应的白名单内容，避免客户端伪造超长系统上下文或覆盖系统提示词。

### 8.2 解析顺序

```text
显式的本次任务 modelOverride（且在用户 allowlist 中）
  → 用户针对 workflow 的 Model policy
  → goJapan 针对 workflow 的默认 policy
  → 系统安全默认模型
```

得到 primary 后：

1. 校验模型是否可用、连接是否属于当前用户、模型能力是否满足任务。
2. 在同 Provider 内优先处理瞬时重试和可用认证连接。
3. 仅对允许 fallback 的错误进入下一模型。
4. 内容安全拒绝、参数错误、上下文超限等不可重试错误不得换模型绕过。
5. 每次尝试写入 `ai_runs`，但不记录密钥和明文内容。

错误分类建议：`AUTH / QUOTA / RATE_LIMIT / TIMEOUT / PROVIDER_5XX / INVALID_REQUEST / CONTEXT_LIMIT / SAFETY / NETWORK`。

### 8.3 Provider Adapter 契约

```typescript
interface AiProviderAdapter {
  listModels(connection: ResolvedConnection): Promise<ModelDescriptor[]>;
  validate(connection: ResolvedConnection): Promise<ValidationResult>;
  stream(request: CanonicalAiRequest): AsyncIterable<CanonicalAiEvent>;
  classifyError(error: unknown): ProviderError;
}
```

内部统一事件：`job.started / progress / artifact.delta / tool.call / usage / job.completed / error`。Android 只处理统一的 Job/SSE 协议，不感知 Anthropic、OpenAI 或 Gemini 的流式差异。

## 9. 智能选择策略

MVP 不做不可解释的 LLM 路由器，使用规则策略：

```text
攻略文本提取 → structured_output
截图/菜单/标识理解 → vision + structured_output
行程编排 → reasoning + tool_capable
相似收藏与聚类 → embedding
听对方说 → transcription + scene-constrained reasoning
日语播报 → speech
标题、摘要 → utility
用户显式指定 → 指定模型
```

候选模型再按以下硬条件过滤：连接可用、能力匹配、地区合规、剩余额度、上下文容量。最后按管理员配置的质量档、单位成本和近期健康度打分。

不要在首期根据“问题看起来难”自动从廉价模型升级到昂贵模型；这会带来费用不可预测和结果难复现。需要升级时，由用户点击“用更强模型重答”，或由特定 Agent 固定使用更强模型。

## 10. 离线、同步与隐私边界

- KANA LINK、课程浏览、收藏、复习调度继续完全离线。
- 无网络时 AI 入口显示离线状态，用户输入保存为草稿，不自动无限重试。
- AI 会话可在 Room 缓存，以提升启动速度；同步前必须有账号和隐私确认。
- 个性化信息只使用任务所需摘要，例如旅行偏好、步行承受度和饮食限制；不默认上传完整收藏、位置轨迹或学习历史。
- 用户可分别关闭“使用旅行偏好”“使用当前位置”“使用学习进度”；关闭后 Prompt Builder 不读取相应数据。
- 供应商数据保留策略在连接前展示；BYOK 不等于数据不经过 goJapan Gateway。

## 11. 可观测性与费用控制

必须记录：resolved model、fallback 次数、首 token 延迟、总延迟、输入/输出 token、错误分类和估算费用。

不得记录：API Key、Authorization header、完整 prompt、完整 response、用户输入原文。

保护措施：

- 用户、设备、Provider、模型四级限流。
- managed 模式按套餐设置日/月预算。
- BYOK 模式仍限制并发和请求体尺寸，防止 Gateway 被滥用。
- circuit breaker 在 Provider 连续故障时临时摘除模型。
- `Idempotency-Key` 防止 Android 网络重试造成重复计费。
- 每次生成设置最大输入、最大输出、总时长和工具调用轮数。

## 12. 分阶段落地

### Phase 0：架构准备

- 增加账号体系和 Gateway 基础认证。
- 定义 Canonical AI Request/Event 与 Provider Adapter。
- 接入一个 managed Provider；完成 SSE、限流、审计和费用账本。
- Android 增加 AI domain/data/ui 包，但不修改 KANA LINK 离线链路。

验收：单模型端到端可对话；密钥不进入客户端和日志；断网不影响现有学习功能。

### Phase 1：攻略导入闭环

- Android Sharesheet、截图/链接/文字导入、处理状态与隐私开关。
- `ImportTravelNoteWorkflow`：清洗、分类、Place 提取、地址标准化、地理编码、摘要、标签、Source 追溯。
- 结构化输出必须经过 schema 校验；用户确认后才写入正式 Place Card。
- 系统默认模型 + utility model，不向普通用户展示模型设置。

验收：真实攻略可稳定生成可编辑 Place Card；原始来源可追溯；错误提取可修正；任务取消、重试和幂等可测试。

### Phase 2：用户绑定模型

- OpenAI、Anthropic、Google BYOK。
- 添加/验证/更新/撤销连接。
- 模型 catalog、会话级切换、primary/fallback 配置。
- 凭证 envelope encryption 与异常审计。

验收：删除连接后无法再次调用；失效模型可自动修复；fallback 仅在允许的错误类型触发。

### Phase 3：行程智能与在途能力

- Trip Planning、Route Evaluation、Today Briefing 与 Scene Assistant。
- Subagent/并行任务仅用于确有收益的复杂场景。
- 按能力、成本、健康度的规则路由；增加 OpenRouter/自定义兼容 Provider。

验收：策略可解释、费用有上限、每次实际使用模型可追踪。

## 13. 不建议的方案

- 不把平台统一 API Key 写入 APK 或 Remote Config。
- 不把用户 API Key 明文放入 Room、DataStore、SharedPreferences 或 Android Keystore 后直接长期调用第三方；Keystore 能保护本机静态数据，但不能解决代理、审计、跨设备和统一撤销问题。
- 不允许客户端自由传 system prompt、Provider URL 或任意工具定义。
- 不用 fallback 做质量升级或安全拒绝绕过。
- 不让所有 AI 请求读取完整学习历史。
- 不因为引入 AI 而改变 KANA LINK 的离线可用承诺。

## 14. 建议的 Android 包结构

```text
com.gojapan/
├── core/ai/
│   ├── api/             AiGatewayClient、SSE 解析
│   ├── model/           ModelRef、CanonicalAiEvent
│   ├── repository/      AiRepository 接口与实现
│   └── security/        本机 session token 管理
└── feature/ai/
    ├── chat/            对话 UI / ViewModel
    ├── settings/        Provider 与模型设置
    ├── domain/          capability、policy、use case
    └── context/         课程/复习上下文最小化映射
```

AI 页面继续遵循项目现有 Compose 单向数据流：UI 只消费不可变 `UiState`，ViewModel 调用 UseCase，Repository 负责本地缓存与 Gateway；Provider SDK 不进入 UI 或 feature 层。

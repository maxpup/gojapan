# GoJapan 小红书网页攻略数据设计

版本：V2.6  
范围：纯 Android 应用；一篇小红书网页经 AI 识别后生成一份可阅读、可编辑的攻略。

## 1. 目标

用户提供小红书攻略网页链接，应用取得页面中可读取的标题、正文和图片说明，交给 AI 提取，生成结构化攻略。攻略可包含地点、吃住行建议、费用、提醒和作者给出的行程建议。攻略本身不是用户的每日行程，不自动创建 `TripDay` 或 `Stop`。

```text
小红书网页链接 → 取得网页内容 → AI 识别 → 校验 → 保存攻略 → 展示与编辑
```

这条流程没有独立的“导入任务”业务对象。处理状态由页面 ViewModel 持有；失败时保留链接和已取得的文字以供重试。只有成功生成的攻略长期保存。若将来需要后台批量处理，再设计持久化任务队列。

网页可能无法在应用中直接读取。此时让用户粘贴网页正文或从系统分享文字；不能把仅有 URL 的输入当作识别成功。首版处理实际取得的文字及图片说明，图片和视频识别留待后续。

## 2. 名词

| 名称 | 含义 | 本设计中的用途 |
|---|---|---|
| `Guide` / 攻略 | 一篇整理后的旅游内容 | 核心对象 |
| `GuideSection` / 章节 | 如“浅草半日游”“交通建议” | 保留攻略的阅读结构 |
| `GuideBlock` / 内容块 | 章节中的段落、地点、美食等卡片 | 构成完整正文及展示顺序 |
| `GuidePlace` / 攻略地点 | 网页提到的景点、餐厅或酒店等 | 无需安排日期或时间 |
| `SourceExcerpt` / 来源片段 | 支持某条信息的网页原话 | 可选溯源字段；原名 `Evidence` |
| `Stop` / 行程站点 | 用户行程中某天安排的一站 | 仅属于后续“加入行程”功能 |

例如“浅草寺值得早上去”是一条攻略地点及建议；只有用户决定“周一 09:00 去浅草寺”时，才会生成 `Stop`。

## 3. Room 数据结构

`GuideEntity.content` 保存已确认的结构化攻略正文，`draftContent` 保存尚未确认的编辑副本。两者都用 `String` 列存版本化 JSON，由 Kotlin Serialization 解码为 `GuideContentV1`。标题、摘要等列表页需要查询的字段单独放列中；编辑草稿时不覆盖已确认内容。

### `GuideEntity`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | UUID | 本地攻略 ID |
| `sourcePlatform` | String | 首版固定 `XIAOHONGSHU` |
| `sourceUrl` | String | 用户提供的网页链接 |
| `sourceTitle`、`sourceAuthor` | String? | 网页可读取时记录 |
| `sourceText` | String | 实际给 AI 的文字；保留原文 |
| `sourceContentHash` | String | 重复识别提示 |
| `title` | String | AI 整理后的标题，可编辑 |
| `summary` | String | 简短摘要，可编辑 |
| `content` | String | 已确认的 `GuideContentV1` JSON：章节及攻略核心对象 |
| `draftContent` | String? | 待确认的 `GuideContentV1` JSON；`null` 表示没有编辑草稿 |
| `destination` | String? | 如“东京”，不能确定则为空 |
| `tags` | List<String> | Room 可用 JSON 类型转换器 |
| `createdAt`、`updatedAt` | Instant | 本地时间戳 |
| `extractorVersion` | Int | AI 输出格式版本 |

### `GuideContentV1`（`content` 的结构）

```text
GuideContentV1
└── sections[]：章节类型、标题、blocks[]
    ├── PARAGRAPH：正文段落
    ├── PLACE：地点
    ├── SUGGESTION：建议
    ├── TODO：待办
    ├── IDEA：想法
    └── FOOD_RECOMMENDATION：美食推荐
```

数组顺序就是阅读顺序。每个 block 有稳定的本地 `id`、`type`，以及恰好一个与类型对应的对象；还可附 `sourceExcerpt` 和 `needsReview`。AI 输出时不生成 ID，由应用保存前生成。编辑草稿时沿用已有 ID，便于追踪同一对象。AI 识别阶段不要求地图地点 ID 或坐标，避免 AI 猜测位置。

### 攻略核心对象

| 对象 | 用途 | 主要字段 |
|---|---|---|
| `GuidePlace` 地点 | 值得去或网页提到的具体位置 | `name`、`nameJa?`、`category`、`area?`、`addressText?`、`description?`、`visitTip?`、`googleMatch?`、`amapMatch?` |
| `GuideSuggestion` 建议 | 对旅行做法的推荐或提醒，不表示用户必须执行 | `text`、`category`（交通/时间/预算/餐饮/避坑/其他）、`relatedPlaceId?`、`validityNote?` |
| `GuideTodo` 待办 | 可完成的一项行动，例如“预订门票” | `title`、`details?`、`relatedPlaceId?`、`dueDate?`、`status`（待办/完成/取消） |
| `GuideIdea` 想法 | 值得保留但尚未决定的构想，例如“雨天改去美术馆” | `text`、`relatedPlaceId?`、`status`（待考虑/已采纳/已放弃） |
| `GuideFoodRecommendation` 美食推荐 | 某家店及值得吃的菜品 | `shopName`、`recommendedDishes[]`、`images[]`、`relatedPlaceId?`、`area?`、`note?` |

`GuideSuggestion` 是内容性的建议，`GuideTodo` 是可勾选的动作，`GuideIdea` 是尚未决定是否采用的设想。同一句话如“建议提前预订门票”，AI 可以提取为建议；只有网页明确列出要做的事，或用户手动转为待办，才创建 `GuideTodo`。AI 从原文提取的待办初始状态固定为 `PENDING`，想法为 `OPEN`；模型不能声称用户已完成或已采纳。

`relatedPlaceId` 引用同一份 `content` 中 `PLACE` block 的本地 ID，允许为空。来源原话放在 block 的 `sourceExcerpt`，不在各对象中重复存一份。需要修改状态时更新 `draftContent`；用户确认后再写入 `content`。

`GuideFoodRecommendation` 的 `shopName` 必填，`recommendedDishes[]` 是有序菜品列表，每项包含 `name`、可选 `reason`（好吃在哪里）和可选 `priceText`（原文提及的价格）。`images[]` 也是有序列表，每项包含 `uri`、`sourceUrl?`、`caption?`、`mimeType?`、`origin`（`SOURCE_PAGE` / `USER_ADDED`）。JSON 只存图片引用和说明，不存图片二进制；用户自行添加的图片应复制到应用私有存储，网页图片只引用实际取得且可使用的地址。AI 不得编造图片链接或菜品。

美食推荐可通过 `relatedPlaceId` 关联已有餐厅地点，沿用该地点的 Google / 高德匹配结果；若网页只提到店名，先保留 `shopName` 与区域线索，后续再匹配地图。店名或推荐菜品存在歧义时将 block 标记 `needsReview`。

### 地点的双地图匹配

同一个 `GuidePlace` 保存两份相互独立的匹配结果：`googleMatch` 与 `amapMatch`。每份结果包含：

| 字段 | 说明 |
|---|---|
| `status` | `UNMATCHED`、`AMBIGUOUS`、`CONFIRMED`、`NOT_FOUND` |
| `placeId` | 对应服务的地点 ID；只有确认匹配后才填写 |
| `matchedName`、`matchedAddress` | 对应服务返回的名称和地址 |
| `latitude`、`longitude` | 对应服务返回的坐标，必须同时存在或同时为空 |
| `matchedAt` | 最后确认时间，供后续重新核对 |

Google 的 Place ID 与高德的 POI ID 各自保存，不能互相代替；坐标也跟随各自的匹配结果保存，不设一个会被切换覆盖的公共 `latitude/longitude`。用户切换地图时读取对应结果。某一方尚未匹配时只显示“待定位”，不拿另一方的 ID 假装已匹配。日本地点能否由高德匹配，还取决于该 Key 的海外服务权限。

地图搜索返回的候选先写入 `draftContent` 供用户核对；确认具体地点后才将对应 `status` 设为 `CONFIRMED` 并提交到 `content`。小红书网页的地点名称、地址线索与两套地图返回值分别保留，便于发现同名店铺误配。

首版以整篇攻略读取和编辑为主，JSON 列足够。将来需要按地点跨攻略搜索，再从已确认的 `content` 派生地点索引表；索引是可重建数据，不能成为另一份可编辑正文。

### 草稿状态

- 开始编辑：若 `draftContent == null`，复制当前 `content` 到 `draftContent`；已有草稿则继续编辑该草稿。
- 编辑中：自动保存只更新 `draftContent` 和 `updatedAt`，已确认的 `content` 不变；详情页提示“有未确认编辑”。
- 确认更新：先校验草稿 JSON，再在一次 Room 事务中将 `draftContent` 赋给 `content`、清空 `draftContent`，并更新 `updatedAt`。
- 放弃编辑：清空 `draftContent`，恢复展示 `content`。此操作会丢弃未确认编辑，界面需明确提示。
- 应用重启：若草稿非空，重新打开编辑页时恢复草稿；普通攻略详情仍展示已确认的 `content`。

若标题、摘要等元数据也允许在“确认”前编辑，应一并放进草稿 JSON，确认时再同步更新 `GuideEntity.title`、`summary` 等展示列，避免正文与列表标题版本不一致。

## 4. AI 输出格式

模型只描述网页里能找到的内容，不生成数据库 ID、坐标、正式行程或不存在的日期。

```json
{
  "schemaVersion": 1,
  "title": "东京浅草与上野攻略",
  "summary": "从浅草寺出发，逛上野周边，包含交通与餐饮建议。",
  "destination": "东京",
  "tags": ["景点", "美食"],
  "sections": [
    {
      "title": "浅草半日游",
      "type": "PLACES",
      "blocks": [
        {
          "type": "PARAGRAPH",
          "text": "作者建议乘地铁前往浅草。",
          "sourceExcerpt": "坐地铁到浅草"
        },
        {
          "type": "PLACE",
          "place": {
            "name": "浅草寺",
            "nameJa": null,
            "type": "ATTRACTION",
            "area": "浅草",
            "addressText": null,
            "description": "作者建议从雷门进入。",
            "visitTip": "建议早上到达。",
            "googleMatch": null,
            "amapMatch": null
          },
          "sourceExcerpt": "早上去浅草寺，从雷门进去"
        },
        {
          "type": "SUGGESTION",
          "suggestion": {
            "text": "建议早上游览浅草寺。",
            "category": "TIME",
            "relatedPlaceId": null,
            "validityNote": null
          },
          "sourceExcerpt": "早上去浅草寺"
        },
        {
          "type": "TODO",
          "todo": {
            "title": "出发前查看浅草寺开放信息",
            "details": null,
            "relatedPlaceId": null,
            "dueDate": null,
            "status": "PENDING"
          },
          "sourceExcerpt": "出发前记得查一下开放信息",
          "needsReview": true
        },
        {
          "type": "IDEA",
          "idea": {
            "text": "如果时间充足，可以顺路逛上野。",
            "relatedPlaceId": null,
            "status": "OPEN"
          },
          "sourceExcerpt": "有空可以顺路逛上野",
          "needsReview": false
        },
        {
          "type": "FOOD_RECOMMENDATION",
          "foodRecommendation": {
            "shopName": "浅草某店",
            "area": "浅草",
            "relatedPlaceId": null,
            "recommendedDishes": [
              { "name": "抹茶甜点", "reason": "作者推荐", "priceText": null }
            ],
            "images": [
              {
                "uri": "https://example.invalid/source-image.jpg",
                "sourceUrl": "https://example.invalid/source-image.jpg",
                "caption": "店内甜点照片",
                "mimeType": "image/jpeg",
                "origin": "SOURCE_PAGE"
              }
            ],
            "note": null
          },
          "sourceExcerpt": "在浅草某店吃了抹茶甜点",
          "needsReview": false
        }
      ]
    }
  ]
}
```

以上 JSON 展示对象形状，图片 URL 是格式示例，不应由模型生成；只有网页提取器实际取得的图片引用才可写入。没有来源片段的待办和想法只能由用户手动新增，或作为明确标记的 AI 推断草稿，不能在首次识别时当作网页事实写入已确认的 `content`。AI 返回内容经本地校验后，Repository 生成 block ID 并序列化到 `GuideEntity.content`。

本地校验：标题和摘要不能为空；章节、内容块列表允许为空；块类型与对象字段必须一一对应；地点名称、店名、菜品名、待办标题和建议/想法正文不能为空；`relatedPlaceId` 必须引用本攻略的地点块；枚举值必须受支持；`sourceExcerpt` 必须能在原文中找到，找不到就清空并标记待核对；图片 `uri` 必须来自实际提取结果或用户添加的文件；地图匹配的坐标须成对出现，`CONFIRMED` 状态须有对应服务的 `placeId`；价格、营业时间和交通时刻即使有原文，也要提示可能过时。原文与整理结果分开保存，用户修改攻略不改写来源。

## 5. 页面流程

1. 用户在“攻略”入口粘贴小红书网页链接。
2. 应用读取可获取的网页标题和正文，展示将交给 AI 整理的内容预览。
3. 用户点击“生成攻略”，页面显示获取中、识别中、保存中状态。
4. AI 返回结构化数据，本地校验通过后，保存 `GuideEntity`，将完整攻略正文写入 `content`。
5. 打开攻略详情：标题、摘要、按顺序排列的章节正文、地点卡片和原网页链接；用户可编辑和删除。
6. 网页读取失败时保留链接，提示粘贴正文后重试；AI 失败时保留已取得的文字。

同一来源再次识别时提示“已有攻略”。用户可查看现有攻略或重新生成；重新生成结果先写入 `draftContent`，由用户确认后替换 `content`，避免误覆盖编辑过的内容。

## 6. 与现有行程代码的关系

当前 `DeepSeekTripGenerator` 返回 `TripPlan`，`TripPlannerViewModel.saveSourceAndGenerate()` 立即保存并覆盖活动行程。实现攻略功能时应新增 `GuideExtractor` 和 `GuideRepository`，将“生成攻略”入口接到攻略页面。`TripPlan` 可以继续服务行程演示，但不作为网页攻略的存储格式。

未来用户选择“加入行程”时，再从 `content` 的 `PLACE` 块创建某个 `TripDay` 下的 `Stop`，由用户确定日期、时间和顺序。此动作与 AI 识别网页生成攻略分开。

## 7. 首版验收

- 输入可读取的小红书网页内容后生成独立攻略，应用重启后仍可查看。
- 攻略包含标题、摘要、章节、段落，以及地点、建议、待办、想法、美食推荐；顺序与生成结果一致，且能打开原网页。
- 美食推荐可展示店名、推荐菜品和多张有序图片；缺少图片时仍可保存店名与菜品。
- 模型未提到的地址、坐标、日期不被补写。
- 同一地点可分别保存 Google 与高德的地点 ID、地址和坐标；切换地图不会覆盖另一方结果。
- 网页或 AI 失败时，链接和已取得的文字仍可重试。
- 生成攻略不会修改现有行程。
- 用户能编辑攻略内容，来源原文保持不变。
- 未确认编辑写入 `draftContent`；退出并重进可恢复，确认后成为 `content`，放弃后已确认内容保持原样。

# 其他 issue 修复与验收

本轮针对 Android 客户端。未向 GitHub 发送评论或关闭 issue。

| Issue | 本地处理 | 尚需确认或验收 |
| --- | --- | --- |
| #1 词语分割 | 搜索与关卡预览共用固定词元数据，含原文跨度、读音、词性、释义；点词不播放，独立按钮朗读；无配置时保留整句 | 固定词条覆盖当前常用表达，并非任意日语分词器；新增内容仍需日语编辑复核 |
| #2 语法 | 单独展开原文片段的讲解，含 は、どこ、ですか、を、お願いします、ください、もらえますか；每项有旅行替换例句与中文解释 | 无配置显示明确提示；不提供任意输入语法分析 |
| #3 播放规则 | 取消搜索逐字自动朗读和配对选卡朗读，增加独立播放、全局停止、持久化全局静音；退到后台停止 TTS 和假名录音 | 原始反馈语义仍未明确；实机验证静音重启与页面切换 |
| #4 配色 | 继续推进后，暖白/青绿主题已接入客户端亮暗模式；补齐文字、按钮、容器、错误、边框等语义颜色与全局背景 | 固定语义主题，不启用系统动态颜色；仍需设备上检查布局和系统栏 |
| #5 进度 | 首页/场景目录显示已通过关卡；题号、已答数、含追加错题的分母分别明确；保存关卡快照、恢复已选答案及追加错题；区分已学/掌握 | 验证真实设备杀进程/重启、切页恢复；强化子集练习不会覆盖整关通过记录 |
| #7 日语语音 | 统一日语 voice 策略，拒绝中文/英文及明确未安装 voice；优先离线，兼容已确认的联网语音与旧式语言接口；内置录音优先 | 实际出声与发音质量沿用 JAPANESE_AUDIO_ACCEPTANCE.md 的真人听音清单，不能仅凭测试判定 |

## 播放触发规则

| 入口 | 发声操作 | 不发声操作 |
| --- | --- | --- |
| 搜索 | “播放整句”或选词后的“播放该词” | 搜索、点词查看、收藏、加入复习 |
| 关卡预览 | 播放整句/已选词 | 进入页面、查看语法 |
| 场景听力 | “播放/重播” | 进入题目、切题、选择答案 |
| 假名复习/核心练习 | 听力题的“播放/重播” | 进入、切题、选择答案 |
| 假名配对 | 选中卡片后单独点击“播放选中的假名” | 选择/匹配卡片不触发朗读；答题反馈音由原有音效设置及全局静音共同控制 |
| 五十音点读图 | 点读假名卡（页面明确标注“点读”） | 进入、切换行/字形 |

全局静音覆盖 TTS、内置假名录音和答题提示音，重启后保留；恢复声音不会自动重播。全局停止不会改变静音偏好。关卡切题停止旧 TTS；关闭页面释放播放器；退到后台停止播放。

## 进度统计口径

- 关卡分子：已通过的不同关卡 ID；弱项子集练习不算整关通过，未完成退出不计。
- 关卡题目进度：已答题数 / 已生成的基础、挑战、错题总数。当前末题答错时提前把待追加的重试题计入分母，避免提前出现 100%。
- 首次练习正确率不含追加重试，最终挑战单独计算。
- 已学表达：完成关卡后写入的答题表达 ID，并兼容原有答题历史；以集合去重。收藏、搜索、播放均不计入。
- 掌握表达：复习记忆等级达到第 3 级（level >= 2），不以单次播放或单次正确推断掌握。
- 学习快照保存完整题目、已答位置、当前答案和错题证据，恢复时不重抽题。旧版本/损坏/无效快照忽略，不使应用崩溃。

## 自动验证

新增回归覆盖：词元原文跨度、は的读音、语法例句、长音/促音/拗音/汉字、标点和缺失数据的回退；最后一题未答/答错的进度；含错题与已选答案的快照往返；损坏快照；真实 Preferences DataStore 关闭后重新创建的恢复、重复学习去重、最好分数保留、收藏不算已学。

配色草案由 Chromium 实际渲染并检查亮暗切换；截图是设计草案，不是 Android 实机截图。

上一轮检查（2026-11-10）：

- `testDebugUnitTest assembleDebug lintDebug assembleRelease`：BUILD SUCCESSFUL。
- 89 项单元测试，0 failures、0 errors、0 skipped。
- Debug Lint：0 errors、54 warnings、1 hint；release 关键 Lint 通过。
- `git diff --check` 通过。
- 最终构建日志：`/workspace/other-issues-checks.log`。
- Debug APK：`android/app/build/outputs/apk/debug/app-debug.apk`；release APK 为 `android/app/build/outputs/apk/release/app-release-unsigned.apk`（未签名）。
- 配色预览：`design-assets/travel-palette-preview.html`，亮暗截图为 `travel-palette-light.png` 和 `travel-palette-dark.png`。


## 继续推进（2026-11-10）

- 已接入暖白/青绿亮暗主题。`ThemeContrastTest` 直接检查客户端 ColorScheme 的 14 组前景/背景颜色，两种模式的普通文本对比度均须 >= 4.5:1。正确/错误保留文字和符号，并使用主题语义颜色。
- 完成关卡改为单次 DataStore 事务，统一保存复习加入、答题历史、已学 ID、最好分数、通过状态和快照清理。同一 session 的重复提交不重复记账；旧 session 完成不删除较新快照；已完成 session 的迟到保存不会重新出现为待继续。
- 快照增加 session ID，并核对原始题目、追加错题、计数、当前答案与正确性、证据字段；异常状态不恢复。
- 新增 Android UI 测试覆盖点词/展开语法不发声、显式播放与未就绪禁用；设备执行仍待有 Android 设备的环境。
- ADB 已实际检查：无连接设备；`/dev/kvm` 不存在。未声称已做实机听音或 Android 截图。
- 104 个课程 audioKey 均有录音，104 条 m4a 全部经 ffmpeg 解码为 PCM 且非静音，时长 0.448–0.704 秒。详细结果：`/workspace/kana-audio-validation.json`；不等于真人发音审核。

### 设备上执行新增 UI 回归

连接 Android 设备后，从仓库根目录执行（测试 APK 已单独构建，不代表已经执行）：

```bash
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
adb install -r android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e class com.gojapan.feature.search.ui.ExpressionLearningInteractionTest com.gojapan.test/androidx.test.runner.AndroidJUnitRunner
```

实际出声另按 `JAPANESE_AUDIO_ACCEPTANCE.md` 验证，有/无日语语音包、中文系统、离线、重启静音和长音/促音/拗音不能由上述 UI 回归替代。

### 本轮最终结果

- `testDebugUnitTest assembleDebug lintDebug assembleDebugAndroidTest assembleRelease`：BUILD SUCCESSFUL。
- 92 项单元测试全部通过，0 失败、0 错误、0 跳过。主题对比度测试直接检查客户端颜色。
- Debug Lint：0 errors、54 warnings、1 hint；release 关键 Lint 通过。
- Android UI 测试 APK 成功编译打包，未在设备执行。
- Debug 确认包含全部 104 条课程录音；release 会缩短资源路径，已通过 aapt2 资源表逐项解析原始名称，确认 104 个资源均指向包内文件，且音频字节与源文件一致。
- 最终日志：`/workspace/continue-final-checks.log`；实际听音仍待连接设备。

# 日语播放修复（issue #6）

实现：Android 11+ 声明 TTS 引擎查询；搜索整句、逐假名、场景听力以及假名开发 fallback 共用 JapaneseSpeaker。初始化完成后验证日语语言和已安装的离线日语 voice，缺失时不回退到中文。搜索和场景的 audio_play 结果来自 UtteranceProgressListener，而非 speak 的入队结果。取消/替换请求不计播放成功。退出页面释放引擎，场景切题停止旧请求。

恢复：初始化失败/超时、缺少日语包显示安装和重新初始化操作；已就绪时显示停止按钮，播放失败可再次点击播放。内置假名音频缺失/损坏时，debug 使用同一日语策略降级，release 显示重新安装提示。

## 待实机验收

尚未在反馈设备或模拟器验证实际出声，不能据此关闭 issue。
记录设备型号、Android 版本、应用版本、TTS 引擎版本、日语包安装情况、网络状态和实际听音结果。

| 条件 | 操作 | 预期 |
| --- | --- | --- |
| 中文系统，有日语包，冷启动 | 搜索“酒店”并播放整句和假名 | 初始化前不可播放；就绪后为日语，完成后才记录成功 |
| 中文系统，无日语包/无引擎 | 打开搜索、听力、debug 假名 fallback | 明确提示，不用中文读日语；安装后重试恢复 |
| 离线且已安装日语包 | 搜索整句、场景听力 | 使用离线日语 voice，可以播放 |
| 连续快速点击 | 连续播放两个表达或假名 | 旧请求停止，只有当前请求改变状态/记录结果 |
| 退出页面或听力切题 | 播放中返回、切题 | 停止旧音频，迟到回调不更新当前请求 |
| 假名内置资源损坏 | debug/release 分别触发 | debug 可用日语 TTS 降级；release 显示修复提示，练习不崩溃 |
| 各假名入口 | 假名点读、游戏、练习、复习 | 内置录音可播放 |
| 真人听音 | ホテル、タクシー、コーヒー、チケット、含汉字整句，以及拗音 | 发音为日语；长音、促音、拗音准确 |

## 本轮自动检查

- `git diff --check` 通过。
- 已添加 `JapaneseVoicePolicyTest`：允许已安装的离线日语 voice，拒绝中文/英文 voice、需联网 voice 和未安装 voice。
- 初次构建因缺少 Android SDK 失败；2026-11-10 已在当前工作区安装 Android API 36、Build Tools 36.0.0、platform-tools 和 Temurin JDK 17，并配置代理与系统 CA 信任库。
- `testDebugUnitTest assembleDebug lintDebug assembleRelease` 全部通过，80 项单元测试，0 失败、0 错误、0 跳过；包含新增的 3 项语音策略测试。
- Debug Lint：0 errors、53 warnings、1 hint；release 关键 Lint 通过。
- Debug APK：`android/app/build/outputs/apk/debug/app-debug.apk`。
- Release APK：`android/app/build/outputs/apk/release/app-release-unsigned.apk`（未签名）。
- 当前工作区执行入口：`source /workspace/android-env.sh`，或 `python3 /workspace/android-build.py <Gradle tasks>`。环境说明：`/workspace/ANDROID_ENVIRONMENT.md`。

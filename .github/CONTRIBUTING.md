# 贡献指南 / Contributing

欢迎改进 WearPomodoro 的计时、设备兼容、交互、翻译和文档。Issue 和 Pull Request 均可使用中文或英文。
Contributions to timing, device compatibility, interaction, translations, and documentation are welcome. Issues and pull requests may be written in Chinese or English.

项目使用与构建说明见 [中文 README](../README.md) / [English README](../README_en.md)。代码结构和行为约定见 [AGENT.md](../AGENT.md)；修改触觉插件时同时阅读其 [维护指南](../miwearhaptics/AGENT.md)。
See the READMEs for usage and build instructions. Consult the linked development guides for architecture, timer behavior, and haptics constraints.

## 问题与建议 / Issues and suggestions

- 提交前搜索已有 Issue，尽量在相关讨论中补充信息。 / Search existing issues first and add information to a relevant discussion when possible.
- 问题反馈使用 Bug report 表单，提供实际应用版本或提交、机型、Android / Wear OS、固件、复现步骤和预期表现。 / Use the bug report form and include the affected app version or commit, device, Android / Wear OS, firmware, reproduction steps, and expected behavior.
- 后台计时或提醒问题请补充熄屏状态、通知与精确闹钟权限、电池管理设置。 / For background timing or reminder issues, include screen state, notification and exact alarm access, and battery settings.
- 功能建议使用 Feature request 表单，描述使用场景、希望的行为和尝试过的替代方案。 / Use the feature request form to describe the use case, requested behavior, and alternatives tried.

## 修改代码 / Making changes

1. 保持每个 PR 聚焦一个问题，并保留与当前任务无关的已有修改。 / Keep each PR focused on one problem and preserve unrelated existing changes.
2. 使用项目自带的 Gradle Wrapper，依赖、JDK 和 SDK 要求以仓库配置为准。 / Use the repository's Gradle Wrapper and follow its dependency, JDK, and SDK configuration.
3. 遵循现有分层：纯计时逻辑位于模型层，控制器管理副作用，Compose 页面负责展示与交互。 / Keep pure timer logic in the model, side effects in the controller, and presentation and interaction in Compose screens.
4. 用户可见文本同步维护英文和中文资源；功能与构建步骤变化时同步两种 README。README 不固定记录应用、依赖或编译环境版本号。 / Keep English and Chinese strings and READMEs in sync. Refer to configuration files for app, dependency, and build environment versions instead of pinning them in the READMEs.

## 验证 / Validation

仅文档修改时检查内容、图片、链接和格式即可。应用代码修改可在项目根目录执行编译与 Android Lint：
For documentation-only changes, check content, images, links, and formatting. For app code changes, run compilation and Android Lint from the project root:

Windows / PowerShell：

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug
```

macOS / Linux：

```bash
sh ./gradlew :app:assembleDebug :app:lintDebug
```

插件、R8 或发布配置的验证命令见 [AGENT.md](../AGENT.md)。涉及计时、通知、返回手势、表冠或触觉时，在相关设备与权限状态下验证，并记录未覆盖的场景。
See the development guide for plugin, R8, and release configuration checks. Changes to timing, notifications, back gestures, rotary input, or haptics should be checked on relevant devices and permission states, with any coverage gaps recorded.

当前仓库没有自动化测试源码或 CI 工作流；构建通过不能替代设备行为验证。请如实说明执行的检查，未运行的项目注明原因。
The repository currently has no automated test sources or CI workflows. A successful build does not replace device behavior checks. Report what you actually ran and explain checks that were not performed.

## 提交 Pull Request / Opening a pull request

填写 PR 模板，说明具体问题、修改后的行为、关联 Issue 和验证结果。界面改动可附前后截图；如果未覆盖某些设备或系统，请在验证说明中列出。
Complete the PR template with the problem, resulting behavior, related issues, and validation results. Include before/after screenshots for UI changes when useful and note untested devices or systems.

保留现有作者信息与许可证：主项目使用 [AGPL v3](../LICENSE)，`miwearhaptics` 使用独立的 [LGPL v3](../miwearhaptics/LICENSE)。
Preserve existing author credits and licenses: the main project uses AGPL v3, and `miwearhaptics` has its own LGPL v3 license.

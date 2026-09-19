# WearPomodoro 开发与代理协作指南

本文件供维护项目的开发者和编程代理参考。用户文档见 [README.md](README.md) 与 [README_en.md](README_en.md)。以当前任务要求和实际源码为准，保持修改集中，保留工作区中已有的用户改动。

本指南覆盖仓库根目录和 Android 应用。涉及 `miwearhaptics/` 时，还需阅读其 [AGENTS.md](miwearhaptics/AGENTS.md)；该目录是独立的 Gradle 插件与运行时库。

## 项目定位

- 项目名 `WearPomodoro`，中文应用名「腕上番茄钟」，应用 ID / namespace 为 `cc.star0.wear.pomodoro`。
- 应用在手表上独立运行，使用 Kotlin、Wear Compose Material 3、Navigation 3、协程 / StateFlow 和 Preferences DataStore。
- 计时包括准备学习、学习、短休息、长休息四个阶段。学习结束自动进入休息，休息结束等待手动开始下一轮。
- 所有计时与设置在本地处理；现有实现不需要账号、后端或配套手机应用。

## 代码地图

以下应用源码路径均相对于 `app/src/main/java/cc/star0/wear/pomodoro/`。

| 路径 | 职责 |
| --- | --- |
| `PomodoroApplication.kt` | 应用级协程作用域、共享控制器、触觉策略和通知通道初始化 |
| `MainActivity.kt` | Compose 入口、权限请求、权限刷新和外部设置 / 链接跳转 |
| `PomodoroViewModel.kt` | 向 UI 暴露状态，通过服务发送计时操作，转交设置修改 |
| `model/PomodoroModels.kt` | `PomodoroSettings`、`PomodoroState`、`PomodoroPhase` 和纯转换逻辑 `PomodoroEngine` |
| `data/PomodoroStore.kt` | 设置、计时状态和本轮设置快照的 DataStore 读写 |
| `timer/PomodoroController.kt` | 状态转换、恢复、持久化、系统闹钟及阶段提醒 |
| `timer/PomodoroService.kt` | 前台通知、计时命令和通知停止确认 |
| `timer/BootReceiver.kt` | 接收开机广播，将计时恢复为安全的待开始状态 |
| `notifications/PomodoroNotifications.kt` | 通知通道、倒计时通知、通知操作及阶段提醒 |
| `permissions/AppPermissions.kt` | 读取 APK 声明的权限，按 Android 版本计算状态 |
| `text/PomodoroText.kt` | UI 与通知共用的时长、分钟数及轮数格式化 |
| `ui/PomodoroApp.kt` | 三页主页、可保存导航栈和全局返回行为 |
| `ui/TimerScreen.kt`、`ui/TimerDisplay.kt` | 可见页面刷新、倒计时显示与进度计算 |
| `ui/SettingEditorScreen.kt` | 小时 / 分钟与轮数选择器、草稿和保存行为 |
| `ui/InteractionSettings.kt`、`ui/StopConfirmationDialog.kt` | 返回手势边界与停止确认弹窗 |
| `ui/SettingsScreen.kt`、`ui/WearListDefaults.kt` | 设置列表、颜色和 Wear 列表变换的复用组件 |

构建入口是根目录 `settings.gradle.kts` 与 `app/build.gradle.kts`。依赖版本集中在 `gradle/libs.versions.toml`；`miwearhaptics` 独立管理自己的依赖。

## 构建约定

- 使用仓库自带 Wrapper：当前 Gradle `9.7.1`、AGP `9.3.2`、Kotlin `2.4.20`、Wear Compose `1.6.2`。
- `gradle/gradle-daemon-jvm.properties` 指定 **JDK 25**；应用 Java / Kotlin 字节码目标为 **17**。不要混淆运行 Gradle 的 JDK 与源码目标版本。
- Android 配置为 `compileSdk = 37`、`targetSdk = 37`、`minSdk = 25`，新增平台 API 必须处理版本边界。
- 保持现有 AGP Kotlin 集成方式；不要无依据地额外添加 `org.jetbrains.kotlin.android` 插件。
- SDK 路径放在本机 `local.properties`。不提交 SDK、构建输出、缓存、签名密钥或机器专属路径。
- 应用仅打包 `armeabi-v7a` / `arm64-v8a`，同时输出通用 ARM APK。变更 ABI 时同步检查 `ndk.abiFilters`、`splits.abi`、输出命名和 README。
- Release 启用 R8 与资源缩减，使用 `src/main/keepRules/rules.keep`，当前未配置 Release 签名。不要将未签名产物描述为可直接安装的发布包。
- README 不固定记录应用、依赖或构建环境的具体版本号，统一链接到实际配置文件。功能、构建步骤或兼容行为变化时同步更新中英文 README；插件依赖版本变更需同时检查 included build。

## 计时与持久化约束

1. **保留清晰分层。** 纯状态转换放在 `PomodoroEngine`，不要引入 Context、通知或存储依赖。控制器负责副作用，服务处理 Android 生命周期和命令，Composable 负责展示与交互。
2. **使用单调时钟。** 计时截止点基于 `SystemClock.elapsedRealtime()`，不依靠每秒递减变量。系统日历时间仅用于通知等展示需要，不用于保存计时截止点。
3. **保留本轮设置快照。** `activeSettings` 固定本轮学习和休息的时长；新时长在下一次 `startFocus` 时采用。`applySettings` 会立即更新长休息轮数规则，不重置当前截止时间。
4. **保留边界值。** 默认时长为 25 / 5 / 15 分钟，合法范围为 1–1439 分钟；长休息间隔为 2–12 轮，`NEVER_LONG_BREAK = 0` 表示禁用。UI 编辑和持久化读取应与 `sanitized()` 一致。
5. **保留轮数规则。** 学习完成时累计一轮，达到间隔时进入长休息；短休息结束保留轮数，长休息结束清零。停止操作重置计时和累计轮数。
6. **区分进程恢复与重启。** 进程重建可恢复已保存状态；开机广播调用 `resetAfterReboot()`，保留设置并清空计时。不能跨设备重启复用旧的 elapsed 时间戳。
7. **准确处理过期状态。** 当前恢复逻辑对过期状态推进一个阶段，学习后的休息从处理时刻开始。不要把它描述为离线期间自动完成多个循环。
8. **保持副作用一致。** 状态变更需同步考虑 StateFlow、DataStore、闹钟取消 / 重排和通知。修改回调路径时检查迟到或重复闹钟、暂停后回调以及状态恢复的交互。
9. **保持数据兼容。** 修改 DataStore 键、枚举名或设置字段时，处理旧值和缺失值；新增设置应同步默认值、校验、读取、保存、快照、UI 和文档。
10. **保护初始化与草稿。** 设置恢复完成前不接受修改，也不应让默认值覆盖已保存值。编辑器以恢复后的设置初始化草稿，取消不保存。

## 后台服务、通知与权限

- 阶段结束依靠 `AlarmManager` 与 `PomodoroService`，不要将后台计时转移到 Activity 或 Composable 的刷新循环。
- API 31 及以上先检查 `canScheduleExactAlarms()`；不可用时保留非精确闹钟回退，文档不能承诺此时仍按秒准时提醒。
- 保留前台服务的启动通知、`specialUse` 类型与清单声明的一致性；调整服务生命周期后检查暂停、待开始和进程重建场景。
- 常驻通知与阶段提醒使用独立通道。通知文本取当前应用 locale，语言配置变化时刷新通知通道名称及服务通知。
- 通知中的停止使用两次点击确认，窗口为 10 秒；计时页先暂停，再通过对话框确认。修改其中一路时检查另一路的行为。
- 权限状态来自已安装 APK 的声明与系统状态，区分普通权限、通知运行时授权和精确闹钟特殊访问。不要把所有清单权限都当作运行时弹窗权限。
- 外部设置或网页可能没有可处理的 Activity；保留现有异常处理、设置回退和本地化提示。

## UI、交互与语言

- 采用现有 Kotlin 风格与四空格缩进，复用 Wear Compose Material 3、`SettingsListLayout`、`FittedText` 和共享颜色规则。
- 保留适合小圆屏的布局，检查长文本、小时级倒计时、触控区域、滚动列表及表冠焦点。不要仅按手机屏幕验证。
- UI 状态使用生命周期感知的收集。计时页只在生命周期与页面可见状态允许时刷新，文字和进度环使用 `timerDisplay` 的同一取整结果。
- Navigation 3 目的地保持可保存 / 可序列化。手势由 `InteractionSettings` 统一解释，不在各页面独立猜测系统版本行为。
- API 36 以下支持独立右滑返回设置；API 36 及以上由系统返回设置统一控制。导航页和停止对话框需一致，关闭手势后仍保留显式返回、取消或保存按钮。
- 用户可见文本放入资源：`app/src/main/res/values/strings.xml` 为英文默认资源，`values-zh/strings.xml` 为中文；同步维护占位符、复数和无障碍说明。
- UI 与通知共用时长 / 轮数格式化逻辑，使用当前配置的 Resources，避免硬编码语言或固定 Locale。新增语言时更新 `res/xml/locales_config.xml`。

## 触觉插件边界

- 根 `settings.gradle.kts` 顶层的 `includeBuild("miwearhaptics")` 同时提供插件解析和运行时依赖替换，保留其位置与完整目录。
- 插件 ID 为 `cc.star0.wear.lib.miwearhaptics`，应用模块已启用；运行时由插件自动添加，无需再接入另一个同名库。
- 应用清单将 `wear-sdk` 声明为可选库；设备 SDK 实现不应打包进 APK。
- `PomodoroApplication` 在 UI 初始化前设置 `GOOGLE_FIRST`。优先按接口是否可调用选择 Google / 小米实现，不按机型字符串硬编码。
- 当前仅适配 `getScrollItemFocus`、`getScrollTick`、`getScrollLimit`；SDK 均不可用时返回无反馈结果。阶段提醒的通知振动独立于这些滚动触觉常量。
- 插件编译目标为 Java 17，纯 Java 运行时保持 Java 8 兼容。更详细的字节码、缓存、回退和 R8 约束见 [插件指南](miwearhaptics/AGENTS.md)。

## 验证与交付

仅修改文档时，核对配置来源、默认值、命令、路径、许可证和中英文内容，检查 Markdown 链接与格式；无需运行完整 Android 构建。

修改代码时按影响范围在根目录运行以下命令。Windows / PowerShell：

```powershell
# 应用编译与 Android Lint
.\gradlew.bat :app:assembleDebug :app:lintDebug

# 通知与正在进行的活动测试
.\gradlew.bat :app:testDebugUnitTest

# 仅在修改触觉插件或其运行时时执行
.\gradlew.bat -p miwearhaptics build

# 涉及 R8、资源缩减、反射、发布配置时执行
.\gradlew.bat :app:assembleRelease
```

macOS / Linux 先按需执行 `chmod +x gradlew`，将入口换为 `./gradlew`。调试产物位于 `app/build/outputs/apk/debug/`，Release 产物位于 `app/build/outputs/apk/release/`，Lint 报告通常位于 `app/build/reports/lint-results-debug.html`。

通知的 Robolectric 测试位于 `app/src/test/`，覆盖正在进行的活动数据、倒计时、阶段切换和暂停恢复。不要把单元测试或编译通过当作完整设备行为验证；新增关键逻辑时优先为纯转换、显示计算或权限判定添加有针对性的测试。

根据改动选择手动验证场景：

| 范围 | 重点场景 |
| --- | --- |
| 计时 | 开始 / 暂停 / 继续，UI 与通知停止确认，短休息、长休息、禁用长休息，轮数清零 |
| 设置与恢复 | 最小 / 最大时长，运行中修改设置，冷启动立即打开编辑器，取消草稿，进程重建，设备重启 |
| 后台与提醒 | 退出界面、熄屏、精确闹钟允许 / 拒绝、通知关闭、暂停后旧回调、前台通知操作 |
| 正在进行的活动 | 表盘与启动器入口、系统倒计时、暂停移除 / 继续恢复、休息结束和停止清理、冷启动与已有设置页点按返回计时页 |
| UI 与语言 | 小圆屏、小时级时间、中英文及复数、表冠焦点、API 36 前后的返回开关及弹窗 |
| 触觉与 Release | Google / 小米接口可用与缺失、R8 后反射、ABI 输出与实际签名安装 |

提交前查看差异，确保只有任务相关修改。交付时说明变更、实际执行的检查及未验证的部分；构建失败需记录具体原因，不要将未执行的检查写为通过。

主项目使用 [AGPL v3](LICENSE)，`miwearhaptics` 使用独立的 [LGPL v3](miwearhaptics/LICENSE)。保留相应许可证和作者信息；用户功能、兼容边界、权限或构建方式改变时同步更新两种语言的 README。

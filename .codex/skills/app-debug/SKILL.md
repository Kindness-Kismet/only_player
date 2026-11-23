---
name: app-debug
description: 调试、测试或验证 Only Player 应用功能、页面和代码改动时使用，支持统一命令入口、业务调用和界面交互验证。
---

# Only Player 应用调试

## 任务配置

开始前明确目标功能、验收条件、已确认设备、Debug 包与 APK、证据目录、需要恢复的设置和测试数据；已有会话授权及未受本次改动影响的验证结果直接继承。

- Android 包：`one.only.player.debug`；Activity：`one.only.player.debug/one.only.player.MainActivity`。
- Provider：`content://one.only.player.debug.commands`，仅位于 Debug 源集并校验调用 UID。
- 主源：`.codex/skills/app-debug`；镜像：`.claude/skills/app-debug`。下文 `<skill_dir>` 表示当前使用的技能目录。
- 命令以普通 `python <skill_dir>/scripts/run.py` 调用；从其他工作目录执行时使用脚本绝对路径，工程根目录由脚本位置确定。
- 按任务需要在 `build/record/` 保存记录和命令结果，截图保存到 `build/screenshots/`；本地配置、凭据和证据不入库。

## 指引索引

只阅读当前任务所需的指引，链接相对于本文件。

| 任务 | 指引 |
|---|---|
| 设备选择、调用格式、唤醒、锁屏、截图和应用生命周期 | [运行与截图](guides/runtime.md) |
| 页面跳转 | [页面指令](guides/pages.md) |
| 播放器控制、状态、播放标记、章节 | [播放器指令](guides/player.md) |
| 本地媒体列表、打开、移动、回收站、扫描 | [媒体库指令](guides/media.md) |
| 云服务器和云端媒体列表、打开 | [云端指令](guides/cloud.md) |
| 本地和云端快捷设置 | [快捷设置指令](guides/quick_settings.md) |
| 收藏夹准备、列表、移动、删除 | [收藏夹指令](guides/favorites.md) |
| 播放列表和观看历史 | [播放列表与观看历史指令](guides/playlists.md) |
| 设置写入、切换、清理、重置 | [设置指令](guides/settings.md) |

## 执行流程

1. 先查看入口 `--help`，再按任务阅读分类指南与对应命令 `--help`，核对作用、参数、取值及业务前置状态。
2. 用 `device.list` 列出设备；用户已指定时直接沿用，否则即使仅一台设备也先确认目标。需要实机交互时用 `device.wake` 唤醒并检查锁屏状态。
3. 代码变化需要安装时使用项目构建入口，按设备 ABI 安装 Debug APK；仅帮助、脚本或文档变化按实际影响验证。
4. 功能验证前用 `page.open home` 或目标页将应用置前台；设备查询、安装、唤醒等前置步骤除外。打开本地或云端视频前也须先置前台。
5. 默认用业务指令触发功能；用户要求 UI 交互时，先打开页面、检查资源 ID，再模拟操作。
6. 检查命令退出码和 JSON 的 `ok`，再按验收目标核对业务状态；静态页面切换后截图查看布局。`page.open` 成功表示导航已受理，页面就绪与显示效果须另行观察。
7. 恢复本次修改的设置和测试数据，实机测试结束后及时 `device.sleep` 锁屏，并记录实际验证结果。

观察或点击播放器控制按钮前，将 `player.controller_timeout` 设为 `custom_60` 并记录原值。字幕验证先定位到应出现字幕的时间点；需要固定画面时再暂停。单选设置使用 `settings.set` 明确值。

## 命令与返回

```bash
python <skill_dir>/scripts/run.py
python <skill_dir>/scripts/run.py settings.set --help
python <skill_dir>/scripts/run.py page.open home --device <device_id> + settings.set appearance.top_bar_style --value HYPER_OS_4
```

- 叶命令统一 `domain.action`，例如 `settings.set`、`cloud.media.open`；`target` 映射到 Provider 的 `arg`。不新增 `domain + arg=action` 形式。
- `--value`、`--enabled true|false` 为常用值；其他字段用可重复的 `--extra KEY:TYPE:VALUE`。完整取值和业务前置条件放在对应分类指南。
- 无参数与 `--help` 输出相同的完整英文帮助并退出 0；帮助由 `commands.py` 注册表生成，不能连接设备、加载执行模块或准备构建环境。每条命令提供独立帮助和完整示例。
- 操作 stdout 只返回一个 JSON；诊断和子工具输出走 stderr。单条成功含 `ok`、`command`、`result`；失败含 `ok: false`、`error`、`exit_code`，业务失败保留应用返回的 `result`。
- 参数错误退出 2；业务失败退出 1；子工具失败保留实际非零码。不能仅凭 adb 退出 0 判断应用操作成功。
- 独立参数 `+` 串联命令，先解析全链，再顺序执行；同链共享明确设备，冲突设备拒绝执行。整组返回有序 `steps`，执行失败停止后续命令，保留完成步骤、失败步骤、从 1 开始的 `failed_index` 及退出码。

## 输入与授权边界

- Provider 命令调用实际业务入口；它的成功不能代替控件命中、手势、弹窗和页面渲染的交互验收。
- UI 操作前检查 `testTag`、`contentDescription` 或资源 ID，优先用稳定标识定位；手工 adb 操作始终带 `-s <device_id>`，Git Bash 中加 `MSYS_NO_PATHCONV=1`。
- 设备、安装、删除或重置操作沿用用户已授权范围；清空数据、卸载、覆盖正式包、永久删除及无备份的设备文件删除按项目规范处理。
- 截图命令是设备显示屏截图，先查询 display ID，再保存原尺寸 PNG；播放器自身的截图动作使用 `player.screenshot`。

## 维护规则

- 新增、变更或移除业务，或调试需求增加时，同步检查并成组维护：应用端处理器、`commands.py` 注册与帮助/示例、对应分类指南、技能索引及镜像。优先复用已有能力，业务改动完成前验证受影响命令，不把调试维护推迟到下一次任务。
- `scripts/run.py` 只负责无缓存引导、参数/帮助、全链校验和结果编排；`scripts/commands.py` 只放标准库与无 IO 声明，处理器使用延迟加载引用；`scripts/client.py` 负责设备、adb 传输和结果解析。需要 Shell 支持时放 `scripts/shell/`，不得另建平行主入口。
- 导入项目模块前设置 `sys.dont_write_bytecode = True` 和 `PYTHONDONTWRITEBYTECODE=1`；主源、镜像与其他工作目录执行都不能产生字节码缓存。
- 指令表保存在 `guides/*.md`，`SKILL.md` 保留任务配置、入口、执行流程、授权边界和索引；文档只列实际实现的能力，删掉已失效的方法和示例。
- 首次修改技能源前核对镜像与同步基线。修改后执行 `python scripts/build_support/sync_skills.py --sync`，再执行 `--check` 核对文件集合与字节一致。
- 验证涵盖本次受影响的帮助、参数失败、JSON/退出码、链式预解析与中断、真实业务结果；应用端有改动时编译 Debug 并实机验证。继承未受影响的历史结果，默认不新增或调整 test 文件。

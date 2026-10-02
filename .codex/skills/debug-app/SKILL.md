---
name: debug-app
description: 调试、测试或验证 Only Player 应用功能、页面和代码改动时使用，支持调试指令和界面交互验证。
---

## 使用原则

用户要求调试、测试或验证 Only Player 时，优先使用 debug 指令，不优先点 UI 坐标。

- 默认触发功能：用 debug 指令。
- 功能验证开始前，先用 `page.open home` 或目标页把应用拉到前台；`page.open` 会启动 `MainActivity`，设备查询、安装、唤醒这类前置步骤除外。
- 明确要求 UI 验证、实际验证或模拟点击：先用 `page.open` 切页面，再检查资源 ID，最后模拟点击。
- 观察或点击播放器控制按钮前，先用 `settings.set player.controller_timeout` 设为 `custom_60`。
- 执行 `media.open` 或 `cloud.media.open` 前，先用 `page.open home` 把应用置前台，避免系统拦截后台启动播放器。
- 指令返回 `Bundle` 中的 `ok` 必须为 `true`，否则按失败处理。
- 只能选一个的设置项必须用 `settings.set` 写明确值，不用 `settings.toggle` 猜状态。

## 指引索引

只阅读当前任务需要的指引。文档链接相对于所在文件，命令在仓库根目录执行。

`<skill_dir>` 为本次调用的技能目录，使用 `.codex/skills/debug-app` 或 `.claude/skills/debug-app`。

| 任务 | 指引 |
|---|---|
| 设备选择、唤醒、锁屏、调用格式、截图流程 | [运行与截图](guides/runtime.md) |
| 页面跳转 | [页面指令](guides/pages.md) |
| 播放器控制、状态读取、播放标记、区间循环 | [播放器指令](guides/player.md) |
| 本地媒体列表、打开、移动、回收站、扫描 | [媒体库指令](guides/media.md) |
| 云服务器和云端媒体列表/打开 | [云端指令](guides/cloud.md) |
| 本地和云端快捷设置 | [快捷设置指令](guides/quick_settings.md) |
| 收藏夹准备、列表、移动、删除 | [收藏夹指令](guides/favorites.md) |
| 播放列表准备、列表、添加、删除，以及观看历史 | [播放列表与观看历史指令](guides/playlists.md) |
| 设置写入、切换、清理、重置动作 | [设置指令](guides/settings.md) |

## 指令入口

Debug 包名固定为 `one.only.player.debug`；Activity 为 `one.only.player.debug/one.only.player.MainActivity`，命令 Provider 为 `content://one.only.player.debug.commands`。

页面切换优先用脚本，脚本会先 `am start` 拉起 `MainActivity`，再调用 `page.open`：

```bash
<skill_dir>/scripts/debug-open-page.sh -s <device_id> -p <page_id>
```

底层统一使用 debug-only `ContentProvider.call()`：

```bash
adb shell content call \
  --uri content://one.only.player.debug.commands \
  --method <domain.action> \
  --arg <target> \
  --extra value:s:<value> \
  --extra enabled:b:true
```

新增指令必须使用 `domain.action`，禁止新增 `domain` + `arg=action` 形式。

## 维护规则

- 本技能同时保存在 `.codex/skills/debug-app/` 和 `.claude/skills/debug-app/`。修改任一端的正文、指引或脚本时，必须同步更新另一端，保持所有同名文件内容一致。
- 调试指令入口、参数、返回值或说明发生变化时，更新本目录中的对应文档或脚本。
- 指令表只放到 `guides/*.md`，`SKILL.md` 只保留入口、规则和索引；新增指令类别时更新本索引。

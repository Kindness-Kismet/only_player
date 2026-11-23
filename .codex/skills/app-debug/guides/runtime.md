# 运行与截图

## 入口与帮助

以下命令从仓库根目录执行，`<skill_dir>` 使用 `.codex/skills/app-debug` 或 `.claude/skills/app-debug`。其他工作目录使用 `run.py` 的绝对路径。

```bash
python <skill_dir>/scripts/run.py
python <skill_dir>/scripts/run.py --help
python <skill_dir>/scripts/run.py settings.set --help
python <skill_dir>/scripts/run.py device.list
```

前三项只读取命令声明，不依赖 adb、SDK、应用安装或联网。操作时 adb 依次从 PATH、`ANDROID_HOME`、`ANDROID_SDK_ROOT`、根目录 `local.properties` 的 `sdk.dir` 定位。

## 设备与生命周期

用户明确指定设备后沿用该设备；未指定时即使只连接一台也先确认。所有操作命令使用 `--device <device_id>`，同一链可只写一次。

```bash
python <skill_dir>/scripts/run.py device.wake --device <device_id> + page.open home
```

`device.wake` 请求唤醒并解除无密码锁屏；命令成功后检查窗口状态，确认屏幕可见。密码锁屏需用户解锁。

```bash
MSYS_NO_PATHCONV=1 adb -s <device_id> shell dumpsys window
```

`page.open` 先启动 Debug MainActivity，再调用 Provider 导航；Manifest 使用默认 `standard` 启动模式，导航 Intent 显式携带 `NEW_TASK | CLEAR_TOP | SINGLE_TOP`，复用任务中的 MainActivity 并交付页面请求。`result.ok=true` 表示请求受理，页面内容以 UI 树和截图为准。播放器进入后台后服务仍可能运行，退出播放器用 `player.back`；锁屏不代表应用或播放服务已退出。

实机结束后恢复本次修改的设置并锁屏，模拟器无需锁屏：

```bash
python <skill_dir>/scripts/run.py device.sleep --device <device_id>
```

## 业务调用格式

```bash
python <skill_dir>/scripts/run.py settings.set appearance.top_bar_style --value HYPER_OS_4 --device <device_id>
python <skill_dir>/scripts/run.py settings.set appearance.top_bar_blur --enabled true --device <device_id>
python <skill_dir>/scripts/run.py cloud.media.list --extra server_id:s:<server_id> --extra path:s:/Movies --device <device_id>
```

分类表中的 `method` 对应叶命令，`arg` 对应其位置参数 `target`。额外字段使用 `--extra KEY:TYPE:VALUE`：`s` 字符串、`b` 布尔、`i` 32 位整数、`l` 64 位整数、`f` 浮点。含空格的参数整体引用；负时间建议 `--value=-5s`，纯 `+` 作为值时写 `--value=+`。

应用端保持原 Bundle 调用协议，统一入口使用保留的 `response_json:b:true` 请求完整 JSON，布尔、数字和字符串类型保留。旧 Debug APK 未提供该传输字段时，入口明确失败，需安装当前 Debug APK。

## 返回与串联

```bash
python <skill_dir>/scripts/run.py page.open home --device <device_id> + media.list + screenshot.take --name home
```

链式命令的语法、类型、重复字段和设备冲突在所有动作执行前校验；具体业务前置条件由应用执行时校验。成功示意：

```json
{"ok":true,"command":"media.list","result":{"ok":true,"command":"media.list","message":"...","target":null,"value":null}}
```

运行失败保留已完成步骤和失败位置，后续不执行：

```json
{"ok":false,"command":"media.open","error":"Video not found","exit_code":1,"failed_index":2,"steps":[{"index":1,"ok":true,"command":"page.open","result":{"ok":true}},{"index":2,"ok":false,"command":"media.open","error":"Video not found","exit_code":1}]}
```

stdout 始终是单个操作结果 JSON，stderr 用于诊断；总帮助和逐命令帮助按文本输出。参数失败退出 2，业务失败退出 1，adb 非零退出码原样传递，超时为 124。

## 截图与交互

```bash
python <skill_dir>/scripts/run.py screenshot.take --device <device_id> --name appearance
```

脚本查询 SurfaceFlinger 的显示屏 ID，默认使用首个显示屏，也可用 `--display-id` 显式选择。截图为设备原尺寸 PNG，文件名追加时间和随机后缀，保存到根 `build/screenshots/`，JSON 返回绝对路径、display ID 和字节数。

静态页面切换后打开截图检查布局；如果黑屏，先检查唤醒和锁屏状态。UI 验证按页面跳转、读取资源 ID、定位控件、执行操作、读取结果和截图的顺序进行。

观察播放器控件前设 `settings.set player.controller_timeout --value custom_60`；验证字幕先跳到应出现的时间点。非控件验证的截图优先调用 `player.hide_controls`。截图清理遵守仓库回收站和备份规则。

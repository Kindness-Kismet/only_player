# 运行与截图

## 设备选择

每次开始调试前先列出设备，根据结果选择实机或模拟器；用户已明确指定设备时直接使用指定设备。

```bash
adb devices -l
```

仅有一台设备且用户未明确指定时也需要确认是否为目标设备，避免误用残留连接。

## 唤醒与锁屏

每次开始测试前先唤醒并解除无密码锁屏：

```bash
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb shell dumpsys window | grep -E "mCurrentFocus|mKeyguardShowing|isStatusBarKeyguard"
```

如果截图黑屏，先重新唤醒解锁并检查窗口状态，不要先怀疑页面或渲染逻辑。

实机测试结束后必须及时锁屏，模拟器无需锁屏：

```bash
adb shell input keyevent KEYCODE_SLEEP
```

## 截图

静态页面切换完成后必须截图检查布局。

旧截图按仓库删除规范处理，优先移到回收站。

```bash
<skill_dir>/scripts/debug-screenshot.sh -s <device_id> -n <page_id>
```

截图脚本必须按项目规范先查询 display id，再用 `adb exec-out screencap -d <display_id>`。

## 验证流程

1. 用 `adb devices -l` 选定目标设备；用户已明确指定时直接使用。
2. 检查设备 ABI。
3. 一键唤醒并解除无密码锁屏。
4. 用窗口状态确认已解锁；异常时再截图兜底。
5. 如有代码变更，编译并安装 debug 包。
6. 用 `page.open home` 或目标页把应用拉到前台。
7. 按仓库删除规范清理 `build/screenshots` 中的旧截图。
8. 默认用 debug 指令触发功能。
9. UI 验证时用 `<skill_dir>/scripts/debug-open-page.sh` 打开目标页面，检查资源 ID 后再模拟点击。
10. 验证字幕效果时，先跳到字幕会出现的时间点；需要固定某句字幕时，再按实际情况暂停。
11. 非控制器验证截图时，优先用 `player.hide_controls` 隐藏播放器控制器，失败再模拟点击触发。
12. 对静态页面截图并查看，确认布局正常。
13. 实机测试结束后及时锁屏。

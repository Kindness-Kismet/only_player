# 播放器指令

播放器指令统一使用 `player.<action>`。时间可以写毫秒数字，也可以写 `1500ms`、`15s`、`2m`。

数值范围：
- `seek_to`：`0` 到当前视频总时长。
- `seek_by`：负数后退，正数前进，最终会停在 `0` 到当前视频总时长之间。
- `long_press_speed` 的 `value`：`0.2..4.0`。
- `long_press_speed` 的 `duration_ms`：至少 `1ms`，建议不要超过 `2500ms`。
- `shuffle` 的 `enabled`：`true` 或 `false`，不填就切换当前状态。
- `loop` 的 `value`：`off`、`one`、`all`，不填就切换到下一个模式。

| method | arg | 作用 |
|---|---|---|
| `player.play` | 空 | 开始播放 |
| `player.pause` | 空 | 暂停播放 |
| `player.toggle_play_pause` | 空 | 切换播放和暂停 |
| `player.previous` | 空 | 播放上一个视频 |
| `player.next` | 空 | 播放下一个视频 |
| `player.seek_to` | 时间 | 连接播放器服务执行跳转，并等待位置落稳；也可用 `value` 填时间 |
| `player.seek_by` | 时间 | 连接播放器服务按指定时间前进或后退，并等待位置落稳；也可用 `value` 填正数或负数时间 |
| `player.long_press_speed` | 倍速 | 临时倍速一段时间，也可用 `value` 填倍速，`duration_ms` 填持续时间 |
| `player.stop` | 空 | 停止播放 |
| `player.shuffle` | 空 | 设置或切换随机播放，`enabled` 可选 |
| `player.loop` | 模式 | 设置或切换循环模式，`arg` 或 `value` 可填 `off`/`one`/`all` |
| `player.rotate` | 空 | 触发旋转按钮 |
| `player.toggle_ambience` | 空 | 打开或关闭氛围模式 |
| `player.toggle_mirror` | 空 | 打开或关闭视频镜像 |
| `player.show_controls` | 空 | 显示播放器控制器 |
| `player.hide_controls` | 空 | 隐藏播放器控制器 |
| `player.show_playlist` | 空 | 打开播放列表面板 |
| `player.show_speed` | 空 | 打开播放速度面板 |
| `player.show_audio` | 空 | 打开音频轨道面板 |
| `player.show_subtitle` | 空 | 打开字幕面板 |
| `player.lock` | 空 | 锁定播放器控制按钮 |
| `player.unlock` | 空 | 解锁播放器控制按钮 |
| `player.toggle_lock` | 空 | 切换控制按钮锁定状态 |
| `player.cycle_scale` | 空 | 切换下一个画面缩放方式 |
| `player.show_scale` | 空 | 打开画面缩放面板 |
| `player.show_decoder` | 空 | 打开解码器选择框 |
| `player.show_stats` | 空 | 显示技术信息叠加层 |
| `player.hide_stats` | 空 | 隐藏技术信息叠加层 |
| `player.toggle_stats` | 空 | 切换技术信息叠加层 |
| `player.show_video_filters` | 空 | 打开视频滤镜面板 |
| `player.show_audio_equalizer` | 空 | 打开均衡器面板 |
| `player.pip` | 空 | 进入画中画，没权限时打开系统设置 |
| `player.screenshot` | 空 | 触发播放器截图按钮 |
| `player.background` | 空 | 触发后台播放按钮 |
| `player.show_sleep_timer` | 空 | 打开睡眠定时选择框 |
| `player.show_marks` | 空 | 打开播放标记面板 |
| `player.show_chapters` | 空 | 打开章节面板，当前视频没有章节时显示空状态 |
| `player.chapter.seek` | 索引 | 按从 0 开始的章节索引跳转，并等待位置稳定 |
| `player.chapter.next` | 空 | 跳转到下一章，并等待位置稳定 |
| `player.chapter.previous` | 空 | 跳转到上一章，并等待位置稳定 |
| `player.chapter.swipe_next` | 空 | 走真实双指左滑的界面路径切到下一章，并显示章节反馈 |
| `player.chapter.swipe_previous` | 空 | 走真实双指右滑的界面路径切到上一章，并显示章节反馈 |
| `player.mark.add` | 空 | 按当前播放位置添加播放标记 |
| `player.mark.list` | 空 | 返回当前媒体播放标记，`value` 形如 `id@positionMs|id@positionMs` |
| `player.mark.seek` | id/时间 | 跳转到播放标记；`id` 可填标记 id，`arg` 或 `value` 可填标记 id 或毫秒位置 |
| `player.mark.delete` | id | 删除播放标记；`id` 或 `value` 可填标记 id，不填则删除第一条 |
| `player.show_menu` | 空 | 打开播放器一级菜单 |
| `player.menu_back` | 空 | 在播放器菜单中回到上一级 |
| `player.stress_pan_zoom` | 空 | 连续触发 zoom 与 pan；`value` 填循环次数，`interval_ms` 填间隔 |
| `player.panel_resize` | 尺寸 | 缩放播放器悬浮面板；`arg` 或 `value` 可填 `max`/`min`/`default`/`height_max`/`width_max`，成功后 `value` 返回当前布局快照 |
| `player.panel_move` | 位置 | 移动播放器悬浮面板；`arg` 或 `value` 可填 `center`/`default`，成功后 `value` 返回当前布局快照 |
| `player.panel_state` | 空 | 读取悬浮面板布局快照，`value` 形如 `w=360,h=720,x=24,y=80,...` |
| `player.back` | 空 | 退出播放器并结束当前播放 |
| `player.state` | 空 | 读取播放器完整状态，返回 `current_position_ms`、`media_duration_ms`、`is_currently_playing`、来源类型、播放状态、缓冲终点、剩余缓冲时长、缓冲百分比、播放速度、音视频码率、播放意图、加载状态、错误信息和卡顿统计 |
| `player.position` | 空 | 读取当前位置，返回 `current_position_ms` |
| `player.duration` | 空 | 读取总时长，返回 `media_duration_ms` |
| `player.cues` | 空 | 读取当前字幕 cue 数量和文本 |
| `player.video_format` | 空 | 读取 decoder、尺寸、色彩、HDR 判定和 video effects 状态 |
| `player.chapters` | 空 | 读取章节数量和列表；`message` 中每项格式为 `index@startMs@endMs@title` |

播放、暂停、上一集、下一集、跳转、长按倍速、随机播放和循环模式会直接连到播放器服务；面板和界面按钮需要播放器页面正在前台。

验证字幕效果时，必须让播放器处在字幕应该出现的时间点。可以播放到对应时间，也可以用 `seek_to` 跳过去；需要固定画面看清字幕时，再按实际情况暂停。

如果不是为了验证播放器控制器本身，截图前优先用 `player.hide_controls` 隐藏控制器；需要观察控制器时优先用 `player.show_controls` 显示。

# 设置指令

设置相关命令统一归在 `settings.*` 域：`settings.set` 写入确定状态，`settings.toggle` 只用于开关快速切换，`settings.action` 执行设置页里的清理、重置这类副作用动作。

本地媒体列表的视图、布局、排序、字段显示属于快捷设置，使用 `quick_settings.*`，见 [快捷设置指令](quick_settings.md)。

## set：写入确定状态

开关用 `--extra enabled:b:true/false`，数字、固定选项和文字用 `--extra value:s:<value>`。`player.control_slot` 需要同时给 `--extra name:s:<control>` 和 `--extra value:s:<slot>`，`audio.equalizer_band` 同样要 `name` 填频段、`value:i:` 填增益。

| method | arg | 作用 |
|---|---|---|
| `settings.set` | `appearance.theme` | 设置主题：`SYSTEM`/`OFF`/`ON` |
| `settings.set` | `appearance.language` | 设置应用语言，空字符串为系统默认 |
| `settings.set` | `appearance.dynamic_colors` | 设置动态取色开关 |
| `settings.set` | `appearance.title_long_press_home` | 设置首页标题长按回根目录 |
| `settings.set` | `appearance.floating_navigation_bar` | 设置悬浮导航栏开关 |
| `settings.set` | `appearance.top_bar_blur` | 设置顶部标题栏模糊开关 |
| `settings.set` | `appearance.floating_navigation_bar_blur` | 设置悬浮导航栏模糊开关 |
| `settings.set` | `appearance.predictive_back` | 设置预测性返回手势开关，重启应用后生效 |
| `settings.set` | `appearance.show_cloud_tab` | 设置底部导航是否显示云端入口；关闭后 `page.open cloud` 无效 |
| `settings.set` | `media.mark_last_played` | 设置标记最近播放媒体 |
| `settings.set` | `media.restore_last_played_in_folders` | 设置文件夹恢复最近播放 |
| `settings.set` | `media.ignore_nomedia` | 设置忽略 `.nomedia` |
| `settings.set` | `media.recycle_bin` | 设置回收站开关 |
| `settings.set` | `media.exclude_folder` | 添加/移除排除文件夹，`value` 为路径，`enabled` 控制添加或移除 |
| `settings.set` | `thumbnail.strategy` | 设置缩略图策略：`FIRST_FRAME`/`FRAME_AT_PERCENTAGE`/`HYBRID` |
| `settings.set` | `thumbnail.frame_position` | 设置缩略图帧位置：`0.0..1.0` |
| `settings.set` | `player.controller_timeout` | 设置控制栏自动隐藏：`disabled`/`15s`/`1m`、`custom_<秒数>` 或 `value:i:<秒数>` |
| `settings.set` | `player.controller_timeout_preset` | 设置控制栏自动隐藏预设：`DISABLED`/`FIFTEEN_SECONDS`/`ONE_MINUTE`/`CUSTOM` |
| `settings.set` | `player.dim_video_controls` | 设置控制栏显示时是否压暗视频 |
| `settings.set` | `player.screen_orientation` | 设置播放器方向 |
| `settings.set` | `player.remember_orientation` | 设置记住旋转方向 |
| `settings.set` | `player.resume` | 设置恢复播放：`YES`/`NO` |
| `settings.set` | `player.default_speed` | 设置默认播放速度：`0.2..4.0` |
| `settings.set` | `player.autoplay` | 设置自动播放 |
| `settings.set` | `player.pause_at_end_of_queue` | 设置播完最后一个视频时是否暂停 |
| `settings.set` | `player.auto_pip` | 设置自动进入画中画 |
| `settings.set` | `player.pip_mode` | 设置画中画样式：`NATIVE`/`CUSTOM` |
| `settings.set` | `player.background_play` | 设置后台播放 |
| `settings.set` | `player.remember_brightness` | 设置记住亮度 |
| `settings.set` | `player.control_slot` | 把播放器控件移到指定位置，`name` 为 `PlayerControl` 枚举名，`value` 为 `TOP_RIGHT`/`BOTTOM_RIGHT`/`MENU`/`HIDDEN`；角落控件超出当前方向的可见容量后可以横向滚动 |
| `settings.set` | `gesture.seek` | 设置滑动快进快退手势 |
| `settings.set` | `gesture.seek_preview_frame` | 设置拖动时预览目标帧 |
| `settings.set` | `gesture.seek_sensitivity` | 设置快进快退灵敏度 |
| `settings.set` | `gesture.brightness` | 设置亮度手势 |
| `settings.set` | `gesture.brightness_sensitivity` | 设置亮度手势灵敏度 |
| `settings.set` | `gesture.volume` | 设置音量手势 |
| `settings.set` | `gesture.volume_sensitivity` | 设置音量手势灵敏度 |
| `settings.set` | `gesture.double_tap` | 设置双击手势模式 |
| `settings.set` | `gesture.long_press` | 设置长按倍速手势 |
| `settings.set` | `gesture.long_press_variable_speed` | 设置长按变速 |
| `settings.set` | `gesture.long_press_speed` | 设置长按倍速速度 |
| `settings.set` | `gesture.zoom` | 设置缩放手势 |
| `settings.set` | `gesture.pan` | 设置平移手势 |
| `settings.set` | `gesture.seek_increment` | 设置跳转增量秒数 |
| `settings.set` | `decoder.priority` | 设置解码器优先级 |
| `settings.set` | `decoder.video_filters` | 设置视频滤镜总开关 |
| `settings.set` | `decoder.brightness` | 设置亮度滤镜值 |
| `settings.set` | `decoder.contrast` | 设置对比度滤镜值 |
| `settings.set` | `decoder.saturation` | 设置饱和度滤镜值 |
| `settings.set` | `decoder.hue` | 设置色相滤镜值 |
| `settings.set` | `decoder.gamma` | 设置 Gamma 滤镜值 |
| `settings.set` | `decoder.sharpening` | 设置锐化滤镜值 |
| `settings.set` | `audio.language` | 设置首选音频语言 |
| `settings.set` | `audio.require_focus` | 设置播放时是否抢占声音输出 |
| `settings.set` | `audio.pause_on_headset_disconnect` | 设置拔掉耳机后是否暂停 |
| `settings.set` | `audio.system_volume_panel` | 设置调音量时是否弹出系统音量面板 |
| `settings.set` | `audio.remember_volume` | 设置是否记住上次音量 |
| `settings.set` | `audio.remember_track` | 设置是否记住音轨 |
| `settings.set` | `audio.initial_volume_limit` | 设置刚开始播放时的最高音量 |
| `settings.set` | `audio.normalization` | 设置是否让忽大忽小的声音更平稳 |
| `settings.set` | `audio.boost` | 设置是否允许把音量放得更大 |
| `settings.set` | `audio.spatial` | 设置空间音频 |
| `settings.set` | `audio.equalizer` | 设置均衡器总开关 |
| `settings.set` | `audio.equalizer_band` | 设置单个频段增益，`name` 为 `AudioEqualizerBand` 枚举名或 `0..9` 频段下标，`value:i:` 为 `-12..12` 的 dB 值 |
| `settings.set` | `audio.equalizer_preset` | 应用内置均衡器预设，`value` 为 `FLAT`（原声）/`VOCAL`（人声清晰）/`BASS_BOOST`（低音增强） |
| `settings.set` | `subtitle.auto_load` | 设置字幕自动加载 |
| `settings.set` | `subtitle.remember_track` | 设置是否记住字幕 |
| `settings.set` | `subtitle.language` | 设置首选字幕语言 |
| `settings.set` | `subtitle.font` | 设置字幕字体 |
| `settings.set` | `subtitle.bold` | 设置粗体字幕 |
| `settings.set` | `subtitle.size` | 设置字幕字号 |
| `settings.set` | `subtitle.background` | 设置字幕背景 |
| `settings.set` | `subtitle.embedded_styles` | 设置嵌入样式 |
| `settings.set` | `subtitle.encoding` | 设置字幕编码 |
| `settings.set` | `subtitle.system_caption_style` | 设置系统字幕样式 |
| `settings.set` | `subtitle.color` | 设置字幕颜色 |
| `settings.set` | `subtitle.edge_style` | 设置字幕边缘样式 |
| `settings.set` | `subtitle.outline_thickness` | 设置普通字幕描边粗细 |
| `settings.set` | `subtitle.shadow_strength` | 设置普通字幕阴影强度 |
| `settings.set` | `subtitle.bottom_padding` | 设置字幕底部间距 |
| `settings.set` | `subtitle.scale` | 设置 ASS/PGS 字幕缩放：`0.5..3.0` |
| `settings.set` | `privacy.prevent_screenshots` | 设置防截图 |
| `settings.set` | `privacy.hide_in_recents` | 设置最近任务隐藏 |
| `settings.set` | `about.check_updates_on_startup` | 设置启动时检查更新 |
| `settings.set` | `about.update_channel` | 设置更新通道：`STABLE`/`TEST` |

## toggle：快速切换

`settings.toggle` 支持普通开关类 `settings.set` 目标；快捷设置字段开关不要用 toggle，改用 `quick_settings.set field.* --extra enabled:b:true/false`。

## action：执行设置动作

这些命令也属于设置指令，但不是 `set`：它们不会写入一个可复查的目标值，而是立即清理缓存、重置设置或清除资源。

| method | arg | 作用 |
|---|---|---|
| `settings.action` | `general.clear_thumbnail_cache` | 清除缩略图缓存 |
| `settings.action` | `general.clear_video_cache` | 清除缩略图、媒体列表快照、字幕转换和 MKV Cues seek 缓存，保留已下载字幕 |
| `settings.action` | `general.reset_settings` | 重置设置 |
| `settings.action` | `subtitle.clear_external_font` | 清除导入的外部字幕字体 |
| `settings.action` | `media.layout_scale_reset` | 重置文件夹与视频默认网格缩放为 `1.0`，保留目录覆盖 |
| `settings.action` | `player.reset_controls` | 把播放器控件编排恢复默认 |
| `settings.action` | `decoder.save_filter_preset` | 把当前滤镜参数保存为预设（value=名称） |
| `settings.action` | `decoder.apply_filter_preset` | 应用指定名称的滤镜预设（value=名称） |
| `settings.action` | `decoder.delete_filter_preset` | 删除指定名称的滤镜预设（value=名称） |
| `settings.action` | `audio.save_equalizer_preset` | 把当前均衡器曲线保存为预设（value=名称） |
| `settings.action` | `audio.apply_equalizer_preset` | 应用指定名称的均衡器预设（value=名称） |
| `settings.action` | `audio.delete_equalizer_preset` | 删除指定名称的均衡器预设（value=名称） |

新增 `settings.action` 指令时必须在本表追加用途说明。

# 快捷设置指令

快捷设置命令用于调整媒体列表快捷面板里的视图、布局、排序和字段显示。

- 本地媒体列表使用 `quick_settings.*`。
- 云端媒体列表使用 `cloud.quick_settings.*`，且 `server_id` 或 `id` 必填。
- 固定选项和数字用 `--extra value:s:<value>`。
- 字段开关用 `--extra enabled:b:true/false`。

## 本地快捷设置

本地排序支持 `TITLE`、`LENGTH`、`DATE`、`SIZE`、`PATH`；视图支持 `VIDEOS`、`FOLDERS`、`FOLDER_TREE`；布局支持 `LIST`、`GRID`。

| method | arg | 作用 |
|---|---|---|
| `quick_settings.get` | 空 | 读取本地快捷设置 |
| `quick_settings.set` | `view_mode` | 设置本地媒体视图：`VIDEOS`/`FOLDERS`/`FOLDER_TREE` |
| `quick_settings.set` | `layout_mode` | 设置本地媒体布局：`LIST`/`GRID` |
| `quick_settings.set` | `layout_scale` | 设置本地媒体布局缩放，`value` 范围 `0.75..1.5` |
| `quick_settings.set` | `sort_by` | 设置本地媒体排序字段：`TITLE`/`LENGTH`/`DATE`/`SIZE`/`PATH` |
| `quick_settings.set` | `sort_order` | 设置本地媒体排序方向：`ASCENDING`/`DESCENDING` |
| `quick_settings.set` | `field.duration` | 设置本地视频是否显示时长 |
| `quick_settings.set` | `field.extension` | 设置本地媒体是否显示扩展名 |
| `quick_settings.set` | `field.path` | 设置本地文件夹是否显示路径 |
| `quick_settings.set` | `field.played_progress` | 设置本地媒体是否显示播放进度标记 |
| `quick_settings.set` | `field.resolution` | 设置本地媒体是否显示分辨率 |
| `quick_settings.set` | `field.size` | 设置本地媒体是否显示大小 |
| `quick_settings.set` | `field.thumbnail` | 设置本地媒体是否显示缩略图 |

## 云端快捷设置

云端快捷设置按服务器独立保存。云端排序支持 `TITLE`、`SIZE`、`PATH`；布局支持 `LIST`、`GRID`。

| method | arg | 作用 |
|---|---|---|
| `cloud.quick_settings.get` | 空 | 读取指定服务器的云端快捷设置 |
| `cloud.quick_settings.set` | `layout_mode` | 设置指定服务器云端布局：`LIST`/`GRID` |
| `cloud.quick_settings.set` | `layout_scale` | 设置指定服务器云端布局缩放，`value` 范围 `0.75..1.5` |
| `cloud.quick_settings.set` | `sort_by` | 设置指定服务器云端排序字段：`TITLE`/`SIZE`/`PATH` |
| `cloud.quick_settings.set` | `sort_order` | 设置指定服务器云端排序方向：`ASCENDING`/`DESCENDING` |
| `cloud.quick_settings.set` | `field.extension` | 设置指定服务器是否显示扩展名 |
| `cloud.quick_settings.set` | `field.path` | 设置指定服务器目录是否显示路径 |
| `cloud.quick_settings.set` | `field.size` | 设置指定服务器是否显示大小 |
| `cloud.quick_settings.set` | `field.thumbnail` | 设置指定服务器是否显示缩略图 |
| `cloud.quick_settings.set` | `field.played_progress` | 设置指定服务器是否显示播放进度标记 |

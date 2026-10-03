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
| `quick_settings.set` | `layout_mode` | 同时设置本地文件夹与视频默认布局：`LIST`/`GRID` |
| `quick_settings.set` | `layout_scale` | 同时设置本地两类默认网格缩放，`value` 范围 `0.75..1.5` |
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
| `cloud.quick_settings.set` | `layout_mode` | 同时设置指定服务器两类默认布局：`LIST`/`GRID` |
| `cloud.quick_settings.set` | `layout_scale` | 同时设置指定服务器两类默认网格缩放，`value` 范围 `0.75..1.5` |
| `cloud.quick_settings.set` | `sort_by` | 设置指定服务器云端排序字段：`TITLE`/`SIZE`/`PATH` |
| `cloud.quick_settings.set` | `sort_order` | 设置指定服务器云端排序方向：`ASCENDING`/`DESCENDING` |
| `cloud.quick_settings.set` | `field.extension` | 设置指定服务器是否显示扩展名 |
| `cloud.quick_settings.set` | `field.path` | 设置指定服务器目录是否显示路径 |
| `cloud.quick_settings.set` | `field.size` | 设置指定服务器是否显示大小 |
| `cloud.quick_settings.set` | `field.thumbnail` | 设置指定服务器是否显示缩略图 |
| `cloud.quick_settings.set` | `field.played_progress` | 设置指定服务器是否显示播放进度标记 |


## 递归布局

以下参数同时适用于 `quick_settings.set` 和 `cloud.quick_settings.set`；云端仍需指定服务器。

| arg | 作用 |
|---|---|
| `folder.layout_mode` / `video.layout_mode` | 分别设置文件夹或视频布局，`value` 为 `LIST` 或 `GRID` |
| `folder.layout_scale` / `video.layout_scale` | 分别设置网格大小，`value` 范围 `0.75..1.5` |
| `folder.inherit` / `video.inherit` | 删除对应类型的目录覆盖，恢复上级设置；必须提供 `directory` |

- 不传 `--extra directory:s:<path>` 时修改媒体库默认值；传入时作用于该目录及未覆盖的子目录。
- `get` 也接受 `directory`，返回有效的 `folder`、`video` 布局及 `folder_source`、`video_source`；来源为 `default` 表示媒体库默认。
- 目录路径按实际文件层级匹配；父目录修改不会覆盖子目录的显式设置，即使两者当前值相同。
- 旧 `layout_mode`、`layout_scale` 参数保留为同时修改两类默认值的快捷方式，不改目录覆盖。

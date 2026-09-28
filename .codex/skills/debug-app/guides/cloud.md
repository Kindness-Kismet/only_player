# 云端指令

## 云服务器

云服务器指令用来快速准备云端服务器测试数据，支持 `FTP`、`WEBDAV`、`SMB`。

| method | arg | 作用 |
|---|---|---|
| `cloud.server.add` | 空 | 添加云服务器，`protocol` 填 `FTP`/`WEBDAV`/`SMB`，`host` 必填，`name`/`port`/`path`/`username`/`password`/`proxy_enabled`/`proxy_host`/`proxy_port` 可选 |
| `cloud.server.update` | id | 更新云服务器，也可用 `id` extra，其余参数同 `add` |
| `cloud.server.delete` | id | 删除云服务器，也可用 `id` extra |
| `cloud.server.clear` | 空 | 清空所有云服务器 |
| `cloud.server.list` | 空 | 列出当前云服务器 |

## 云端媒体

`server_id` 或 `id` 必填。

- `cloud.media.list` 的 `path` extra 只表示目录路径，默认使用服务器根路径；不支持用 `arg` 传目录。
- `cloud.media.open` 的 `path` 只表示文件路径，会自动用父目录建立播放列表。
- 按 `index`、`name`、`arg` 或 `value` 打开时，用 `directory_path` 指定目录上下文；不传则使用服务器根路径。

| method | arg | 作用 |
|---|---|---|
| `cloud.media.list` | 空 | 列出云端目录内所有可播放视频，目录用 `path` extra 传入，返回 name、path 和 size |
| `cloud.media.open` | 文件 path/name/index | 打开云端视频；`path` 为文件路径，`directory_path` 为目录上下文 |

云端快捷设置见 [快捷设置指令](quick_settings.md)。

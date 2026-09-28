# 播放列表与观看历史指令

播放列表和观看历史只覆盖本地媒体，从首页菜单进入，不是底部导航标签。指令用于准备和验证数据。`playlist.add` 默认添加本地视频，可用 `type` 指定 `video` 或 `folder`。

| method | arg | 作用 |
|---|---|---|
| `playlist.create` | 名称 | 创建播放列表，也可用 `name` extra |
| `playlist.add` | 空 | 向播放列表添加条目；`id` 为播放列表 id。本地视频用 `value/name/path` 匹配，整个文件夹用 `type=folder` 和 `path` |
| `playlist.list` | 空 | 列出播放列表，返回 id、title 和条目数 |
| `playlist.items` | id | 列出播放列表条目，也可用 `id` extra |
| `playlist.delete` | id | 删除播放列表，也可用 `id` extra |
| `playlist.clear` | 空 | 清空全部播放列表 |
| `history.list` | 可选过滤 | 列出观看历史，可用 `value/name/path` 过滤标题、路径或 media key |
| `history.remove` | 媒体 | 从观看历史移除一条，可用 `value/name/path` 匹配 |
| `history.clear` | 空 | 清空观看历史 |

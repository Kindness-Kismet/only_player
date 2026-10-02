# 收藏夹指令

收藏夹指令用于快速准备和验证收藏夹数据。`favorite.add` 的 `type` 必填，可填 `FAVORITE_FOLDER`、`LOCAL_VIDEO`、`LOCAL_FOLDER`、`REMOTE_SERVER_ROOT`、`REMOTE_DIRECTORY`、`REMOTE_FILE`。

| method | arg | 作用 |
|---|---|---|
| `favorite.add` | 空 | 添加收藏；本地视频用 `value/name/path` 匹配，远端项用 `server_id` 和 `path`，收藏夹目录可用 `name` |
| `favorite.list` | 空 | 列出收藏项，返回 id、type、title、path 和 parent |
| `favorite.delete` | id | 删除收藏项，也可用 `id` extra |
| `favorite.move` | id | 移动收藏项，`parent_id` 可选，空表示移动到收藏夹根级 |
| `favorite.clear` | 空 | 清空所有收藏项 |

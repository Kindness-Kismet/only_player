# 媒体库指令

媒体库指令用于验证媒体数据库、MediaStore、文件存在状态和回收站业务链路。

| method | arg | 作用 |
|---|---|---|
| `media.list` | 可选 name/path | 列出视频，返回 uri、name、path、isInRecycleBin 和文件存在状态 |
| `media.open` | uri/name/path | 按 uri、文件名或路径快速打开本地视频 |
| `media.move_to_recycle_bin` | uri/name/path | 直接调用 Repository 的回收站逻辑 |
| `media.move_to_folder` | uri/name/path | 直接调用 Repository 的视频移动逻辑，`target_folder` 填目标目录路径 |
| `media.move_folder_to_folder` | folder path | 直接调用 Repository 的文件夹移动逻辑，`target_folder` 填目标目录路径 |
| `media.restore_from_recycle_bin` | uri/name/path | 直接调用 Repository 的还原逻辑 |
| `media.delete_permanently` | uri/name/path | 直接永久删除视频 |
| `media.refresh` | 可选 path | 触发 `mediaSynchronizer.refresh()` |
| `media.scan_path` | path | 校验指定文件或目录存在后，刷新扫描指定路径 |
| `media.status` | uri/name/path | 返回数据库记录、回收站状态和文件存在状态 |

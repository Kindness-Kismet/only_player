- Fix SMB share browsing, video playback, and thumbnail reads, and improve playback buffering
- Keep cloud video lists responsive with thumbnail placeholders and adjust thumbnail loading concurrency to device capacity and observed performance
- Show cached cloud thumbnails without reopening remote videos to reduce repeated connections and waiting

---

- 修复 SMB 共享目录浏览、视频播放和缩略图读取问题，改善播放缓冲
- 保持云端视频列表流畅，先显示占位缩略图，再根据设备能力和实际取图效率动态调整加载数量
- 直接使用本地缓存展示云端缩略图，减少重复连接和等待
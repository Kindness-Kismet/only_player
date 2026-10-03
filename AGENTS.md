# Only Player - Project Rules

本地视频播放器，基于 Kotlin + Jetpack Compose + Hilt + Media3。

---

## Architecture

多模块分层架构，遵循单向数据流。

```
app                → 应用入口，导航图，Application
core/common        → 日志，调度器，扩展函数
core/model         → 纯数据模型，无 Android 依赖
core/database      → Room 数据库，DAO，Entity
core/datastore     → DataStore 数据源与序列化
core/data          → Repository 接口与实现，Mapper
core/domain        → Use Case
core/media         → 媒体扫描与同步服务
core/ui            → 共享 Compose 组件，主题，字符串资源
feature/player     → 播放器 UI 与播放流程
feature/settings   → 设置页面
feature/videopicker→ 媒体库浏览，搜索，快速设置
```

### Module Dependency

依赖方向严格自上而下，禁止循环依赖。

- `feature/*` 可依赖 `core/*`，禁止 feature 之间互相依赖
- `app` 依赖应用组装所需模块，负责导航、Activity 和 Application 组装
- `core/domain` 依赖 `core/data`、`core/model`、`core/common`
- `core/data` 依赖 `core/database`、`core/datastore`、`core/media`、`core/model`、`core/common`
- `core/media` 依赖 `core/common`、`core/database`、`core/datastore`、`core/model`
- `core/ui` 依赖 `core/model`
- `core/model` 不依赖任何其他 core 模块

### Layer Conventions

#### Model

- 位于 `core/model`，使用纯 Kotlin 类型，不依赖 Android
- 主要使用 `data class`、`enum`、`sealed` 或值对象
- 可实现 `Serializable` 或使用 `@Serializable`
- 仅允许模型不变量、序列化和轻量派生逻辑，不做 IO 或平台调用

#### Repository

- 核心 Repository 在 `core/data` 中定义接口和实现
- feature 内允许仅服务本 feature 的 IO helper，不跨 feature 暴露
- 流式查询返回 `Flow<T>`，状态订阅返回 `StateFlow<T>`
- 单次查询或写入使用 `suspend` 函数
- 核心 Repository 通过 Hilt `@Binds` 绑定接口到实现

#### Use Case

- 每个 Use Case 一个独立 class，无公共基类
- 使用 `operator fun invoke()` 作为唯一公开方法
- 流式结果返回 `Flow<T>`，一次性结果使用 `suspend`
- 通过 `@Inject constructor` 注入所需 Repository、Use Case 和 Dispatcher
- 使用 `flowOn(dispatcher)` 切换执行上下文

#### ViewModel

- 使用 `@HiltViewModel` + `@Inject constructor`
- 内部 `MutableStateFlow` + 公开 `val uiState = *.asStateFlow()`
- 或使用 `.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), initialValue)`
- 用户交互优先通过 `fun onEvent(event: SealedEvent)` 分发，播放器等高频控制可暴露专用方法
- 在 `viewModelScope.launch` 中执行异步操作

#### UI State & Event

- UI State 使用 `@Stable data class` 或 `sealed interface`
- Event 使用 `sealed interface`，子类为 `data class` 或 `data object`
- 加载状态使用 `DataState<T>`（Loading / Success / Error）

#### Navigation

- 路由使用 `@Serializable data class` 或 `@Serializable data object`（Type-Safe Navigation）
- Compose 页面提供 `NavGraphBuilder.xxxScreen()` 扩展和 `NavController.navigateTo*()` 扩展
- `feature/player` 当前使用 Activity / Intent 播放入口，不强制提供 Compose Navigation 扩展
- feature 之间通过回调参数通信，不直接引用彼此

#### Dependency Injection

- Module 使用 `@InstallIn(SingletonComponent::class)`
- 接口绑定用 `@Binds`（interface Module），实例提供用 `@Provides`（object Module）
- Dispatcher 注入使用自定义 `@Dispatcher(DispatcherType.IO/Default)` Qualifier
- 应用级 CoroutineScope 使用 `@ApplicationScope` Qualifier

---

## Kotlin Conventions

### Naming

| 类别 | 规则 | 示例 |
|---|---|---|
| 类 / 接口 / Object | PascalCase | `PlayerViewModel`, `MediaRepository` |
| 函数 / 变量 | camelCase | `getVideoByUri`, `folderPath` |
| 常量 | UPPER_SNAKE_CASE，定义在 companion object 或顶层 | `private const val TAG = "..."` |
| 包名 | 全小写，点分隔 | `one.only.player.core.data.repository` |
| Composable 函数 | PascalCase | `VideoItem`, `MediaPlayerScreen` |
| 回调参数 | `on` + 事件名 | `onClick`, `onNavigateUp`, `onPlayVideo` |
| 函数类型参数 | 动词或名词短语 | `transform: suspend (T) -> T` |
| Boolean 变量 | 必须命名为可直接判断真假的短语，按语义使用 `is` / `has` / `can` / `should` 前缀 | `isLoading`, `hasPermission`, `canDelete`, `shouldRetry` |

补充约束：

- `is` 用于状态、特征、分类结果
- `has` 用于拥有、包含、存在
- `can` 用于能力或当前条件下是否允许执行
- `should` 用于策略、推荐或派生决策
- 优先使用正向命名，避免双重否定，如 `isEnabled` 优于 `isDisabled`
- 属性与返回 `Boolean` 的函数都应命名为判断句，如 `isSelected`、`hasAccess()`、`canDelete()`
- 禁止使用不带谓词语义的布尔命名，如 `loading`、`permission`、`visible`
- 禁止用不同表达描述同一语义，如同时使用 `canDelete` 和 `isDeleteEnabled` 表示同一件事
- 布尔参数需保持语义清晰，避免 `flag`、`state` 这类无语义命名
- 语义复杂或存在多种状态时，优先使用 enum 或 sealed type，不要硬塞为 `Boolean`
- 非必要不要使用 `Boolean?`，避免 `null` 带来额外语义歧义

### Syntax Style

- 简短逻辑使用单表达式函数（`=`），复杂逻辑使用块体
- 密封类型分发使用 `when` 表达式，覆盖所有分支，不使用 `else` 兜底
- Nullable 处理优先用 `?.`、`?:` 和 `takeIf`；复杂场景用显式 `if` 检查
- 作用域函数：`let` 用于 null 链式操作，`apply` 用于对象初始化，`use` 用于资源管理
- 扩展函数按接收者类型放在独立文件中（如 `Context.kt`、`Uri.kt`、`File.kt`）

### Import

- 禁止通配符导入
- 按包名字母顺序排列

### Trailing Comma

所有多行参数列表、属性列表使用尾随逗号：

```kotlin
data class Video(
    val id: Long,
    val path: String,
    val duration: Long,  // <- 尾随逗号
)
```

---

## Guard Clause

使用卫语句减少嵌套，保持函数逻辑扁平。

### Rules

1. 函数入口处优先验证前置条件，不满足时立即 `return` / `return defaultValue` / `throw`
2. 最大嵌套深度不超过 3 层（不含 class 和 fun 本身的层级）
3. 条件取反提前退出，正向逻辑留在主路径
4. 禁止 `if-else` 链超过 3 个分支，改用 `when`

### Patterns

```kotlin
// 参数校验
fun moveItem(fromIndex: Int, toIndex: Int) {
    if (fromIndex == toIndex) return
    if (fromIndex !in playlist.indices || toIndex !in playlist.indices) return
    // 主逻辑
}

// Null 检查
fun isRecentlyPlayed(video: Video?): Boolean {
    if (recentlyPlayedVideo == null) return false
    if (video == null) return false
    return video.path == recentlyPlayedVideo.path
}

// 集合检查
fun onSearch(query: String) {
    if (query.isBlank()) return
    // 主逻辑
}
```

### Anti-patterns

```kotlin
// 禁止：深层嵌套
fun process(data: Data?) {
    if (data != null) {
        if (data.isValid) {
            if (data.items.isNotEmpty()) {
                // 三层嵌套
            }
        }
    }
}

// 应改为：
fun process(data: Data?) {
    if (data == null) return
    if (!data.isValid) return
    if (data.items.isEmpty()) return
    // 主逻辑
}
```

---

## Path Handling

`/storage/emulated/0` 走 FUSE，大小写不敏感且保留写法，同一文件可能以不同写法进入应用。路径语义由类型承载：比较按大小写归一，取值保留真实写法。

### Rules

1. 外部存储上的媒体路径用 `StoragePath`（`core/model`）表示，相等、前缀和去重都交给它
2. 路径在进入应用的边界（Intent、MediaStore、文件系统遍历、DataStore 反序列化）先 `canonicalPathOrSelf()` 解析 `.`、`..` 与符号链接，再 `StoragePath.of()` 归一大小写与分隔符，之后一路传 `StoragePath`
3. 包含关系用 `isInside()`，名称用 `name`，单级目录项按名匹配用 `StoragePath.namesEqual()`
4. 访问文件、写入数据库、展示给用户都用 `value`，它是真实写法
5. 持久化的路径字段直接声明为 `StoragePath`，序列化结果是裸字符串
6. Room 里的路径列声明 `collate = ColumnInfo.NOCASE`，让主键和索引的比较跟外部存储一致
7. 未声明 collation 的表按路径查询时在 SQL 里写 `COLLATE NOCASE`；一批路径用 `getAllByPaths` 一次取回，在内存里按 `StoragePath` 建映射
8. `StoragePath` 表示外部存储上的路径；`/data` 这类大小写敏感的挂载点用 `String` 表示

### Usage

```kotlin
// 边界：解析真实位置后定型
val path = StoragePath.of(intentPath.canonicalPathOrSelf())

// 比较：交给类型
if (path.isInside(excludedFolder)) return

// 取值：真实写法用于访问与展示
File(path.value).delete()
```

---

## Code Formatting

### EditorConfig

- 缩进：4 空格
- 行长度：不硬性限制，建议 120 字符内
- 尾随逗号：启用
- ktlint code style：`android_studio`
- Composable 函数跳过函数命名检查

### Formatting Rules

- 多参数函数声明每个参数独占一行
- 链式调用超过 2 层时换行，续行缩进 4 空格
- `when` 分支体为单行时写在同一行，多行时使用块体
- 方法之间保留一个空行
- 逻辑段落之间用空行分隔

### Coroutines

- 使用 `@Dispatcher` 注入 Dispatcher，不直接引用 `Dispatchers.*`
- Flow 操作链使用 `flowOn(dispatcher)` 控制执行上下文
- ViewModel 中使用 `viewModelScope.launch`

### Error Handling

- DataSource / Repository 层使用 try-catch 包裹 IO 操作，通过 Logger 记录错误
- Presentation 层使用 `DataState<T>` 传递加载和错误状态
- 禁止使用 `exception.printStackTrace()`，统一使用 `Logger.error()`

---

## Git Workflow

- 创建提交、修改提交信息或整理提交历史前，必须阅读并遵守 [commit 技能](.codex/skills/commit/SKILL.md)，提交信息格式以该技能为准。
- 创建、说明或合并 Pull Request 前，必须阅读并遵守 [pr 技能](.codex/skills/pr/SKILL.md)。功能 PR 打向 `dev`，只有晋升正式版时才把 `dev` 打向 `main`。
- 任务每完成一部分就对该部分改动发起 commit，不要等全部做完再一次性提交
- 除非用户明确要求，不得擅自执行 git push；推送前必须说明待推送的提交内容并等待用户确认

## Version Bump

使用 `/version-bump` 触发；版本号、changelog、依赖更新和版本提交模板遵守本地 `version-bump` 技能，通用提交规则仍遵守 `commit` 技能。

## Code Quality

除非明确要求，不新增或调整 test 文件。

只有改动涉及 Kotlin、Gradle、资源、Manifest 等代码或格式相关文件时，才运行 `ktlintFormat` 和 `ktlintCheck`。
纯文档、changelog、提交信息、issue 元数据、版本说明等非代码改动，不需要运行 check，除非用户明确要求。

首次构建或环境缺失时先执行 prebuild，它会补齐 Gradle wrapper、把便携 Temurin JDK 26 下载到 `build/jdk/` 并校验 Android SDK：

```bash
python scripts/prebuild.py
```

编译 APK 优先使用项目脚本，按需指定 ABI 和构建类型；脚本会把 `build/jdk/` 下的便携 JDK 传给 Gradle：

```bash
python scripts/build.py build-apk --abi arm64-v8a --build-type debug
```

提交代码格式相关改动前使用 `ktlintFormat` 自动格式化，再用 `ktlintCheck` 验证：

```bash
./gradlew ktlintFormat ktlintCheck
```

涉及构建、运行逻辑、依赖、资源、Manifest 或 APK 行为时，按需运行完整构建命令，并检查 test 报告；当前 `test` 不能只看退出码判断通过：

```bash
./gradlew ktlintCheck test assembleDebug --warning-mode=all
```

### UI 控件标识

所有需要交互的 Compose 控件必须添加 `Modifier.testTag()` 或 `contentDescription`，保证 debug 指令和 UI 自动化可以稳定定位。

JDK 26 required (portable copy provisioned by `scripts/prebuild.py`). Android minSdk 30, targetSdk 37.

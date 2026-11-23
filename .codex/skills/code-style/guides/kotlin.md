# Kotlin 风格

适用于项目 Kotlin、Jetpack Compose 和 Gradle Kotlin DSL。格式配置以根 `.editorconfig` 为准。

## 命名

| 类别 | 规则 | 示例 |
|---|---|---|
| 类、接口、对象、类型别名 | PascalCase | `PlayerViewModel`、`MediaRepository` |
| 函数、属性、参数、局部变量 | camelCase | `getVideoByUri`、`folderPath` |
| 编译期常量 | UPPER_SNAKE_CASE，定义在 companion object 或顶层 | `private const val TAG = "Player"` |
| 包名 | 全小写，点分隔 | `one.only.player.core.data.repository` |
| Composable 函数 | PascalCase | `VideoItem`、`MediaPlayerScreen` |
| 回调参数 | `on` + 事件名 | `onClick`、`onNavigateUp`、`onPlayVideo` |
| 其他函数类型参数 | 动词或名词短语 | `transform: suspend (T) -> T` |
| Boolean 属性、参数与返回 Boolean 的函数 | 可直接判断真假的短语，按语义使用 `is`、`has`、`can`、`should` 前缀 | `isLoading`、`hasPermission`、`canDelete`、`shouldRetry` |

- `is` 表示状态、特征或分类，`has` 表示拥有、包含或存在，`can` 表示能力或当前允许条件，`should` 表示策略、推荐或派生决策。
- 布尔命名优先正向，避免双重否定；禁止 `loading`、`permission`、`visible` 这类没有谓词的项目自有布尔名，也不使用 `flag`、`state` 等无语义参数名。
- 同一语义使用同一表达，不同时使用 `canDelete` 与 `isDeleteEnabled` 指代同一条件。
- 多状态优先用 enum 或 sealed type，非必要不使用 `Boolean?`。
- 公开只读状态与私有可变存储可配对使用 `entries`、`_entries`。挂起函数按操作命名，如 `readEntry`，不添加无意义的异步后缀。
- 重写函数、Java 互操作与框架回调保留原 API 签名；运算符使用语言规定名称。重命名公共参数时检查 Kotlin 命名调用，布尔属性重命名时核对 JVM getter 和 Java 调用。

## 语法

- 简短逻辑用单表达式函数 `=`，复杂逻辑用块体。
- 密封类型用 `when` 穷举所有分支，不加 `else` 兜底。
- Nullable 优先用 `?.`、`?:`、`takeIf`，复杂场景显式检查。
- `let` 用于 null 链式操作，`apply` 用于初始化，`use` 用于资源管理。
- 扩展函数按接收者放独立文件，如 `Context.kt`、`Uri.kt`、`File.kt`。
- 禁止通配符导入；导入按包名字母顺序排列。

## 卫语句

先处理无效输入、空值和空集合，再进入主逻辑；最大嵌套不超过 3 层，超过 3 分支的 `if-else` 链改用 `when`。

```kotlin
fun moveItem(fromIndex: Int, toIndex: Int) {
    if (fromIndex == toIndex) return
    if (fromIndex !in playlist.indices || toIndex !in playlist.indices) return
    // 仅处理有效且不同的索引。
}

fun process(data: Data?) {
    if (data == null) return
    if (!data.isValid) return
    if (data.items.isEmpty()) return
    // 前置条件满足后执行处理。
}
```

## 格式

- 缩进 4 空格；行长不硬性限制，建议 120 字符内。
- ktlint 使用 `android_studio`；Composable 函数跳过函数命名检查。
- 多参数函数声明每个参数独占一行；所有多行参数和属性列表使用尾随逗号。
- 链式调用超过 2 层时换行，续行缩进 4 空格。
- `when` 单行分支写在同一行，多行分支用块体。
- 方法之间留一个空行，逻辑段落之间用空行分隔。

```kotlin
data class Video(
    val id: Long,
    val path: String,
    val durationMs: Long,
)
```

## 验证

代码格式相关修改提交前按 `AGENTS.md` 的额度串行执行 `ktlintFormat`、`ktlintCheck`。重命名同时检查调用方和外部契约；只修改技能文档时检查内容与配置一致性。

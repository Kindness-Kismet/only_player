# Kotlin 资源

本项目使用 Android 原生资源，Compose 通过 `stringResource(R.string.key)` 与 `pluralStringResource` 获取文案；非 Compose 层通过适用的 Android 资源入口获取，不在纯模型层引入 Android 依赖。

| 语言 | 实际来源 |
|---|---|
| 默认英文 | `core/ui/src/main/res/values/strings.xml` |
| 简体中文 | `core/ui/src/main/res/values-zh-rCN/strings.xml` |
| 繁体中文 | `core/ui/src/main/res/values-zh-rTW/strings.xml` |

- 资源名采用英文小写下划线，先功能再用途；各语言同义项使用同一键。修改前检查已有键与使用位置，避免单纯因相同英文拼写误复用不同语义。
- 格式参数按位置和类型对应，如 `%1$s`、`%2$d`；参数代表的含义在各语言保持一致，语序可调整。字面百分号按 Android 格式规则处理。
- 数量决定词形时用 `plurals`，保留该语言所需数量分支；显示数量时同时向格式化调用传入数量值。
- 遵守 XML 转义与 Android 字符串的引号、撇号规则；标签或注解属于富文本语义时保留对应结构。
- `translatable="false"` 用于确实不翻译的资源，不能用来跳过应有翻译。缺少本地化项时 Android 使用默认资源，验收时核对当前语言实际显示内容。
- 变更检查覆盖重复资源名、引用存在、格式参数与复数项，再按影响执行资源构建。仅维护技能文档时不重建 APK。

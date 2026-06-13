---
name: version-bump
description: Bump the app version number and generate changelog. Trigger when user says "更新版本号", "提高版本号", "版本号提升", or "version bump".
user_invocable: true
---

## 流程

1. 读取 `app/build.gradle.kts` 中的 `versionCode` 和 `versionName`
2. 检查是否存在未提交内容；如果有，先根据其更改内容执行格式化、校验并提交，提交必须只包含这些业务改动，不得把版本号与 changelog 混入同一个提交
3. 执行 `./gradlew versionCatalogUpdate`，更新 `gradle/libs.versions.toml` 中可升级的版本与插件声明
4. 检查 `gradle/libs.versions.toml` 是否仍保留构建脚本依赖的自定义版本别名；若 `versionCatalogUpdate` 删除了 `android-compileSdk`、`android-targetSdk`、`android-minSdk`、`android-jvm` 这类项目自定义键，必须立即补回，保证构建脚本访问器不失效
5. 确定新版本号：
   - 默认：patch +1（如 1.0.3 → 1.0.4），`versionCode` +1
   - 用户指定了具体版本号时，使用用户指定的版本
6. 通过 `git log` 查找上一次版本号提升的 commit，收集此后所有变更
7. 归纳为面向用户的功能描述，忽略纯重构、CI 修复、GitHub Action、发布脚本、代码风格、调试指令等不影响普通用户体验的改动
8. 识别本次版本涉及的 GitHub issue：
   - 用 `gh` 读取相关 issue 原文
   - 逐条核对本次改动是否**完整满足** issue 要求
   - 只有在 issue 要求被本次改动完整满足时，提交信息才允许追加 `close #xxxx`
   - 若只是部分满足，或无法证明已完整满足，则**不要**追加 `close #xxxx`
9. 修改 `app/build.gradle.kts` 的 `versionCode` 和 `versionName`
10. **覆盖写入** `.github/CHANGELOG.md`，必须先清空旧日志内容，只保留上一个版本号提升 commit 之后到当前版本之间的新增功能变化，不保留更早版本的日志
11. 执行自动格式化与校验指令，至少运行 `./gradlew ktlintFormat ktlintCheck`
12. 版本号与 changelog 更新后必须及时提交，提交信息基础格式：`chore: bump version to {version} and update changelog`
    - 仅当第 8 步确认完整满足某个 issue 时，才在提交信息末尾追加 `close #xxxx`

## 更新日志格式

```markdown
- 中文改动描述 1
- 中文改动描述 2

<details>
<summary>English Version</summary>

- English change 1
- English change 2

</details>
```

规则：
- 不写版本号标题，发布标题已有体现
- 更新日志禁止黑话，要求用自然拟人的语气来写
- 中英文一一对应
- 每条以动词开头，简洁描述用户可感知的变化
- 禁止写入 GitHub Action、发布脚本、构建产物命名、CI 调整、调试指令变更等非用户可感知变化
- 末尾无空行
- issue 校验必须基于 `gh` 返回的原文，不允许凭印象追加 `close #xxxx`
- 版本号更新只处理本地改动与本地提交，push 阶段交给用户自己执行

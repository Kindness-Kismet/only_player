---
name: version-bump
description: 更新 Only Player 版本号、整理更新日志并创建版本提交。用户说「更新版本号」「提高版本号」「版本号提升」或「version bump」时使用。
---
## ⚠️ 两条最容易被忘记的规则

1. **CHANGELOG.md 默认整体重写，不是追加。** 它是“这次发布”的说明书，不是历史累积日志——默认清空旧内容，只写本次区间的变更。**仅当用户在本次对话中明确要求“这次用追加”时才允许追加**，且只对本次生效，下次执行不参考上次的选择，仍然默认重写。
2. **每条日志写的是功能现在的最终样子，不是这次做了哪些操作。** 同一功能/bug 在区间内被改了不止一次，只写一条，描述改完之后的最终效果，不按 commit 顺序拆开写。

## 流程
1. 读取 `app/build.gradle.kts` 中的 `versionCode` 和 `versionName`
2. 检查是否存在未提交内容；如果有，先根据其更改内容执行格式化、校验并提交，提交必须只包含这些业务改动，不得把版本号与 changelog 混入同一个提交
3. 执行 `./gradlew versionCatalogUpdate`，更新 `gradle/libs.versions.toml` 中可升级的版本与插件声明
4. 检查 `gradle/libs.versions.toml` 是否仍保留构建脚本依赖的自定义版本别名；若 `versionCatalogUpdate` 删除了 `android-compileSdk`、`android-targetSdk`、`android-minSdk`、`android-jvm` 这类项目自定义键，必须立即补回，保证构建脚本访问器不失效
5. 确定新版本号：
   - 默认：patch +1（如 1.0.3 → 1.0.4），`versionCode` +1
   - 用户指定了具体版本号时，使用用户指定的版本
6. 通过 `git log` 查找上一次版本号提升的 commit，收集此后所有变更
7. 归纳为面向用户的功能描述，忽略纯重构、CI 修复、GitHub Action、发布脚本、代码风格、调试指令等不影响普通用户体验的改动。同一功能被多次改动时合并为一条最终效果描述，不要按 commit 拆开写
8. 识别本次版本涉及的 GitHub issue。**不得只 grep commit 信息里的 `#编号`**——开发时往往没写编号，只看 commit 会漏掉本次实际解决的 issue：
   - 先用 `gh issue list --state open --limit 100 --json number,title` 列出全部未关闭 issue
   - 拿第 7 步归纳出的每条用户可见改动，与每个 open issue 的标题逐一比对，挑出主题相关的候选
   - 对每个候选用 `gh issue view <编号> --json title,body,state` 读原文，再核对本次改动是否**完整满足**其要求
   - 只有在 issue 要求被本次改动完整满足时，提交信息才允许追加 `close #xxxx`
   - 若只是部分满足、实现方式与 issue 期望不一致，或无法证明已完整满足，则**不要**追加 `close #xxxx`
   - 得出「本次无关联 issue」这个结论前，必须已经列过 open issue 并逐条比对过，不能因为 commit 里没有编号就直接下结论
9. 修改 `app/build.gradle.kts` 的 `versionCode` 和 `versionName`
10. 更新 `.github/CHANGELOG.md`：
    - 默认**整体重写**：清空旧内容，只保留上一次版本号提升 commit 之后到当前版本的变更
    - 仅当用户本次明确要求追加时，保留旧内容并把本次变更追加进去；没有明确要求就一律重写，不要因为上次是追加就顺着延续
11. 执行自动格式化与校验指令，至少运行 `./gradlew ktlintFormat ktlintCheck`
12. 版本号与 changelog 更新后必须及时提交，提交信息基础格式：`chore: bump version to {version} and update changelog`
    - 仅当第 8 步确认完整满足某个 issue 时，才在提交信息末尾追加 `close #xxxx`
13. **提交前自检**：
    - [ ] 本次是重写还是追加？没有用户明确要求追加的话必须是整体重写，文件里不能有本次区间之外的旧版本条目
    - [ ] 每条日志是否是最终效果，而不是按 commit 罗列的过程？重复/互相修正的条目合并成一条
    - [ ] 中英文条目一一对应、数量一致
    - [ ] `close #xxxx` 都基于 `gh` 读到的 issue 原文核实过，不是凭印象加的
    - [ ] 是否执行过 `gh issue list` 并把每条改动与 open issue 标题比对过？只 grep commit 编号不算做过第 8 步
    - 任一项不通过，回到对应步骤重做

## 更新日志格式
```markdown
- English change 1
- English change 2

---

- 中文改动描述 1
- 中文改动描述 2
```
规则：
- 英文在上，中文在下，中间用单独一行 `---` 分隔
- 不写版本号标题、日期和分类小节，发布标题已有体现
- 条目平铺，不折叠，不用 emoji，条目末尾不加标点
- 更新日志禁止黑话，要求用自然拟人的语气来写
- 中英文一一对应
- 每条以动词开头，简洁描述用户可感知的变化；同一改动只写最终效果，不写过程，净效果为零则不写
- 禁止写入 GitHub Action、发布脚本、构建产物命名、CI 调整、调试指令变更等非用户可感知变化
- 末尾无空行
- issue 校验必须基于 `gh` 返回的原文，不允许凭印象追加 `close #xxxx`
- issue 关联以功能主题匹配为准，不以 commit 是否写了编号为准
- 版本号更新只处理本地改动与本地提交，push 阶段交给用户自己执行

## 两端同步

本技能同时保存在 `.codex/skills/version-bump/` 和 `.claude/skills/version-bump/`。修改任一端时，必须同步更新另一端，保持所有同名文件内容一致。

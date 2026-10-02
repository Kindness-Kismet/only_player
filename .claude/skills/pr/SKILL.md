---
name: pr
description: 按 Only Player 分支与发布通道规范创建、说明和合并 Pull Request。用户说「开 PR」「提 PR」「创建 PR」「pull request」「晋升到 main」「合到正式版」时使用。
---

# Pull Request

本仓库没有独立测试套件作为合并门槛。PR 检查以 `测试检查` 工作流里的 ktlint 为准。

## 分支

| 分支 | 用途 |
|---|---|
| `dev` | 日常开发。产品改动合入后，CI 自动发 `vX.Y.Z-betaN` 测试包 |
| `main` | 正式版。`versionName` 变更后，CI 发稳定版 |

## 目标分支

- 功能、修复、重构、CI、文档：PR 打向 **`dev`**
- 只有晋升正式版时才打向 **`main`**，且 head 必须是 `dev`
- 禁止把功能分支直接打向 `main`

## 标题与正文

- 标题遵守 [commit 技能](../commit/SKILL.md)：英文、`type(scope): summary`、祈使句、末尾不加句号
- 正文用简体中文即可，写清改了什么、怎么验证
- 关联 issue 的规则与 commit 技能相同：读过原文且完整满足才允许 `close #编号`，否则用 `Refs #编号` 或不写

## 创建

1. 工作区干净，相关改动已按 commit 技能提交
2. 推送前说明待推送提交并取得用户确认；已有覆盖本次推送的明确确认时不重复询问
3. 用 `gh pr create` 指定 `--base`：日常用 `dev`，晋升用 `main`
4. 创建后把 PR 链接发给用户，不要自行合并

日常示例：

```bash
gh pr create --base dev --head <branch> --title "feat(settings): add update channel" --body "<简体中文说明>"
```

晋升示例：

```bash
gh pr create --base main --head dev --title "chore: promote dev to main" --body "晋升当前 dev 到正式通道，不含版本号提升。"
```

## 检查

- PR 上只要求 `测试检查`（ktlint）通过
- 不要因为没有单元测试、UI 测试或设备测试就阻塞开 PR
- 新增可交互 Compose 控件仍须带 `testTag` 或 `contentDescription`，这是代码规范，不是额外的 PR 测试门

## 合并与发布

- 未得到用户明确要求时，不要合并、不要删除分支
- 合入 `dev` 后若触发测试版构建，这是预期行为
- version-bump 的结果必须落到 `main` 才会发正式版。可以在 `dev` 上 bump，条件是紧接着把 `dev` 晋升到 `main`；不要 bump 完停在 `dev`，那会让该版本号永远发不出去
- 晋升合入 `main` 之后，推送 `main` 就会触发正式版发布；推送前按 commit 技能取得用户确认

## 两端同步

本技能同时保存在 `.codex/skills/pr/` 和 `.claude/skills/pr/`。修改任一端时，必须同步更新另一端，保持所有同名文件内容一致。

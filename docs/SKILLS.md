# Agent Skills

Agent skills let GLM-CLI discover reusable instructions from your repo or home directory.

## Create Skills

Create one folder per skill name and put a `SKILL.md` inside:

- `.glm/skills/<name>/SKILL.md`
- `~/.glm/skills/<name>/SKILL.md`
- `.claude/skills/<name>/SKILL.md`
- `~/.claude/skills/<name>/SKILL.md`

## SKILL.md Format

```yaml
---
name: my-skill
description: Brief description
license: MIT
compatibility: glm-cli
metadata:
  key: value
---

## What I do
Detailed description...

## When to use me
When to apply...
```

## Use Skills

```
skill({ list_available: true })  // List skills
skill({ name: "git-release" })   // Load skill
```

## CLI Commands

List all available skills:
```bash
glm skill --list
glm skill -l
```

Show details for a specific skill:
```bash
glm skill git-release
glm skill --path git-release  // Show skill file path
```

## Configuration

```toml
[skills]
enabled = true

[skills.skill_permissions]
"git-release" = "allow"
"code-review" = "allow"
"experimental-*" = "ask"
"*" = "allow"
```

## Agent Integration

Skills are automatically available to agents via the `skill` tool. Agents can:
1. List available skills with `skill({ list_available: true })`
2. Load a skill with `skill({ name: "skill-name" })`

When a skill is loaded, its content is added to the agent's system prompt.

## Example Skills

### code-review
Performs thorough code reviews focusing on code quality, security, and best practices.

### git-release
Creates consistent releases and changelogs by analyzing git history.

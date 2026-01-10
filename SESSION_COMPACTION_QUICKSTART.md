# Session Compaction - Quick Start Guide

## Enable Session Compaction

Add to `~/.glm/config.toml`:

```toml
[behavior]
auto_compact = true
max_context_tokens = 8000
compaction_threshold = 75
```

## How It Works

When running a long agent session:

1. **Monitors** context size before each LLM call
2. **Warns** when reaching 75% of token limit (7,500 tokens)
3. **Compacts** when reaching 90% of token limit (7,200 tokens)
   - Intelligently removes old messages
   - Preserves system prompts and recent context
   - Generates summary of removed context
4. **Continues** with optimized history

## Configuration Options

| Option | Default | Description |
|--------|---------|-------------|
| `auto_compact` | `true` | Enable automatic compaction |
| `max_context_tokens` | `8000` | Maximum tokens before compaction |
| `compaction_threshold` | `75` | Percentage at which to warn (75-100) |

## View Status

During a session, you'll see:

```
[INFO] Session compacted: 7200 → 4800 tokens (removed 15 messages)
```

This means:
- Used 7,200 tokens before compaction
- Reduced to 4,800 tokens after
- Removed 15 messages from history

## Disable Compaction

To disable automatic compaction:

```toml
[behavior]
auto_compact = false
```

## Adjust Thresholds

For more aggressive compaction (trigger earlier):

```toml
[behavior]
compaction_threshold = 65  # Trigger at 65% instead of 75%
```

For less aggressive compaction (trigger later):

```toml
[behavior]
compaction_threshold = 85  # Trigger at 85% instead of 75%
```

## Increase Context Window

If you have more tokens available:

```toml
[behavior]
max_context_tokens = 16000  # For longer sessions
```

## What Gets Removed

Compaction prioritizes:

1. ✅ **Kept** - System prompts, latest messages, tool calls
2. ❌ **Removed** - Older conversations, middle messages
3. 📝 **Summarized** - Removed context becomes summary

## Manual Compaction

(Currently automatic only)

To force a fresh start:
1. End current session (`Ctrl+C`)
2. Start new session with `glm agent "new task"`

## Performance

- Compaction takes ~100-500ms depending on history size
- No compaction when below threshold
- Transparent to agent - continues with optimized context

## Troubleshooting

**"Context at X% - compacting" keeps appearing**
- Token threshold too low, increase `max_context_tokens`
- Tasks generating many tool calls, reduce `max_steps`

**Compaction removed too much context**
- Increase `max_context_tokens`
- Decrease `compaction_threshold` (e.g., 70 instead of 75)

**Want to preserve old conversations**
- Disable compaction: `auto_compact = false`
- Manage sessions manually (create new sessions regularly)

## Default Behavior

By default (no config):
- ✅ Auto-compaction enabled
- ✅ Threshold at 75% (6,000 tokens for 8K limit)
- ✅ System prompts always preserved
- ✅ Recent context prioritized
- ✅ Summaries generated for removed context

---

**Need help?** See `SESSION_COMPACTION_INTEGRATION.md` for technical details.

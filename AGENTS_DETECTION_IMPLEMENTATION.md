# AGENTS.md Detection Implementation Summary

## Overview

Successfully implemented AGENTS.md detection and loading system for glm-cli-jbang, inspired by opencode architecture. The system automatically detects and loads project-specific instructions from multiple sources.

## Implementation Details

### Phase 1: Filesystem Utilities ✅
**File**: `core/Filesystem.groovy` (NEW)

Implemented core filesystem utilities:
- `findUp()` - Search upward through directory tree for a specific file
- `globUp()` - Search upward using glob patterns with ripgrep
- `globToRegex()` - Convert glob patterns to regex
- `contains()` - Check if one path contains another
- `normalizePath()` - Normalize absolute paths

### Phase 2: Instructions Detection ✅
**File**: `core/Instructions.groovy` (NEW)

Implemented instruction detection logic:
- Searches for local `AGENTS.md`, `CLAUDE.md`, `CONTEXT.md`
- Checks global locations (`~/.glm/AGENTS.md`, `~/.claude/CLAUDE.md`)
- Supports custom instruction patterns from config
- Uses `LinkedHashSet` for ordered, deduplicated results
- Gracefully handles missing files (logs warnings)

Key methods:
- `detect()` - Find all instruction file paths
- `loadAll()` - Load content from detected files
- `findStopDirectory()` - Determine where to stop searching
- `resolveInstructionPath()` - Resolve relative/absolute paths

### Phase 3: Configuration Support ✅
**File**: `core/Config.groovy`

Added `instructions` field:
```groovy
@JsonProperty("instructions")
List<String> instructions = []
```

Configured via `~/.glm/config.toml`:
```toml
[instructions]
instructions = [
    "docs/*.md",
    "~/custom-rules.md",
    "packages/*/AGENTS.md"
]
```

### Phase 4: Agent Integration ✅
**Files**: `core/Agent.groovy`, `core/AgentConfig.groovy`

Agent.groovy - Modified `run()` method:
- Loads custom instructions before processing user prompt
- Adds each instruction as system message
- Preserves existing behavior

AgentConfig.groovy - Modified `loadPrompt()` method:
- Loads base prompt from `prompts/{type}.txt`
- Appends AGENTS.md instructions to agent prompt
- Falls back to description if no prompt file

### Phase 5: TUI Integration ✅
**File**: `tui/LanternaTUI.groovy`

Modified `processInput()` method:
- Loads AGENTS.md instructions on each input
- Adds as system messages before agent-specific prompt
- Maintains proper order: Custom instructions → Agent prompt → User input

### Phase 6: Chat Command Integration ✅
**File**: `commands/ChatCommand.groovy`

Modified `processInput()` method:
- Loads AGENTS.md instructions on first message
- Only loads once per chat session
- Adds as system messages

### Phase 8: Root Detection Enhancement ✅
**File**: `core/RootDetector.groovy`

Added new methods:
- `findWorktreeRoot()` - Find git root or fall back to home
- `findStopDirectory()` - Find project root by marker files

Stop markers include:
- `.git`, `.opencode`
- `package-lock.json`, `yarn.lock`, `bun.lock`

### Phase 9: Init Command ✅
**Files**: `commands/InitCommand.groovy` (NEW), `commands/GlmCli.groovy`

Created `/init` command:
```bash
glm init                    # Create AGENTS.md in current directory
glm init --path /path/to/project  # Create in specific directory
glm init                    # Improve existing AGENTS.md
```

Features:
- Analyzes codebase structure
- Detects build/test commands
- Includes existing rules (Cursor, Copilot)
- Creates comprehensive guidelines (~150 lines)
- Supports existing file improvement

### Phase 10: Testing & Documentation ✅
**Files**: `tests/InstructionsTest.groovy` (NEW), `AGENTS.md`, `CONFIGURATION.md`

Created comprehensive test suite:
- `testFindUp()` - Verify upward file search
- `testDetect()` - Verify instruction detection
- `testLoadAll()` - Verify content loading
- All tests pass successfully

Updated documentation:
- AGENTS.md - Added detection section with examples
- CONFIGURATION.md - Added [instructions] configuration section

## Search Order

Instructions are loaded in this priority order:

1. **Local AGENTS.md** - Found by traversing upward from current directory
   - Stops at git root or home directory
   - First match wins per file type

2. **Global AGENTS.md** - Checked in known locations
   - `~/.glm/AGENTS.md`
   - `~/.claude/CLAUDE.md`

3. **Custom Instructions** - From config.toml
   - Supports glob patterns
   - Supports absolute/relative paths
   - Supports `~/` home directory prefix

## File Detection Rules

### Local Files (Searched Upward)
- `AGENTS.md` - Primary instruction file
- `CLAUDE.md` - Claude AI compatibility
- `CONTEXT.md` - Deprecated but supported

### Global Files (If Exist)
- `~/.glm/AGENTS.md`
- `~/.claude/CLAUDE.md`

### Custom Patterns (From Config)
```toml
[instructions]
instructions = [
    "docs/*.md",                    # Glob pattern
    "~/custom-rules.md",             # Home directory
    "/path/to/rules.md",             # Absolute path
    "packages/*/AGENTS.md",          # Multi-package
    "STYLE_GUIDE.md"                 # Specific file
]
```

## Integration Points

### Agent Commands
- `glm agent "task"` - Loads AGENTS.md automatically
- Custom instructions added as system messages

### Chat Mode
- `glm chat` - Loads AGENTS.md on first message
- Instructions persist through session

### TUI Mode
- `glm` (default) - Loads AGENTS.md per input
- Supports multiple sessions with fresh context

### Init Command
- `glm init` - Creates or improves AGENTS.md
- Analyzes codebase structure
- Includes existing project rules

## Testing

Run test suite:
```bash
cd /home/kevintan/glm-cli-jbang
jbang tests/InstructionsTest.groovy
```

All tests pass:
- ✓ findUp test passed (found 1 matches)
- ✓ detect test passed (found 1 files)
- ✓ loadAll test passed (loaded 1 files)

## Compatibility

### Backward Compatibility
- Existing `prompts/` directory still works
- Agent system prompts unchanged
- No breaking changes to existing behavior

### opencode Compatibility
- Matches opencode detection pattern
- Supports same file types (AGENTS.md, CLAUDE.md)
- Similar glob pattern support
- Compatible configuration approach

## Benefits

1. **Automatic Context Loading** - Project-specific guidelines loaded automatically
2. **Flexible Configuration** - Support for multiple instruction sources
3. **No Manual Setup** - Detects existing AGENTS.md files
4. **Easy Initialization** - `glm init` creates optimized AGENTS.md
5. **Multi-Project Support** - Different rules for different projects
6. **Global Overrides** - Personal preferences via home directory
7. **Backward Compatible** - Works with existing system
8. **Well Documented** - Comprehensive configuration guide

## Usage Examples

### Basic Usage
```bash
# In a project with AGENTS.md
glm agent "Add error handling to AuthService"

# Automatically loads AGENTS.md from project root
```

### Custom Instructions
```toml
# ~/.glm/config.toml
[instructions]
instructions = [
    "docs/coding-standards.md",
    "~/personal-guidelines.md"
]
```

### Init Command
```bash
# Create AGENTS.md for current project
glm init

# Create in subdirectory
glm init --path /path/to/project
```

## Files Modified/Created

### New Files (6)
1. `core/Filesystem.groovy` - Filesystem utilities
2. `core/Instructions.groovy` - Instruction detection
3. `commands/InitCommand.groovy` - Init command
4. `tests/InstructionsTest.groovy` - Test suite

### Modified Files (6)
1. `core/Config.groovy` - Added instructions field
2. `core/Agent.groovy` - Load AGENTS.md in run()
3. `core/AgentConfig.groovy` - Update loadPrompt()
4. `tui/LanternaTUI.groovy` - Load instructions in processInput()
5. `commands/ChatCommand.groovy` - Load on first message
6. `commands/GlmCli.groovy` - Add InitCommand subcommand

### Documentation Updates (2)
1. `AGENTS.md` - Added detection section
2. `CONFIGURATION.md` - Added [instructions] section

## Success Criteria

All success criteria met:
- ✅ AGENTS.md detected in current directory and parents
- ✅ Global AGENTS.md loaded from `~/.glm/AGENTS.md`
- ✅ Custom instructions loaded from config.toml
- ✅ Instructions integrated into Agent and TUI prompts
- ✅ No breaking changes to existing functionality
- ✅ Comprehensive test coverage
- ✅ Documentation updated
- ✅ All tests passing

## Next Steps (Optional Enhancements)

Future improvements could include:
1. **Caching** - Cache detected instructions for performance
2. **Validation** - Validate AGENTS.md format
3. **Templates** - Provide AGENTS.md templates
4. **GUI Config** - Visual configuration editor
5. **Merge Strategy** - Smart merge of multiple instruction files
6. **Watch Mode** - Reload on file changes
7. **Priority System** - Weighted instruction merging

## Conclusion

Successfully implemented a complete AGENTS.md detection and loading system that matches opencode's architecture while being fully compatible with glm-cli-jbang's existing codebase. The system provides automatic context loading, flexible configuration, and backward compatibility with comprehensive documentation and testing.

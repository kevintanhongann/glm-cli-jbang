# Configuration Guide

This document provides comprehensive information about configuring GLM-CLI.

## Overview

GLM-CLI can be configured through:
1. **Config file**: `~/.glm/config.toml`
2. **Environment variables**: Override config settings
3. **Command-line flags**: Highest priority

Configuration is loaded in priority order: Flags > Environment > Config file > Defaults

## Configuration File Location

The config file is located at:

```bash
~/.glm/config.toml
```

Where `~` expands to your home directory:
- Linux/macOS: `/home/username/.glm/config.toml` or `/Users/username/.glm/config.toml`
- Windows: `C:\Users\username\.glm\config.toml`

## Configuration Structure

### Complete Example

```toml
[api]
key = "your.api.key.here"
base_url = "https://open.bigmodel.cn/api/paas/v4/"

[behavior]
default_model = "glm-4-flash"
safety_mode = "ask"
language = "auto"
max_steps = 10

[agent]
model = "glm-4"
temperature = 0.7

[logging]
level = "INFO"
file = "/path/to/agent.log"

[chat]
system_prompt = "You are a helpful coding assistant."
stream = true
```

## Configuration Sections

### [api] - API Configuration

| Key | Type | Default | Description |
|------|------|---------|-------------|
| `key` | string | `null` | Your GLM-4 API key (format: `id.secret`) |
| `base_url` | string | `https://open.bigmodel.cn/api/paas/v4/` | API endpoint URL |

#### Example

```toml
[api]
key = "1234567890.abcdefghijklmnopqrstuvwxyz"
base_url = "https://open.bigmodel.cn/api/paas/v4/"
```

#### Notes

- API key can also be set via `ZAI_API_KEY` environment variable
- Only use HTTPS endpoints (no HTTP for security)
- Don't include trailing slash in base_url (it's added automatically)

### [behavior] - Behavior Configuration

| Key | Type | Default | Description |
|------|------|---------|-------------|
| `default_model` | string | `"glm-4-flash"` | Default GLM-4 model to use |
| `safety_mode` | string | `"ask"` | Safety mode for file operations |
| `language` | string | `"auto"` | Language preference |
| `max_steps` | integer | `null` (unlimited) | Maximum agent iterations before tools are disabled |

#### Models

Available values for `default_model`:
- `glm-4-flash` - Fast, cost-effective
- `glm-4` - Balanced quality/speed
- `glm-4-plus` - Higher quality reasoning
- `glm-4.5` - Latest with multi-function calls
- `glm-4v` - Vision capabilities

#### Safety Modes

Available values for `safety_mode`:
- `ask` - Prompt user before destructive operations (default)
- `always_allow` - Execute without confirmation
- `strict` - Deny all writes (read-only mode)

#### Language

Available values for `language`:
- `auto` - Detect from content (default)
- `en` - English
- `zh` - Chinese

#### Max Steps

Controls the maximum number of agent iterations. When the limit is reached:
- Tools are disabled for the final response
- Agent is prompted to summarize work and provide recommendations
- Default is `null` (unlimited iterations)

Recommended values:
- `5-10` - Quick tasks, limited tool usage
- `20-30` - Complex multi-step tasks
- `null` - Unlimited (default, be aware of potential infinite loops)

#### Example

```toml
[behavior]
default_model = "glm-4"
safety_mode = "ask"
language = "en"
max_steps = 20
```

### [agent] - Agent Configuration

| Key | Type | Default | Description |
|------|------|---------|-------------|
| `model` | string | `"glm-4-flash"` | Model for agent operations |
| `temperature` | float | `0.7` | AI creativity level (0.0-2.0) |

#### Temperature Values

| Value | Description |
|--------|-------------|
| `0.0` - `0.3` | More deterministic, focused |
| `0.4` - `0.7` | Balanced (default) |
| `0.8` - `1.0` | More creative, varied |
| `1.0` - `2.0` | Very creative, less focused |

#### Example

```toml
[agent]
model = "glm-4"
temperature = 0.5
```

### [logging] - Logging Configuration

| Key | Type | Default | Description |
|------|------|---------|-------------|
| `level` | string | `"INFO"` | Log level (DEBUG, INFO, WARN, ERROR) |
| `file` | string | `null` | Log file path (null = stdout only) |

#### Log Levels

- `DEBUG` - Detailed diagnostic information
- `INFO` - General informational messages (default)
- `WARN` - Warning messages
- `ERROR` - Error messages only

#### Example

```toml
[logging]
level = "DEBUG"
file = "/home/user/.glm/agent.log"
```

### [chat] - Chat Configuration

| Key | Type | Default | Description |
|------|------|---------|-------------|
| `system_prompt` | string | `null` | Custom system prompt for chat |
| `stream` | boolean | `true` | Enable streaming responses |

#### System Prompt

Customize the chat assistant's behavior:

```toml
[chat]
system_prompt = "You are a senior software architect. Provide detailed, production-ready solutions."
```

#### Streaming

- `true` - Stream responses token-by-token (default)
- `false` - Wait for complete response

#### Example

```toml
[chat]
system_prompt = "You are a helpful Groovy expert."
stream = true
```

### [experimental] - Experimental Features Configuration

| Key | Type | Default | Description |
|------|------|---------|-------------|
| `continue_loop_on_deny` | boolean | `false` | Continue agent loop even when permission is denied (e.g., doom loop) |

#### Doom Loop Behavior

When `continue_loop_on_deny` is set to `false` (default):
- Agent stops execution when a doom loop is detected and user denies permission
- Useful for preventing infinite loops from running

When `continue_loop_on_deny` is set to `true`:
- Agent continues execution even when permission is denied
- May be useful for certain use cases but increases risk of infinite loops

#### Example

```toml
[experimental]
continue_loop_on_deny = false
```

### [web_search] - Web Search Configuration

| Key | Type | Default | Description |
|------|------|---------|-------------|
| `enabled` | boolean | `true` | Enable/disable web search tool |
| `default_count` | integer | `10` | Default number of results (1-50) |
| `default_recency_filter` | string | `"noLimit"` | Default time filter |

#### Recency Filters

| Value | Description |
|-------|-------------|
| `noLimit` | All time (default) |
| `1d` | Last 24 hours |
| `1w` | Last 7 days |
| `1m` | Last 30 days |
| `1y` | Last 365 days |

#### Example

```toml
[web_search]
enabled = true
default_count = 10
default_recency_filter = "noLimit"
```

## Environment Variables

Environment variables override config file settings:

| Variable | Config Key | Description |
|----------|-------------|-------------|
| `ZAI_API_KEY` | `api.key` | API key |
| `GLM_BASE_URL` | `api.base_url` | API endpoint |
| `GLM_DEFAULT_MODEL` | `behavior.default_model` | Default model |
| `GLM_SAFETY_MODE` | `behavior.safety_mode` | Safety mode |
| `GLM_LANGUAGE` | `behavior.language` | Language |
| `GLM_MAX_STEPS` | `behavior.max_steps` | Max agent steps |
| `GLM_AGENT_MODEL` | `agent.model` | Agent model |
| `GLM_TEMPERATURE` | `agent.temperature` | AI temperature |
| `GLM_LOG_LEVEL` | `logging.level` | Log level |
| `GLM_LOG_FILE` | `logging.file` | Log file path |
| `GLM_SYSTEM_PROMPT` | `chat.system_prompt` | System prompt |
| `GLM_WEB_SEARCH_ENABLED` | `web_search.enabled` | Enable web search (true/false) |
| `GLM_WEB_SEARCH_COUNT` | `web_search.default_count` | Default result count (1-50) |
| `GLM_WEB_SEARCH_RECENCY` | `web_search.default_recency_filter` | Default time filter |
| `GLM_CONTINUE_LOOP_ON_DENY` | `experimental.continue_loop_on_deny` | Continue loop on permission deny (true/false) |

### Setting Environment Variables

#### Linux/macOS (bash/zsh)

Temporary (current session):
```bash
export ZAI_API_KEY=your.api.key.here
export GLM_DEFAULT_MODEL=glm-4
export GLM_WEB_SEARCH_ENABLED=true
export GLM_WEB_SEARCH_COUNT=10
```

Permanent (add to `~/.bashrc` or `~/.zshrc`):
```bash
echo 'export ZAI_API_KEY=your.api.key.here' >> ~/.bashrc
source ~/.bashrc
```

#### Windows (PowerShell)

Temporary:
```powershell
$env:ZAI_API_KEY="your.api.key.here"
```

Permanent:
```powershell
[System.Environment]::SetEnvironmentVariable('ZAI_API_KEY', 'your.api.key.here', 'User')
```

#### Windows (cmd)

Temporary:
```cmd
set ZAI_API_KEY=your.api.key.here
```

## Command-Line Flags (Planned)

Future versions will support command-line flags with highest priority:

```bash
glm chat --model glm-4 --temperature 0.5
glm agent --safety-mode always_allow
```

## Configuration Priority

Configuration is loaded in this order (higher priority overrides lower):

1. **Command-line flags** (planned) - Highest priority
2. **Environment variables**
3. **Config file** (`~/.glm/config.toml`)
4. **Default values** - Lowest priority

### Example Priority Flow

```toml
# Config file
[behavior]
default_model = "glm-4-flash"
```

```bash
# Environment variable (overrides config)
export GLM_DEFAULT_MODEL=glm-4

# Result: Uses glm-4
```

## Creating Configuration File

### Automated Creation

```bash
# Create config directory
mkdir -p ~/.glm

# Create config file with defaults
cat > ~/.glm/config.toml << EOF
[api]
key = ""

[behavior]
default_model = "glm-4-flash"
safety_mode = "ask"

[agent]
temperature = 0.7
EOF

# Edit with your preferred editor
nano ~/.glm/config.toml
```

### Manual Creation

1. Create directory: `mkdir -p ~/.glm`
2. Create file: `~/.glm/config.toml`
3. Add your settings (see examples above)
4. Save file

## Validating Configuration

### Check Configuration

```bash
# Run help to verify config loads
glm --help

# Check loaded configuration (if verbose flag implemented)
glm --verbose --help
```

### Common Errors

#### Invalid TOML syntax

**Error**: `Failed to parse config file`

**Solution**: Use a TOML linter/validator:
- [toml-lint](https://github.com/pelletier/go-toml/tree/master/cmd/tomllint)
- Online validator: https://www.toml-lint.com/

#### Invalid API key format

**Error**: `Invalid API Key format`

**Solution**: Ensure format is `id.secret`:
- Get correct key from [bigmodel.cn](https://open.bigmodel.cn/)
- No extra spaces or characters
- Properly escaped if in quotes

#### File not found

**Error**: `Config file not found`

**Solution**: Create config file at correct location:
```bash
mkdir -p ~/.glm
touch ~/.glm/config.toml
```

## Configuration Examples

### Minimal Configuration

```toml
[api]
key = "your.api.key.here"
```

### Developer Configuration

```toml
[api]
key = "your.api.key.here"

[behavior]
default_model = "glm-4"
safety_mode = "ask"

[agent]
temperature = 0.5

[logging]
level = "DEBUG"
file = "/home/dev/.glm/debug.log"
```

### Production Configuration

```toml
[api]
key = "your.production.key.here"

[behavior]
default_model = "glm-4-plus"
safety_mode = "strict"

[agent]
temperature = 0.3

[logging]
level = "ERROR"
file = "/var/log/glm-cli/agent.log"
```

### Multi-Project Configuration

You can have project-specific configs (future feature):

```toml
# ~/.glm/config.toml (global)
[api]
key = "your.global.key"

[behavior]
default_model = "glm-4"

# /path/to/project/.glm/config.toml (project-local)
[behavior]
default_model = "glm-4-plus"
```

## Advanced Configuration

### Custom Endpoints

For development or custom API deployments:

```toml
[api]
key = "your.api.key.here"
base_url = "https://custom-endpoint.example.com/v4/"
```

### Proxy Configuration (Future)

```toml
[network]
proxy = "http://proxy.example.com:8080"
proxy_auth = "user:password"
```

### Session Persistence (Future)

```toml
[session]
persist = true
location = "/home/user/.glm/sessions/"
max_sessions = 10
```

### Custom Tool Paths (Future)

```toml
[tools]
paths = [
    "/home/user/.glm/tools/",
    "/project/tools/"
]
```

## Security Best Practices

### Protect Your API Key

**Do**:
- Store in environment variables or config file
- Use read-only permissions: `chmod 600 ~/.glm/config.toml`
- Rotate keys periodically
- Use separate keys for dev/prod

**Don't**:
- Commit config file to version control
- Share keys in public repositories
- Hard-code keys in scripts
- Log keys in output files

### File Permissions

Set appropriate permissions:

```bash
# Config file (read/write only for owner)
chmod 600 ~/.glm/config.toml

# Config directory (read/write/execute only for owner)
chmod 700 ~/.glm

# Log files
chmod 644 ~/.glm/*.log
```

### Version Control

**Add to `.gitignore`**:

```gitignore
# GLM-CLI configuration
.glm/
config.toml

# Logs
*.log
```

**Include template config**:

```bash
# Create example config (safe to commit)
cp ~/.glm/config.toml config.example.toml

# Remove sensitive data
# Edit config.example.toml to remove API key
```

## Troubleshooting

### Config Not Loading

**Symptoms**: Settings not applied

**Solutions**:
1. Verify file location: `ls -la ~/.glm/config.toml`
2. Check syntax with TOML validator
3. Check file permissions: `ls -la ~/.glm/config.toml`
4. Check for typos in key names

### Environment Variable Ignored

**Symptoms**: Environment variables not overriding config

**Solutions**:
1. Check variable name is correct (case-sensitive)
2. Verify variable is set: `echo $ZAI_API_KEY`
3. Restart terminal after setting variables
4. Check for trailing spaces in values

### API Key Issues

**Symptoms**: Authentication failures

**Solutions**:
1. Verify API key format: `id.secret`
2. Check key hasn't expired
3. Ensure key has required permissions
4. Test key with curl:
   ```bash
   curl -X POST https://open.bigmodel.cn/api/paas/v4/chat/completions \
     -H "Authorization: Bearer <token>" \
     -H "Content-Type: application/json" \
     -d '{"model":"glm-4-flash","messages":[{"role":"user","content":"test"}]}'
   ```

## References

- [README.md](./README.md#configuration) - Quick config guide
- [technicalSpec.md](./technicalSpec.md) - Configuration schemas
- [FAQ.md](./FAQ.md#troubleshooting) - Common issues
- [AGENTS.md](./AGENTS.md) - Agent configuration

## Related Files

- `core/Config.groovy` - Configuration loading logic
- `~/.glm/config.toml` - Your config file
- `config.example.toml` - Example config (optional)

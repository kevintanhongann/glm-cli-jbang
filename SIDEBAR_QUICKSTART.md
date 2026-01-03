# Quick Start Guide: Sidebar Testing

## Prerequisites

- Java 11+ installed
- Groovy installed (or using jbang)
- Terminal with at least 100 columns width for sidebar

## Launch TUI with Sidebar

```bash
cd /home/kevintan/glm-cli-jbang
./glm.groovy chat
```

## Expected Behavior

1. **Initial State**:
   - Main content panel on left
   - Sidebar panel on right (if terminal >= 100 columns)
   - Sidebar shows: Session info, Token stats, LSP status, Modified files

2. **Session Info Section**:
   - Session title
   - Working directory
   - Session ID (truncated)

3. **Token Section**:
   - Total tokens: "0 tokens"
   - Percentage: "0% used" (green/yellow/red based on usage)
   - Cost: "$0.0000 spent"

4. **LSP Section**:
   - "No LSP servers active" initially
   - Updates when LSP servers connect
   - Status indicators: `•` green (connected), red (error)

5. **Modified Files Section**:
   - "No modified files" initially
   - Lists files with +additions (green) and -deletions (red)

## Testing Commands

### Toggle Sidebar

```
/sidebar
```

Expected: Sidebar expands/collapses, shows/hides all sections

### Send a Message

```
Hello, can you help me write a function?
```

Expected:
1. Token section updates with new token count
2. Cost increases
3. Percentage updates

### Check Token Tracking

1. Send multiple messages
2. Watch token count increase
3. Verify percentage color changes:
   - Green: < 50% of context
   - Yellow: 50-80% of context
   - Red: > 80% of context

### Test Terminal Size Handling

1. Resize terminal to < 100 columns
2. Restart TUI
3. Expected: Sidebar is auto-hidden
4. Resize terminal to > 100 columns
5. Restart TUI
6. Expected: Sidebar appears

## Keyboard Shortcuts

| Shortcut | Action |
|----------|--------|
| `/sidebar` | Toggle sidebar visibility |
| `Ctrl+C` | Exit |
| `Ctrl+S` | Save log |
| `Tab/Shift+Tab` | Switch agent |

## Visual Verification Checklist

- [ ] Sidebar appears on right side (terminal >= 100 cols)
- [ ] Session info shows title and directory
- [ ] Token section displays count, %, and cost
- [ ] Token color changes based on usage
- [ ] LSP section shows server status with colored bullets
- [ ] Modified files show with + and - indicators
- [ ] `/sidebar` command toggles visibility
- [ ] Layout adapts to terminal size
- [ ] Colors are readable and consistent

## Troubleshooting

### Sidebar Not Visible

**Check**: Terminal width
```bash
# Check terminal size
echo $COLUMNS
```

**Solution**: Ensure terminal >= 100 columns

### Token Count Not Updating

**Check**: API response includes usage data
```bash
# Look for "usage" in API response
```

**Solution**: Verify API returns token usage information

### LSP Section Always Empty

**Check**: LSP initialization
```bash
# Look for LSP client initialization logs
```

**Solution**: LSP integration is in Phase 4 (not yet implemented)

### Sidebar Toggle Not Working

**Check**: Command parsing
```bash
# Try typing /sidebar in input
```

**Solution**: Ensure `/sidebar` command is properly registered

## Development Testing

### Test Data Updates

```groovy
// In a Groovy REPL or test script
import core.SessionStatsManager
import core.LspManager

// Create a session
def sessionId = "test-123"

// Update token count
SessionStatsManager.instance.updateTokenCount(sessionId, 100, 50, 0.0010)

// Update LSP status
LspManager.instance.updateLspStatus(sessionId, "typescript-server", "connected", null)

// Record modified file
SessionStatsManager.instance.recordModifiedFile(sessionId, "src/test.groovy", 10, 5)

// Check stats
def stats = SessionStatsManager.instance.getStats(sessionId)
println "Tokens: ${stats.totalTokens}, Cost: ${stats.totalCost}"
println "LSP servers: ${stats.lspServers.size()}"
println "Modified files: ${stats.modifiedFiles.size()}"
```

### Test Sidebar Refresh

```groovy
// In TUI, after message response:
refreshSidebar()  // Should update all sections
```

## Next Steps

See [SIDEBAR_IMPLEMENTATION.md](SIDEBAR_IMPLEMENTATION.md) for:

1. Phase 4: Enhanced LSP Integration
2. Phase 5: Visual Polish
3. Future Enhancements
4. Technical Notes
5. Architecture Details

## Support

If issues arise:
1. Check validation: `./validate-sidebar.sh`
2. Review imports and class dependencies
3. Verify Lanterna and Groovy versions
4. Check terminal compatibility

## Success Criteria

✅ Sidebar displays all sections correctly
✅ Token tracking updates on each message
✅ LSP section shows server status (when integrated)
✅ Modified files appear with diff stats
✅ Toggle command works
✅ Auto-hide on small terminals
✅ Color scheme is consistent
✅ Layout adapts to terminal size

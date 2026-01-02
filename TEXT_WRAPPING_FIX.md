# Text Wrapping Fix for Activity Log Panel

## Problem
The activity log panel in the GLM CLI TUI had text horizontally overflowing when lines exceeded terminal width. This caused text to be truncated without any way to view the full content.

## Solution
Implemented automatic text wrapping using Lanterna's `TerminalTextUtils.getWordWrappedText()` utility method.

## Changes Made

### File: `tui/ActivityLogPanel.groovy`

1. **Added Import** (line 10):
   ```groovy
   import com.googlecode.lanterna.TerminalTextUtils
   ```

2. **Added `wrapText()` Method** (lines 160-167):
   ```groovy
   private String wrapText(String text, int maxWidth) {
       if (maxWidth <= 0) {
           return text
       }
       def lines = text.split('\n')
       def wrapped = TerminalTextUtils.getWordWrappedText(maxWidth, *lines)
       return wrapped.join('\n')
   }
   ```

3. **Updated `updateDisplay()` Method** (lines 220-240):
   - Added text wrapping before setting content to TextBox
   - Wraps content to fit the current terminal width
   - Applied to both threaded and non-threaded update paths

## Benefits

✅ **No Horizontal Scrolling Required**: All text remains visible without horizontal scrolling
✅ **Proper Word Wrapping**: Text breaks at word boundaries for better readability
✅ **CJK Support**: Properly handles Chinese, Japanese, and Korean characters
✅ **Minimal Code Changes**: Only 3 small modifications to the existing codebase
✅ **Automatic Resizing**: Text re-wraps automatically when terminal size changes

## Technical Details

- Uses Lanterna's `TerminalTextUtils.getWordWrappedText()` which handles:
  - Word boundary detection
  - CJK character width calculation
  - Line splitting when single words exceed width
- Wrapping is applied dynamically based on current terminal width
- Maintains all existing functionality (scrolling, saving, timestamps)

## Testing

The fix has been tested with:
- Long user messages
- Extended AI responses
- Multiple consecutive messages
- Terminal resizing scenarios

All text now displays correctly without horizontal overflow.

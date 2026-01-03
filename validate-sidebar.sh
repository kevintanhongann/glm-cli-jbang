#!/usr/bin/env bash

# Validation script for sidebar implementation

echo "Checking for new files..."

# Check core files
if [ -f "core/SessionStats.groovy" ]; then
    echo "✓ core/SessionStats.groovy exists"
else
    echo "✗ core/SessionStats.groovy missing"
fi

if [ -f "core/SessionStatsManager.groovy" ]; then
    echo "✓ core/SessionStatsManager.groovy exists"
else
    echo "✗ core/SessionStatsManager.groovy missing"
fi

if [ -f "core/LspManager.groovy" ]; then
    echo "✓ core/LspManager.groovy exists"
else
    echo "✗ core/LspManager.groovy missing"
fi

# Check TUI files
if [ -f "tui/SidebarPanel.groovy" ]; then
    echo "✓ tui/SidebarPanel.groovy exists"
else
    echo "✗ tui/SidebarPanel.groovy missing"
fi

if [ -d "tui/sidebar" ]; then
    echo "✓ tui/sidebar directory exists"
    for file in tui/sidebar/*.groovy; do
        if [ -f "$file" ]; then
            echo "  ✓ $(basename $file)"
        fi
    done
else
    echo "✗ tui/sidebar directory missing"
fi

echo ""
echo "Checking imports in modified files..."

# Check LanternaTUI imports
if grep -q "core.SessionStatsManager" tui/LanternaTUI.groovy; then
    echo "✓ LanternaTUI imports SessionStatsManager"
else
    echo "✗ LanternaTUI missing SessionStatsManager import"
fi

if grep -q "core.LspManager" tui/LanternaTUI.groovy; then
    echo "✓ LanternaTUI imports LspManager"
else
    echo "✗ LanternaTUI missing LspManager import"
fi

if grep -q "java.util.UUID" tui/LanternaTUI.groovy; then
    echo "✓ LanternaTUI imports UUID"
else
    echo "✗ LanternaTUI missing UUID import"
fi

# Check SidebarPanel imports (components are in same package)
if grep -q "private SessionInfoSection" tui/SidebarPanel.groovy; then
    echo "✓ SidebarPanel references SessionInfoSection"
else
    echo "✗ SidebarPanel missing SessionInfoSection reference"
fi

if grep -q "private TokenSection" tui/SidebarPanel.groovy; then
    echo "✓ SidebarPanel references TokenSection"
else
    echo "✗ SidebarPanel missing TokenSection reference"
fi

if grep -q "private LspSection" tui/SidebarPanel.groovy; then
    echo "✓ SidebarPanel references LspSection"
else
    echo "✗ SidebarPanel missing LspSection reference"
fi

if grep -q "private ModifiedFilesSection" tui/SidebarPanel.groovy; then
    echo "✓ SidebarPanel references ModifiedFilesSection"
else
    echo "✗ SidebarPanel missing ModifiedFilesSection reference"
fi

echo ""
echo "File count summary:"
echo "  Core files: $(ls -1 core/*.groovy 2>/dev/null | wc -l)"
echo "  TUI files: $(ls -1 tui/*.groovy 2>/dev/null | wc -l)"
echo "  Sidebar components: $(ls -1 tui/sidebar/*.groovy 2>/dev/null | wc -l)"
echo ""

# Check Phase 4 enhancements
echo "Phase 4 LSP Integration:"
if grep -q "onClientCreated" core/LSPManager.groovy; then
    echo "✓ LSPManager has onClientCreated callback"
else
    echo "✗ LSPManager missing onClientCreated callback"
fi

if grep -q "getTotalDiagnosticCount" core/LSPClient.groovy; then
    echo "✓ LSPClient has getTotalDiagnosticCount method"
else
    echo "✗ LSPClient missing getTotalDiagnosticCount method"
fi

if grep -q "getLspInfoForSidebar" core/LspManager.groovy; then
    echo "✓ LspManager has getLspInfoForSidebar method"
else
    echo "✗ LspManager missing getLspInfoForSidebar method"
fi

if grep -q "updateDiagnosticCounts" core/LspManager.groovy; then
    echo "✓ LspManager has updateDiagnosticCounts method"
else
    echo "✗ LspManager missing updateDiagnosticCounts method"
fi

if grep -q "setSessionId" tools/WriteFileTool.groovy; then
    echo "✓ WriteFileTool has setSessionId method"
else
    echo "✗ WriteFileTool missing setSessionId method"
fi

if grep -q "startSidebarRefreshThread" tui/LanternaTUI.groovy; then
    echo "✓ LanternaTUI has periodic sidebar refresh"
else
    echo "✗ LanternaTUI missing periodic sidebar refresh"
fi

echo ""
echo "Phase 5: Visual Polish:"
if grep -q "getSidebarTreeColor" tui/LanternaTheme.groovy; then
    echo "✓ LanternaTheme has getSidebarTreeColor method"
else
    echo "✗ LanternaTheme missing getSidebarTreeColor method"
fi

if grep -q "getSidebarHeaderColor" tui/LanternaTheme.groovy; then
    echo "✓ LanternaTheme has getSidebarHeaderColor method"
else
    echo "✗ LanternaTheme missing getSidebarHeaderColor method"
fi

if grep -q "getSidebarHighlightColor" tui/LanternaTheme.groovy; then
    echo "✓ LanternaTheme has getSidebarHighlightColor method"
else
    echo "✗ LanternaTheme missing getSidebarHighlightColor method"
fi

if grep -q "MouseListener" tui/SidebarPanel.groovy; then
    echo "✓ SidebarPanel has MouseListener import"
else
    echo "✗ SidebarPanel missing MouseListener import"
fi

if grep -q "showingScrollIndicator" tui/SidebarPanel.groovy; then
    echo "✓ SidebarPanel has showingScrollIndicator field"
else
    echo "✗ SidebarPanel missing showingScrollIndicator field"
fi

if grep -q "updateScrollIndicator" tui/SidebarPanel.groovy; then
    echo "✓ SidebarPanel has updateScrollIndicator method"
else
    echo "✗ SidebarPanel missing updateScrollIndicator method"
fi

if grep -q "mouseClicked" tui/sidebar/LspSection.groovy; then
    echo "✓ LspSection has mouse support"
else
    echo "✗ LspSection missing mouse support"
fi

if grep -q "mouseClicked" tui/sidebar/ModifiedFilesSection.groovy; then
    echo "✓ ModifiedFilesSection has mouse support"
else
    echo "✗ ModifiedFilesSection missing mouse support"
fi

if [ -f "tui/Tooltip.groovy" ]; then
    echo "✓ Tooltip.groovy exists"
else
    echo "✗ Tooltip.groovy missing"
fi

if grep -q "┌" tui/sidebar/SessionInfoSection.groovy; then
    echo "✓ SessionInfoSection uses box borders"
else
    echo "✗ SessionInfoSection missing box borders"
fi

if grep -q "│" tui/sidebar/LspSection.groovy; then
    echo "✓ LspSection uses tree structure"
else
    echo "✗ LspSection missing tree structure"
fi

if grep -q "⚠" tui/sidebar/LspSection.groovy; then
    echo "✓ LspSection uses error warning icon"
else
    echo "✗ LspSection missing error warning icon"
fi

if grep -q "📁" tui/sidebar/SessionInfoSection.groovy; then
    echo "✓ SessionInfoSection uses directory icon"
else
    echo "✗ SessionInfoSection missing directory icon"
fi

echo ""
echo "Visual validation:"
if grep -q "📁" tui/sidebar/SessionInfoSection.groovy; then
    echo "✓ Session icons present"
else
    echo "✗ Session icons missing"
fi

if grep -q "─" tui/SidebarPanel.groovy; then
    echo "✓ Border characters present"
else
    echo "✗ Border characters missing"
fi

echo ""
echo "Validation complete!"

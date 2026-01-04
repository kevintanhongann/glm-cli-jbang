#!/bin/bash
# Quick test of TUI sidebar with timeout

echo "Starting TUI for 3 seconds to check sidebar visibility..."
echo "If sidebar appears on the right side, the fix is working!"
echo ""

timeout 3s ./glm.groovy --tui lanterna 2>&1 | grep -i debug || echo "No debug output"

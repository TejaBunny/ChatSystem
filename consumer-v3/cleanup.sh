#!/bin/bash
# Cleanup script - Move old local scripts to backup

echo "=================================="
echo "  Cleaning up old scripts"
echo "=================================="
echo ""

# Move old scripts to backup
echo "Moving old scripts to backup directory..."
mv monitor_all.sh old_scripts_backup/ 2>/dev/null && echo "✓ Moved monitor_all.sh"
mv monitor_mongodb.sh old_scripts_backup/ 2>/dev/null && echo "✓ Moved monitor_mongodb.sh"
mv monitor_rabbitmq.sh old_scripts_backup/ 2>/dev/null && echo "✓ Moved monitor_rabbitmq.sh"
mv monitoring_scripts.sh old_scripts_backup/ 2>/dev/null && echo "✓ Moved monitoring_scripts.sh"
mv run_batch_tests.sh old_scripts_backup/ 2>/dev/null && echo "✓ Moved run_batch_tests.sh"
mv run_load_tests.sh old_scripts_backup/ 2>/dev/null && echo "✓ Moved run_load_tests.sh"
mv status_check.sh old_scripts_backup/ 2>/dev/null && echo "✓ Moved status_check.sh"

echo ""
echo "Keeping:"
echo "  ✓ analyze_results.sh (for analyzing CSV metrics)"
echo ""

echo "=================================="
echo "  Cleanup complete!"
echo "=================================="
echo ""
echo "Backed up scripts are in: old_scripts_backup/"
echo "You can delete that directory later if you don't need them."
echo ""

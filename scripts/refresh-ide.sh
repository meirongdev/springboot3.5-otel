#!/usr/bin/env bash
set -euo pipefail

echo "🔄 Refreshing IDE dependencies..."

# Stop Gradle daemons
echo "Stopping Gradle daemons..."
./gradlew --stop 2>/dev/null || true

# Clean build
echo "Cleaning build artifacts..."
./gradlew clean --no-daemon

# Force dependency resolution
echo "Resolving all dependencies..."
./gradlew dependencies --refresh-dependencies --no-daemon > /dev/null 2>&1

# Compile to trigger dependency download
echo "Compiling all sources..."
./gradlew compileJava compileTestJava --no-daemon

echo ""
echo "✅ IDE refresh complete!"
echo ""
echo "Next steps in VS Code:"
echo "1. Press Cmd+Shift+P"
echo "2. Run 'Java: Clean Java Language Server Workspace'"
echo "3. Click 'Reload' when prompted"
echo ""
echo "This will clear the import errors for org.springframework.cloud"

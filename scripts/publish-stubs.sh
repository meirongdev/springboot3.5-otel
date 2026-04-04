#!/usr/bin/env bash
set -euo pipefail

echo "Publishing Spring Cloud Contract stubs to local Maven repository..."

# In Spring Cloud Contract, stubs are published to a Maven repository (local or remote)
# For CI/CD, you would typically publish to your organization's Maven repository
# This script demonstrates publishing to local Maven repository

# Build and publish stubs to local Maven
./gradlew :greeting-service:publishStubsToMavenLocal :user-service:publishStubsToMavenLocal

echo "Stubs published successfully to local Maven repository."
echo ""
echo "To use these stubs in hello-service tests:"
echo "  1. Ensure stubsMode = StubRunnerProperties.StubsMode.LOCAL is set"
echo "  2. The @AutoConfigureStubRunner annotation will find them automatically"
echo ""
echo "For CI/CD, configure a remote Maven repository for stub publishing:"
echo "  - Add maven-publish plugin to build.gradle.kts"
echo "  - Configure publishing { repositories { ... } }"
echo "  - Use 'gradle publish' to publish stubs to the remote repository"

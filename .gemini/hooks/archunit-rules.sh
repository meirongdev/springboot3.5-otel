#!/bin/bash
# .gemini/hooks/archunit-rules.sh
# Checks if ArchUnit tests have run and their status.

echo "### Architecture Integrity (ArchUnit) ###"
arch_test_reports=$(find . -name "TEST-com.example.ArchitectureRulesTest.xml" 2>/dev/null)

if [ -z "$arch_test_reports" ]; then
  echo "No ArchUnit test reports found. Run ./gradlew :arch-tests:test"
else
  failures=$(grep -c "<failure" $arch_test_reports | awk -F: '{sum+=$NF} END {print sum}')
  if [ "$failures" -gt 0 ]; then
    echo "❌ ARCHITECTURE VIOLATION: $failures failure(s) in ArchitectureRulesTest."
  else
    echo "✅ Architecture rules are passing."
  fi
fi

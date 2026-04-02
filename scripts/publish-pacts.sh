#!/usr/bin/env bash
set -euo pipefail

if [ -z "${PACT_BROKER_BASE_URL:-}" ] || [ -z "${PACT_BROKER_TOKEN:-}" ]; then
  echo "PACT_BROKER_BASE_URL and PACT_BROKER_TOKEN must be set as secrets"
  exit 1
fi

if [ ! -d build/pacts ]; then
  echo "No pacts found in build/pacts. Run ./gradlew test to generate pacts."
  exit 1
fi

SHA=$(git rev-parse --short HEAD || echo "local")

echo "Publishing pacts found in build/pacts as version: $SHA"

for f in build/pacts/*.json; do
  [ -f "$f" ] || continue
  provider=$(jq -r .provider.name "$f")
  consumer=$(jq -r .consumer.name "$f")
  echo "Publishing pact: $consumer -> $provider"
  # Publish to Pact Broker using Git SHA as version
  url="$PACT_BROKER_BASE_URL/pacts/provider/$provider/consumer/$consumer/version/$SHA"
  echo "PUT $url"
  curl -sS -X PUT \
    -H "Authorization: Bearer $PACT_BROKER_TOKEN" \
    -H "Content-Type: application/json" \
    --data-binary @"$f" \
    "$url"
done

echo "Publish complete"

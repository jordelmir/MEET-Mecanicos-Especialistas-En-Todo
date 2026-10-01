#!/usr/bin/env bash
set -euo pipefail
task_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
mkdir -p "$task_dir/target"
kotlinc "$task_dir/src/main/kotlin/io/elysium/safety/CustodyCanonicalV2.kt" \
  -jvm-target 17 -d "$task_dir/target/custody-0.1.0.jar"
jar tf "$task_dir/target/custody-0.1.0.jar" | grep -q 'io/elysium/safety/CustodyEventV2.class'
jar --create --file "$task_dir/target/custody-0.1.0-sources.jar" -C "$task_dir/src/main/kotlin" .
cp "$task_dir/pom.xml" "$task_dir/target/custody-0.1.0.pom"
shasum -a 256 "$task_dir/target/custody-0.1.0.jar" "$task_dir/target/custody-0.1.0-sources.jar" > "$task_dir/target/SHA256SUMS"
echo 'Reusable Kotlin custody JAR and sources JAR: PASS (local candidate; publication and license approval separate)'

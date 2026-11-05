#!/usr/bin/env bash
# Builds core and runs the matching benchmark. Optional arguments:
#   ./bench.sh [engineOrders] [exchangeOrders] [producerThreads]
set -euo pipefail
cd "$(dirname "$0")"
mvn -q -pl core -DskipTests compile
java -Xms512m -Xmx2g -cp core/target/classes com.dalalx.core.bench.Benchmark "$@"

#!/usr/bin/env bash
# Runs a command. If it fails, publishes the key error lines and the end of its output as workflow
# annotations, which (unlike raw logs) are readable on a public repo without signing in.
log="${RUNNER_TEMP:-/tmp}/step-$$.log"
"$@" 2>&1 | tee "$log"
rc=${PIPESTATUS[0]}
if [ "$rc" -ne 0 ]; then
  {
    grep -E "^e: |error:|What went wrong|FAILED|Exception|BUILD FAILED|Undefined symbol|cannot find|not found" "$log" | grep -v "^warning" | head -60
    echo "--- last lines:"
    tail -40 "$log"
  } > "$log.summary"
  split -l 25 "$log.summary" "$log.part."
  n=0
  for part in "$log".part.*; do
    n=$((n + 1)); [ "$n" -gt 9 ] && break
    msg=$(sed -e 's/%/%25/g' -e 's/\r/%0D/g' "$part" | awk '{printf "%s%%0A", $0}')
    echo "::error title=Step failed (part $n)::$msg"
  done
fi
exit "$rc"

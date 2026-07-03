#!/bin/sh
# Stable test entrypoint: fixed command strings for permission allowlisting.
# Usage: sh runtests.sh loudness | bendenv
cd "$(dirname "$0")" || exit 2
case "$1" in
  loudness) exec /Applications/SuperCollider.app/Contents/MacOS/sclang Testcode/tests/loudness_tests.scd ;;
  bendenv)  exec /Applications/SuperCollider.app/Contents/MacOS/sclang Testcode/tests/bendenv_align_tests.scd ;;
  *) echo "usage: sh runtests.sh loudness|bendenv"; exit 2 ;;
esac

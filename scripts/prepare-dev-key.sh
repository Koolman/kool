#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
mkdir -p tools
if [ ! -f tools/debug.keystore ]; then
  keytool -genkeypair -keystore tools/debug.keystore -storepass android -keypass android -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=Android Debug,O=Android,C=US'
fi

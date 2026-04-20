#!/bin/bash
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
JAVA_CMD="java"
if [ -n "$JAVA_HOME" ]; then
    JAVA_CMD="$JAVA_HOME/bin/java"
fi
if ! command -v "$JAVA_CMD" &> /dev/null; then
    echo "错误: 未找到 Java，请安装 JRE 1.8+ 或设置 JAVA_HOME"
    exit 1
fi
exec "$JAVA_CMD" -jar "$SCRIPT_DIR/jar-protect-cli.jar" gui "$@"
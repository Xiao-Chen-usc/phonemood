#!/bin/bash
cd "$(dirname "$0")"
if ! /bin/bash scripts/run-emulator.sh; then
    echo '启动未完成，按回车关闭此窗口。'
    read -r
fi

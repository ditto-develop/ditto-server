#!/bin/bash

read -p "원하는 sql 제목을 입력하세요: " TITLE

if [ -z "$TITLE" ]; then
    echo "제목을 입력해주세요."
    exit 1
fi

TIMESTAMP=$(date +"%Y%m%d%H%M%S")
FILENAME="V${TIMESTAMP}_${TITLE}.sql"
SCRIPT_DIR=$(dirname "$0")

touch "$SCRIPT_DIR/$FILENAME"
echo "Created: $FILENAME"

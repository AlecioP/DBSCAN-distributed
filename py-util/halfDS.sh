#!/bin/sh

echo $1

LINE_COUNT=$(cat $1 | sed '/^\s+$/d' | wc -l)

echo $LINE_COUNT

HALF=$(expr $LINE_COUNT / 2)

echo $HALF

NEW_FILE="$1-half"

head -$HALF $1 1>$NEW_FILE
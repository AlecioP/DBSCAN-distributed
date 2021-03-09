#!/bin/sh

if [ "$#" -ne 2 ]; then
    echo "Illegal number of parameters"
    exit
fi

echo "FILE : $1"
echo "DIV FACTOR : $2"

LINE_COUNT=$(cat $1 | sed '/^\s+$/d' | wc -l)

echo $LINE_COUNT

#Exit if one of these parameters doesn't make sense
HALF=$(expr $LINE_COUNT / $2) || exit

echo "DS div $2 is $HALF"

NEW_FILE="$1-div$2"

head -$HALF $1 1>$NEW_FILE
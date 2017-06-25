#!/bin/bash

MAIN_CLASS="cn.edu.tsinghua.ee.fi.cluster_curve.CurveApp"

echo "Running $MAIN_CLASS"

while true
do
    mvn exec:java -Dexec.mainClass=$MAIN_CLASS

    if [ $1 -ne 1 ]; then
        break
    fi

    sleep 10

    if [ $? -ne 0 ]; then
        break
    fi

done
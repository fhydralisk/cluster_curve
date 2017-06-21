#!/bin/bash

MAIN_CLASS="cn.edu.tsinghua.ee.fi.cluster_curve.CurveApp"

echo "Running $MAIN_CLASS"
mvn exec:java -Dexec.mainClass=$MAIN_CLASS


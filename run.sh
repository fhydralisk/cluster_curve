#!/bin/bash

MAIN_CLASS="com.nopqzip.App"

if [ -n $1 ]; then
  [ $1 = "watcher" ] && MAIN_CLASS="com.nopqzip.WatcherApp"
  [ $1 = "watchee" ] && MAIN_CLASS="com.nopqzip.WatcheeApp"
fi

echo "Running $MAIN_CLASS"
mvn exec:java -Dexec.mainClass=$MAIN_CLASS


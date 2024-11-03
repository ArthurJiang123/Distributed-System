#!/bin/bash

find . -type f -name "*.log" -exec rm -f {} +
find . -type f -name "*.lck" -exec rm -f {} +
find . -type f -name "*.csv" -exec rm -f {} +

pkill -u yhao19 java

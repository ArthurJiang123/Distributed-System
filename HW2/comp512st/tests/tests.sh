#!/bin/bash

numPlayers=$1
hostName=$2
if [[ $# -ne 2 ]]
then
    echo "Please pass the number of players as the argument"
    echo "Usage: $0 <numberOfPlayers> <hostName>"
    echo "Example: $0 2 tr-open-03"
    exit 2
fi
for ((process=1; process<=numPlayers; process++))
do
    log_file="game-31-99-${hostName}.cs.mcgill.ca.40${process}31-${process}-processinfo-.log"

    # Check if log file exists
    if [[ -f "$log_file" ]]; then
        echo "Processing log file: $log_file"
        grep "Move accepted in" "$log_file" | grep "Player: $process" | sed -E 's/.*Move accepted in ([0-9]+) ms.*/\1/'

    else
        echo "Log file $log_file not found."
    fi
done

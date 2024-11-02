#!/bin/bash

if [[ $# -lt 2 ]]
then
    echo "Usage: $0 <numberOfProcesses> <healthyProcess1> [<healthyProcess2> ... <healthyProcessN>]"
    exit 2
fi

numplayers=$1
shift

# Array of healthy players
healthy_players=("$@")

reference_player="${healthy_players[0]}"
reference_log="game-31-99-${reference_player}.log"

if [[ ! -f "$reference_log" ]]; then
    echo "Error: Reference log file '$reference_log' does not exist."
    exit 1
fi

for player in "${healthy_players[@]:1}"
do
    player_log="game-31-99-${player}.log"

    # Check if the player's log file exists
    if [[ ! -f "$player_log" ]]; then
        echo "Warning: Log file for player $player does not exist, skipping comparison."
        continue
    fi

    echo "Comparing logs of Player $reference_player and Player $player..."
    diff <(tail -n+2 "$reference_log") <(tail -n+2 "$player_log")

    if [[ $? -eq 0 ]]; then
        echo "No differences found between Player $reference_player and Player $player."
    else
        echo "Differences found between Player $reference_player and Player $player."
    fi
done


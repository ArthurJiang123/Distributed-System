#!/bin/bash

# TODO: Specify the hostnames of the 4 machines (e.g., tr-open-01, tr-open-02, etc.)
MACHINES=("tr-open-05" "tr-open-06" "tr-open-07" "tr-open-08")

# Paths for the server scripts
SERVER_PATH="$HOME/comp512/HW/A1/HW1Test/Server"  # Adjust the path to where your server scripts are
RUN_SERVER="./run_tcpserver.sh"
RUN_MIDDLEWARE="./run_tcpmiddleware.sh"

# Use tmux to split into panes for each machine and run the Resource Managers and Middleware
tmux new-session \; \
  split-window -h \; \
  split-window -v \; \
  split-window -v \; \
  select-layout main-vertical \; \
  select-pane -t 1 \; \
  send-keys "ssh -t ${MACHINES[0]} \"cd $SERVER_PATH > /dev/null; echo -n 'Connected to '; hostname; $RUN_SERVER Flights\"" C-m \; \
  select-pane -t 2 \; \
  send-keys "ssh -t ${MACHINES[1]} \"cd $SERVER_PATH > /dev/null; echo -n 'Connected to '; hostname; $RUN_SERVER Cars\"" C-m \; \
  select-pane -t 3 \; \
  send-keys "ssh -t ${MACHINES[2]} \"cd $SERVER_PATH > /dev/null; echo -n 'Connected to '; hostname; $RUN_SERVER Rooms\"" C-m \; \
  select-pane -t 0 \; \
  send-keys "ssh -t ${MACHINES[3]} \"cd $SERVER_PATH > /dev/null; echo -n 'Connected to '; hostname; sleep .5s; $RUN_MIDDLEWARE ${MACHINES[0]} ${MACHINES[1]} ${MACHINES[2]}\"" C-m \;

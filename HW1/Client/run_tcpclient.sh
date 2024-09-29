#!/bin/bash
# Usage: ./run_tcp_client.sh [<server_hostname> <server_port>]

# Check if the number of arguments is either 0 or 2
if [ "$#" -ne 0 ] && [ "$#" -ne 2 ]; then
  echo "Error: You must provide either 0 or exactly 2 arguments."
  echo "Usage: ./run_tcp_client.sh [<server_hostname> <server_port>]"
  exit 1
fi

# Default values if no arguments are provided
HOST=${1:-tr-open-08}
PORT=${2:-4031}

# Run the TCPClient
java -cp .:request-classes.jar:../Server/response-classes.jar Client.TCPClient.TCPClient $HOST $PORT
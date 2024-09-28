#!/bin/bash
# Usage: ./run_tcp_client.sh [<server_hostname> <server_port>]

# Check if exactly 2 arguments are provided
if [ "$#" -ne 2 ]; then
  echo "Error: You must provide exactly 2 arguments."
  echo "Usage: ./run_tcp_client.sh <server_hostname> <server_port>"
  exit 1
fi

# Default values if not provided
HOST=${1:-tr-open-08}
PORT=${2:-3031}

# Run the TCPClient
java -cp .:request-classes.jar:../Server/response-classes.jar Client.TCPClient.TCPClient $HOST $PORT
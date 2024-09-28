#!/bin/bash
# Usage: ./run_tcp_client.sh [<server_hostname> <server_port>]

# Default values if not provided
HOST=${1:-tr-open-08}
PORT=${2:-3031}

# Run the TCPClient
java -cp .:request-classes.jar:../Server/response-classes.jar Client.TCPClient.TCPClient $HOST $PORT
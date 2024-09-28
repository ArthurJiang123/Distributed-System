#!/bin/bash
# Usage: ./run_tcpserver.sh [<server_name>]

if [ $# -lt 1 ]; then
  echo "Usage: ./run_tcpserver.sh {Flights|Cars|Rooms}"
  exit 1
fi

# Run the TCP ResourceManager with the necessary JAR files
java -cp .:../Client/request-classes.jar:response-classes.jar:RMIInterface.jar Server.TCP.TCPResourceManager $1

#!/bin/bash
# Usage: ./run_tcpmiddleware.sh <FlightHost> <CarHost> <RoomHost>

if [ $# -lt 3 ]; then
    echo "Usage: ./run_tcpmiddleware.sh <FlightHost> <CarHost> <RoomHost>"
    exit 1
fi

# Run the middleware with all necessary JAR dependencies
java -cp .:../Client/request-classes.jar:response-classes.jar:RMIInterface.jar Server.TCP.TCPMiddleware $1 $2 $3
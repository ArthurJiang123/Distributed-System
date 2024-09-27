#echo "Edit file run_middleware.sh to include instructions for launching the middleware"
#echo '  $1 - hostname of Flights'
#echo '  $2 - hostname of Cars'
#echo '  $3 - hostname of Rooms'
echo "Usage: $0 <FlightHost> <CarHost> <RoomHost>"
echo "the default server name for middleware: Middleware"

if [ $# -lt 3 ]; then
    echo "Wrong. The correct Usage: $0 <FlightHost> <CarHost> <RoomHost>"
    echo "the default server name for middleware: Middleware"
    exit 1
fi

java Server.TCP.TCPMiddleware $1 $2 $3 > /dev/null 2>&1
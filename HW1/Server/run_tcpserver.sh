#Usage: ./run_tcpserver.sh [<server_name>]

if [ $# -lt 1 ]; then
  echo "Usage: ./run_server.sh {Flights|Cars|Rooms}"
  exit 1
fi

java Server.TCP.TCPResourceManager $1
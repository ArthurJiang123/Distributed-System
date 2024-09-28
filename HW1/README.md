# comp512 programming assignment 1
If you want to compile the code multiple times, first clean the class files by running:
```
cd Server
make clean
cd ../Client
make clean
```
To compile the code:
```
cd Client
make request-classes.jar
cd ../Server
make && make compile-server
cd ../Client
make client-core
```
## TCP
To run the TCP client(default host: tr-open-08)
(defalt port: 3031):

```
cd Client
./run_tcpclient.sh [<optional_host] [<optional_port>]
```
To run the TCP servers(tcp middleware run on tr-open-08, managers run on tr-open-05 to 07):
```
cd Server
./tcp_servers.sh
```
To run the TCP middleware alone:
```
./run_tcpmiddleware.sh <FlightHost> <CarHost> <RoomHost>
```
To run the TCP resource manager alone:
```
./run_tcpserver.sh [Flights|Cars|Rooms]
```
## RMI
To run the RMI resource manager:
```
cd Server/
./run_server.sh [<rmi_name>] # starts a single ResourceManager
./run_servers.sh # convenience script for starting multiple resource managers
```

To run the RMI client:
```
cd Client
./run_client.sh [<server_hostname> [<server_rmi_name>]]
```

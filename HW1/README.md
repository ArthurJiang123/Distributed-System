# COMP512 programming assignment 1
#### Jiahao Jiang, Yuanqing Hao
#### Group 31

## Compile the code
run make command in the
Client and Server directories, respectively:
```
cd Client
make
cd ../Server
make
```

## Running the code
To run client code, go to the Client directory then
execute associated scripts. To run server code, go to the Server directory
then execute associated scripts.
## TCP
To run the TCP client
(middleware host: tr-open-08)
(middleware port: 4031):

```
cd Client
./run_tcpclient.sh [<host>] [<port>]
```
To run the TCP servers(tcp middleware run on tr-open-08, managers run on tr-open-05 to 07):
```
cd Server
./run_tcpservers.sh
```
To run a single TCP middleware:
```
./run_tcpmiddleware.sh <FlightHost> <CarHost> <RoomHost>
```
To run a single TCP resource manager:
```
./run_tcpserver.sh [<serverName>]
```
## RMI
To run a RMI resource manager:
(default port: 3031)
```
./run_server.sh [<serverName>] # starts a single ResourceManager
```
To run a RMI middleware:
```
./run_middleware.sh <FlightHost> <CarHost> <RoomHost>
```
To run the middleware and the three resource managers:
```
./run_servers.sh
```
To run the RMI client:
```
./run_client.sh [<serverHostname>] [<serverName>]
```

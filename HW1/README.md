# COMP512 programming assignment 1
#### Jiahao Jiang, Yuanqing Hao
#### Group 31
If you want to compile the code multiple times, 
clean the class files before re-compiling:
```
cd Server
make clean
cd ../Client
make clean
```
To compile the code:
```
cd Client
make
cd ../Server
make
```
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
./tcp_servers.sh
```
To run the TCP middleware alone:
```
./run_tcpmiddleware.sh <FlightHost> <CarHost> <RoomHost>
```
To run the TCP resource manager alone:
```
./run_tcpserver.sh [<serverName>]
```
## RMI
To run the RMI resource manager:
(default port: 3031)
```
cd Server/
./run_server.sh [<serverName>] # starts a single ResourceManager
./run_servers.sh # starting multiple resource managers
```
To run the RMI client:
```
cd Client
./run_client.sh [<serverHostname>] [<serverName>]
```

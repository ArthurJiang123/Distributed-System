# Target: Dependencies
#    [tab] Command to execute the target
all: compile-client

# goes to the server directory and get the target
../Server/RMIInterface.jar: ../Server/Server/Interface/IResourceManager.java
	make -C ../Server/ RMIInterface.jar

# compile client code, with classpath defined by ../Server/RMIInterface.jar and Client/*.java
# since our client side will use a class in the server, so classes in Server/Common directory are needed
compile-client: ../Server/RMIInterface.jar
	javac -cp ../Server/RMIInterface.jar ../Server/Server/Common/*.java Client/*.java Client/TCPClient/*.java

clean:
	rm -f Client/*.class
	rm -f ../Server/RMIInterface.jar

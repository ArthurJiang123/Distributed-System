# compiled jar file paths and names
RESPONSE_CLASSES_JAR = response-classes.jar
RMI_INTERFACE_JAR = RMIInterface.jar
REQUEST_CLASSES_JAR = ../Client/request-classes.jar
SERVER_JAR = server.jar

# Java source files for the server
RESPONSE_SRC = Server/Common/ResponsePacket.java
INTERFACE_SRC = Server/Interface/IResourceManager.java
COMMON_SRC = Server/Common/*.java
RMI_SRC = Server/RMI/*.java
TCP_SRC = Server/TCP/*.java

# Default target: build all
all: $(RESPONSE_CLASSES_JAR) $(RMI_INTERFACE_JAR) $(SERVER_JAR)

# Compile `ResponsePacket.java`
# and package it into `response-classes.jar`
$(RESPONSE_CLASSES_JAR): $(RESPONSE_SRC)
	@echo "Compiling and packaging ResponsePacket class into response-classes.jar"
	javac -d . $(RESPONSE_SRC)
	jar cvf $(RESPONSE_CLASSES_JAR) Server/Common/ResponsePacket.class

# build `request-classes.jar`
# by calling the client's Makefile
$(REQUEST_CLASSES_JAR):
	@echo "Compiling request-classes.jar by calling Client's Makefile..."
	make -C ../Client request-classes.jar

# Compile `IResourceManager.java`
# and package it into `RMIInterface.jar`
$(RMI_INTERFACE_JAR): $(INTERFACE_SRC)
	@echo "Compiling and packaging IResourceManager interface into RMIInterface.jar"
	javac -d . $(INTERFACE_SRC)
	jar cvf $(RMI_INTERFACE_JAR) Server/Interface/IResourceManager.class

# Compile the entire server using `request-classes.jar`
# (from the client) as a dependency
# and package into server.jar
$(SERVER_JAR): $(REQUEST_CLASSES_JAR) $(COMMON_SRC) $(INTERFACE_SRC) $(RMI_SRC) $(TCP_SRC)
	@echo "Compiling all Common, Interface, RMI, and TCP classes for server functionality"
	javac -cp .:$(REQUEST_CLASSES_JAR) $(COMMON_SRC) $(INTERFACE_SRC) $(RMI_SRC) $(TCP_SRC)
	@echo "Packaging the full server classes into server.jar"
	jar cvf $(SERVER_JAR) Server/Common/*.class Server/Interface/*.class Server/RMI/*.class Server/TCP/*.class

clean:
	@echo "Cleaning up server files..."
	rm -f Server/Common/*.class Server/Interface/*.class Server/RMI/*.class Server/TCP/*.class
	rm -f $(RESPONSE_CLASSES_JAR) $(RMI_INTERFACE_JAR) $(SERVER_JAR)

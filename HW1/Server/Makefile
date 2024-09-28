# Paths and file names
RESPONSE_CLASSES_JAR = response-classes.jar
RMI_INTERFACE_JAR = RMIInterface.jar
REQUEST_CLASSES_JAR = ../Client/request-classes.jar

# Java source files in Server
RESPONSE_SRC = Server/Common/ResponsePacket.java
INTERFACE_SRC = Server/Interface/IResourceManager.java
COMMON_SRC = Server/Common/*.java
RMI_SRC = Server/RMI/*.java
TCP_SRC = Server/TCP/*.java

# Default target: build the `response-classes.jar`
all: $(RESPONSE_CLASSES_JAR) $(RMI_INTERFACE_JAR)

# Compile only `ResponsePacket.java` and package it into `response-classes.jar`
$(RESPONSE_CLASSES_JAR): $(RESPONSE_SRC)
	@echo "Compiling and packaging ResponsePacket class into response-classes.jar"
	javac -d . $(RESPONSE_SRC)
	jar cvf $(RESPONSE_CLASSES_JAR) Server/Common/ResponsePacket.class

$(RMI_INTERFACE_JAR): $(INTERFACE_SRC)
	@echo "Compiling and packaging IResourceManager interface into RMIInterface.jar"
	javac -d . $(INTERFACE_SRC)
	jar cvf $(RMI_INTERFACE_JAR) Server/Interface/IResourceManager.class

compile-server: $(REQUEST_CLASSES_JAR) $(COMMON_SRC) $(INTERFACE_SRC) $(RMI_SRC) $(TCP_SRC)
	@echo "Compiling all Common, Interface, RMI, and TCP classes for server functionality"
	javac -cp .:$(REQUEST_CLASSES_JAR) $(COMMON_SRC) $(INTERFACE_SRC) $(RMI_SRC) $(TCP_SRC)
# Clean up compiled files and JARs
clean:
	@echo "Cleaning up server files..."
	rm -f Server/Common/*.class Server/Interface/*.class Server/RMI/*.class Server/TCP/*.class
	rm -f $(RESPONSE_CLASSES_JAR)
	rm -f $(RMI_INTERFACE_JAR)

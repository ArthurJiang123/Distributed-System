# Paths and file names
REQUEST_CLASSES_JAR = request-classes.jar
RESPONSE_CLASSES_JAR = ../Server/response-classes.jar
RMI_INTERFACE_JAR = ../Server/RMIInterface.jar
FULL_CLIENT_JAR = client-core.jar

# Java source files for minimal request-classes.jar
REQUEST_SRC = Client/Command.java Client/TCPClient/Request.java

# All client-side Java files
CLIENT_SRC = $(wildcard Client/*.java Client/TCPClient/*.java)

# Full client build using all necessary classes and JAR dependencies
all: client-core

# Target to build just `request-classes.jar`
#request-classes: $(REQUEST_CLASSES_JAR)

# Compile the Command and Request classes into request-classes.jar
$(REQUEST_CLASSES_JAR): $(REQUEST_SRC)
	@echo "Compiling Command and Request classes into request-classes.jar"
	javac -d . $(REQUEST_SRC)
	jar cvf $(REQUEST_CLASSES_JAR) Client/Command.class Client/TCPClient/Request.class

# Generate `response-classes.jar` by calling the server's makefile
$(RESPONSE_CLASSES_JAR):
	@echo "Compiling response-classes.jar by calling Server's Makefile..."
	make -C ../Server response-classes.jar

# Generate `RMIInterface.jar` by calling the server's makefile
$(RMI_INTERFACE_JAR):
	@echo "Compiling RMIInterface.jar by calling Server's Makefile..."
	make -C ../Server RMIInterface.jar

# Compile the full client using the request-classes.jar and response-classes.jar
client-core: $(REQUEST_CLASSES_JAR) $(RESPONSE_CLASSES_JAR) $(RMI_INTERFACE_JAR) $(CLIENT_SRC)
	@echo "Compiling the full client classes with server dependencies"
	javac -cp $(REQUEST_CLASSES_JAR):$(RESPONSE_CLASSES_JAR):$(RMI_INTERFACE_JAR) -d . $(CLIENT_SRC)
	jar cvf $(FULL_CLIENT_JAR) Client/*.class Client/TCPClient/*.class

# Clean up compiled files and JARs
clean:
	@echo "Cleaning up client files..."
	rm -f Client/*.class Client/TCPClient/*.class $(REQUEST_CLASSES_JAR) $(FULL_CLIENT_JAR)

# Rebuild everything
rebuild: clean all
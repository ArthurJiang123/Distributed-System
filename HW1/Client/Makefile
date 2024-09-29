# Paths and file names
REQUEST_CLASSES_JAR = request-classes.jar
RESPONSE_CLASSES_JAR = ../Server/response-classes.jar
RMI_INTERFACE_JAR = ../Server/RMIInterface.jar

# Java source files for minimal request-classes.jar
REQUEST_SRC = Client/Command.java Client/TCPClient/Request.java

# All client-side Java files
CLIENT_SRC = Client/*.java Client/TCPClient/*.java

# Target to build just `request-classes.jar`
request-classes: $(REQUEST_CLASSES_JAR)

# Compile the Command and Request classes into request-classes.jar
$(REQUEST_CLASSES_JAR): $(REQUEST_SRC)
	@echo "Compiling Command and Request classes into request-classes.jar"
	javac -d . $(REQUEST_SRC)
	jar cvf $(REQUEST_CLASSES_JAR) Client/Command.class Client/TCPClient/Request.class

# Full client build using all necessary classes and JAR dependencies
# Ensures that request-classes.jar is up-to-date by running the steps to build it if necessary.
all: $(REQUEST_CLASSES_JAR)

# Compile the full client using the request-classes.jar and response-classes.jar
client-core: $(REQUEST_CLASSES_JAR) $(RESPONSE_CLASSES_JAR) $(RMI_INTERFACE_JAR) $(CLIENT_SRC)
	@echo "Compiling the full client classes with server dependencies"
	# Compile the full client with all dependencies in the classpath
	javac -cp $(REQUEST_CLASSES_JAR):$(RESPONSE_CLASSES_JAR):$(RMI_INTERFACE_JAR) $(filter-out $(REQUEST_SRC),$(CLIENT_SRC))
	jar cvf $(FULL_CLIENT_JAR) Client/*.class Client/TCPClient/*.class

# Clean up compiled files and JARs
clean:
	@echo "Cleaning up client files..."
	rm -f Client/*.class Client/TCPClient/*.class $(REQUEST_CLASSES_JAR) client-core
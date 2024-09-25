package Client.TCPClient;

import java.io.Serializable;
import java.util.Vector;
import Client.Command;

public class Request implements Serializable {
    private Command command;
    private Vector<String> arguments;

    public Request(Command command, Vector<String> arguments) {
        this.command = command;
        this.arguments = arguments;
    }

    public Command getCommand() {
        return command;
    }

    public Vector<String> getArguments() {
        return arguments;
    }
}

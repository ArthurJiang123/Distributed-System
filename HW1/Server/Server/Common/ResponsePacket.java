package Server.Common;

import java.io.Serializable;

public class ResponsePacket implements Serializable {
    private Boolean status;
    private String message;

    public ResponsePacket(Boolean status, String message) {
        this.status = status;
        this.message = message;
    }

    public ResponsePacket(){}

    public Boolean getStatus() {
        return status;
    }

    public void setStatus(Boolean status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }
    public void setMessage(String message) {
        this.message = message;
    }

}

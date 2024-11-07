package comp512st.paxos;

import java.io.Serializable;

class PaxosMessage implements Serializable {
    private static final long serialVersionUID = 123456789L;

    public enum MessageType{
        PROPOSE, // proposal message from proposer, with a specific slot number
        PROMISE, // Acceptor's promise response -> will not accept lower ballot ids
        ACCEPT, // Proposer requesting acceptors (for a value)
        ACCEPT_ACK, // Acceptor's response -> accepted the value
        CONFIRM, // Proposer's final commit message to confirm the value
        REJECT_PROPOSE, // Reject the proposal
        REJECT_ACCEPT
    }

    private final MessageType type;
    private final String proposer;
    private final BallotID ballotID;
    private final BallotID acceptedID; // Highest accepted ballotID for this slot
    private final Object value;
    public PaxosMessage(MessageType type, BallotID ballotID, String proposer, Object value) {
        this(type, ballotID, proposer, value, null);
    }
    public PaxosMessage(MessageType type, BallotID ballotID, String proposer, Object value, BallotID acceptedBallotID) {
        this.type = type;
        this.ballotID = ballotID;
        this.value = value;
        this.proposer = proposer;
        this.acceptedID = acceptedBallotID;
    }

    public MessageType getType() {
        return type;
    }

    public BallotID getBallotID() {
        return ballotID;
    }


    public Object getValue() {
        return value;
    }

    public String getProposer() {
        return proposer;
    }

    public BallotID getAcceptedID() {
        return acceptedID;
    }

    @Override
    public String toString() {
        return "PaxosMessage{" +
                "type=" + type +
                ", ballotID=" + ballotID +
                ", value=" + value +
                ", proposer='" + proposer + '\'' +
                ", acceptedBallotID=" + acceptedID +
                '}';
    }
}

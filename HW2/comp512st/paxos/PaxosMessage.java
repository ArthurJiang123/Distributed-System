package comp512st.paxos;

import java.io.Serializable;

class PaxosMessage implements Serializable {
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
    private final BallotID acceptedBallotID; // Highest accepted ballotID for this slot
    private final Object value;
    private final int round;

    public PaxosMessage(MessageType type, BallotID ballotID, String proposer, Object value, int round) {
        this(type, ballotID, proposer, value, null, round);
    }
    public PaxosMessage(MessageType type, BallotID ballotID, String proposer, Object value, BallotID acceptedBallotID, int round) {
        this.type = type;
        this.ballotID = ballotID;
        this.value = value;
        this.proposer = proposer;
        this.acceptedBallotID = acceptedBallotID;
        this.round = round;
    }

    public int getRound() {
        return round;
    }

    public MessageType getType() {
        return type;
    }

    public BallotID getBallotID() {
        return ballotID;
    }

//    public int getSlot() {
//        return slot;
//    }

    public Object getValue() {
        return value;
    }

    public String getProposer() {
        return proposer;
    }

    public BallotID getAcceptedBallotID() {
        return acceptedBallotID;
    }

    @Override
    public String toString() {
        return "PaxosMessage{" +
                "type=" + type +
                ", ballotID=" + ballotID +
                ", value=" + value +
                ", proposer='" + proposer + '\'' +
                ", acceptedBallotID=" + acceptedBallotID +
                '}';
    }
}

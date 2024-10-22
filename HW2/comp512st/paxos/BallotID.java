package comp512st.paxos;

import java.io.Serializable;

public class BallotID implements Serializable, Comparable<BallotID>{

    private static final long serialVersionUID = 1L;  // Optional but recommended for version control

    private final int value;

    private final String proposer;
    public int getValue() {
        return value;
    }

    public String getProposer() {
        return proposer;
    }

    public BallotID(int sequence, String proposer){
        this.value = sequence;
        this.proposer = proposer;
    }

    @Override
    public int compareTo(BallotID other){
        if(this.value != other.value){
            return Integer.compare(this.value, other.value);
        }

        return this.proposer.compareTo(other.proposer);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        BallotID other = (BallotID) obj;
        return value == other.value && proposer.equals(other.proposer);
    }

    @Override
    public int hashCode() {
        return 31 * value + proposer.hashCode();
    }

    @Override
    public String toString(){
        return String.format("(%d, %s)", value, proposer);
    }
}

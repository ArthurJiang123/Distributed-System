package comp512st.paxos;

import java.io.Serializable;

public class BallotID implements Serializable, Comparable<BallotID>{

    private static final long serialVersionUID = 1L;  // Optional but recommended for version control
    private final int sequence;
    private final String proposer;

    public BallotID(int sequence, String proposer){
        this.sequence = sequence;
        this.proposer = proposer;
    }

    @Override
    public int compareTo(BallotID other){
        if(this.sequence != other.sequence){
            return Integer.compare(this.sequence, other.sequence);
        }

        return this.proposer.compareTo(other.proposer);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        BallotID other = (BallotID) obj;
        return sequence == other.sequence && proposer.equals(other.proposer);
    }

    @Override
    public int hashCode() {
        return 31 * sequence + proposer.hashCode();
    }

    @Override
    public String toString(){
        return String.format("(%d, %s)", sequence, proposer);
    }
}

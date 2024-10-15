package comp512st.paxos;

// Access to the GCL layer
import comp512.gcl.*;

import comp512.utils.*;

// Any other imports that you may need.
import java.io.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.*;
import java.net.UnknownHostException;


// ANY OTHER classes, etc., that you add must be private to this package and not visible to the application layer.

// extend / implement whatever interface, etc. as required.
// NO OTHER public members / methods allowed. broadcastTOMsg, acceptTOMsg, and shutdownPaxos must be the only visible methods to the application layer.
//		You should also not change the signature of these methods (arguments and return value) other aspects maybe changed with reasonable design needs.
public class Paxos
{
	GCL gcl;
	FailCheck failCheck;
	String myProcess;
	String[] allGroupProcesses;
	Logger logger;


	private int proposalSlot = 0;  // Local sequence for proposals
	private int currentSlot = 0;  // Track the current sequence number

	private final Map<Integer, Object> acceptedValues = new HashMap<>();
	private final BlockingQueue<PaxosMessage> messageQueue = new LinkedBlockingQueue<>();

	private final BlockingQueue<Object> proposalQueue = new LinkedBlockingQueue<>();


	public Paxos(String myProcess, String[] allGroupProcesses, Logger logger, FailCheck failCheck) throws IOException, UnknownHostException
	{
		// Rember to call the failCheck.checkFailure(..) with appropriate arguments throughout your Paxos code to force fail points if necessary.
		this.failCheck = failCheck;

		// Initialize the GCL communication system as well as anything else you need to.
		this.gcl = new GCL(myProcess, allGroupProcesses, null, logger) ;

		this.myProcess = myProcess;
		this.allGroupProcesses = allGroupProcesses;
		this.logger = logger;
	}

	// This is what the application layer is going to call to send a message/value, such as the player and the move
	/**
	 * Step 1: propose (proposer)
	 * @param val
	 */
	public void broadcastTOMsg(Object val)
	{
		// TODO:Extend this to build whatever Paxos logic you need to make sure the messaging system is total order.
		// TODO:Here you will have to ensure that the CALL BLOCKS, and is returned ONLY when a majority (and immediately upon majority) of processes have accepted the value.

		boolean accepted = false;

		try{
			proposalQueue.put(val);

			while(!proposalQueue.isEmpty()){

				Object proposalValue = proposalQueue.peek();

				proposalSlot++;

				BallotID ballotID = new BallotID(proposalSlot, myProcess);

				int slot = currentSlot;  // Propose for the current slot

				PaxosMessage proposal = new PaxosMessage(
						PaxosMessage.MessageType.PROPOSE, ballotID, slot, val
				);

				logger.info("Broadcasting proposal: " + proposal);

				// Simulate failure before broadcasting
				failCheck.checkFailure(FailCheck.FailureType.AFTERSENDPROPOSE);

				gcl.broadcastMsg(proposal);

				accepted = waitMajorPromises(ballotID, slot, val);

				// if installed successfully, remove the proposal
				// else, try again
				if (accepted) {
					proposalQueue.poll();
				} else {
					logger.warning("Retrying proposal: " + proposalValue);
				}

			}
		}catch(InterruptedException e) {
			logger.severe("Failed to queue proposal: " + e.getMessage());
			Thread.currentThread().interrupt();
		}
	}
	/**
	 * STEP 2: Wait for Promises
	 * should receive either:
	 * (1) promise of the current ballot id
	 * (2) promise of a higher ballot id
	 *
	 * If there are only (1) -> send Accept Request for our own proposed value
	 * If there are only (2), or (1) and (2) ->
	 * acceptors have accepted our ballot id, but some have accepted values of others.
	 * We can only send Accept Request (with our own ballot ID, the accepted value with
	 * the highest accepted ballot id...)
	 * @param ballotID
	 * @param slot
	 * @param value
	 * @return
	 */
	private boolean waitMajorPromises(BallotID ballotID, int slot, Object value){
		Set<String> promises = new HashSet<>();

		try{
			while(promises.size() <= allGroupProcesses.length/2 ){

				PaxosMessage response = (PaxosMessage) gcl.readGCMessage().val;

				// if we get a promise message, and the promise is made on the current ballot ID
				// then count it toward the majority
				if(response.getType() == PaxosMessage.MessageType.PROMISE){

					if(response.getBallotID().compareTo(ballotID) > 0){
						logger.warning("Higher ballotID detected. Giving up current proposal.");
						return false;
					}

					promises.add(response.getProposer());
				}

				// Either final value is the one we proposed, or the highest accepted value
				if(promises.size() > allGroupProcesses.length/2){

					logger.info("Majority promises received for ballotID: " + ballotID);

					failCheck.checkFailure(FailCheck.FailureType.AFTERBECOMINGLEADER);

//					Object finalValue = (maxAcceptVal != null) ? maxAcceptVal : value;

					sendAcceptRequest(ballotID, slot, value);

					return true;
				}
			}
		} catch (InterruptedException e) {
			logger.severe("Interrupted while waiting for majority acceptance.");
			Thread.currentThread().interrupt();  // Restore interrupt status
		}

		logger.warning("Failed to achieve majority for ballotID: " + ballotID + ". Retrying...");
		return false;
	}

	/**
	 * STEP 2.1: Send Accept Request (Now Proposer -> Leader)
	 * broadcast all messages to acceptors
	 * It will ask acceptors if they accept the proposed value
	 * @param ballotID
	 * @param val
	 */
	private void sendAcceptRequest(BallotID ballotID, int slot, Object val){
		PaxosMessage acceptRequest = new PaxosMessage(
				PaxosMessage.MessageType.ACCEPT, ballotID, slot, val
		);

		logger.info("Sending accept request: " + acceptRequest);

		gcl.broadcastMsg(acceptRequest);

		waitAcceptAcks(ballotID, slot);
	}

	/**
	 * STEP 3: Wait until a majority of acceptances (proposer)
	 * Receive either 2 of the possible responses:
	 * (1) accept ack -> the value is accepted by the acceptor
	 * (2) refuse -> a higher ballot id appears and get the promise from that acceptor
	 * If there are a majority of acks -> value is not
	 * @param ballotID
	 * @return
	 */
	private boolean waitAcceptAcks(BallotID ballotID, int slot){
		Set<String> acceptAcks = new HashSet<>();
		try{
			while(acceptAcks.size() <= allGroupProcesses.length / 2){

				PaxosMessage response = (PaxosMessage) gcl.readGCMessage().val;

				if(response.getType() == PaxosMessage.MessageType.ACCEPT_ACK &&
						response.getBallotID().compareTo(ballotID) == 0
				){
					acceptAcks.add(response.getProposer());
				}


				if (acceptAcks.size() > allGroupProcesses.length / 2) {

					logger.info("Majority AcceptAck received for ballotID: " + ballotID);

					failCheck.checkFailure(FailCheck.FailureType.AFTERVALUEACCEPT);

					sendConfirm(ballotID, slot);

					return true;
				}

			}
		} catch (InterruptedException e) {
			logger.severe("Interrupted while waiting for AcceptAck.");
			Thread.currentThread().interrupt();
		}

		logger.warning("Failed to achieve majority of AcceptAck for ballotID: " + ballotID + ". Retrying...");
		return false;
	}

	/**
	 * STEP 4: Send Confirm Message once the majority of acceptors accepted value (Proposer)
	 * @param ballotID
	 */
	private void sendConfirm(BallotID ballotID, int slot) {
		PaxosMessage confirmMessage = new PaxosMessage(
				PaxosMessage.MessageType.CONFIRM, ballotID, slot, null
		);

		logger.info("Sending confirm message: " + confirmMessage);
		gcl.broadcastMsg(confirmMessage);
	}

	// TODO: figure out what is the next message in the total order.
	// NOTE: Messages delivered in ALL the processes in the group should deliver this in the same order.

	public Object acceptTOMsg() throws InterruptedException
	{
		// This is just a place holder.
		GCMessage gcmsg = gcl.readGCMessage();

		failCheck.checkFailure(FailCheck.FailureType.AFTERVALUEACCEPT);

		return gcmsg.val;
	}


	//TODO:
	// Add any of your own shutdown code into this method.
	public void shutdownPaxos()
	{
		gcl.shutdownGCL();
	}


}


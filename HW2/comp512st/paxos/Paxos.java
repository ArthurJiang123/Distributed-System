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

	private BallotID maxBallotID;
	private Object value;

	public Paxos(String myProcess, String[] allGroupProcesses, Logger logger, FailCheck failCheck) throws IOException, UnknownHostException
	{
		// Rember to call the failCheck.checkFailure(..) with appropriate arguments throughout your Paxos code to force fail points if necessary.
		this.failCheck = failCheck;

		// Initialize the GCL communication system as well as anything else you need to.
		this.gcl = new GCL(myProcess, allGroupProcesses, null, logger) ;

		this.myProcess = myProcess;
		this.allGroupProcesses = allGroupProcesses;
		this.logger = logger;

		// Acceptor field

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

//				int slot = currentSlot;  // Propose for the current slot

				PaxosMessage proposal = new PaxosMessage(
						PaxosMessage.MessageType.PROPOSE, ballotID, val
				);

				logger.info("Broadcasting proposal: " + proposal);

				// Simulate failure before broadcasting
				failCheck.checkFailure(FailCheck.FailureType.AFTERSENDPROPOSE);

				gcl.broadcastMsg(proposal);

				accepted = waitMajorPromises(ballotID, val);

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
	 * @param value
	 * @return
	 */
	private boolean waitMajorPromises(BallotID ballotID, Object value){
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

					sendAcceptRequest(ballotID, value);

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
	private void sendAcceptRequest(BallotID ballotID, Object val){
		PaxosMessage acceptRequest = new PaxosMessage(
				PaxosMessage.MessageType.ACCEPT, ballotID, val
		);

		logger.info("Sending accept request: " + acceptRequest);

		gcl.broadcastMsg(acceptRequest);

		waitAcceptAcks(ballotID);
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
	private boolean waitAcceptAcks(BallotID ballotID){
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

					sendConfirm(ballotID);

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
	private void sendConfirm(BallotID ballotID) {
		PaxosMessage confirmMessage = new PaxosMessage(
				PaxosMessage.MessageType.CONFIRM, ballotID, null
		);

		logger.info("Sending confirm message: " + confirmMessage);
		gcl.broadcastMsg(confirmMessage);
	}

	// TODO: figure out what is the next message in the total order.
	// NOTE: Messages delivered in ALL the processes in the group should deliver this in the same order.

	/**
	 * Processes incoming Paxos messages and handles them based on their message type.
	 * Depending on the type of Paxos message (PROPOSE, ACCEPT, CONFIRM, etc.), the corresponding
	 * behavior is executed, such as receiving proposals, accepting values, or returning confirmed moves.
	 *
	 * @return If a CONFIRM message is received, this method returns the confirmed move (an Object array).
	 *         Otherwise, it returns null.
	 * @throws InterruptedException if the thread is interrupted while waiting for messages.
	 */
	public Object acceptTOMsg() throws InterruptedException
	{
		GCMessage gcmsg = gcl.readGCMessage();
		PaxosMessage paxosMessage = (PaxosMessage) gcmsg.val;

		switch (paxosMessage.getType()) {
			case PROPOSE:
				receivePropose(paxosMessage);
				break;
			case ACCEPT:
				receiveAccept(paxosMessage);
				break;
			case CONFIRM:
				return receiveConfirm(paxosMessage);
			case PROMISE:
				break;
			case ACCEPT_ACK:
				logger.info("Received ACCEPT_ACK message: " + paxosMessage);
				break;
			case REJECT:
				logger.info("Received REJECT message: " + paxosMessage);
				break;
			default:
				logger.warning("Unknown message type received: " + paxosMessage);
				break;
		}

		return null;
	}

	/**
	 * Handles the reception of a PROPOSE message in the Paxos protocol.
	 * If the received proposal has a higher ballot ID than the current maxBallotID,
	 * it updates the maxBallotID and sends a PROMISE message to the proposer.
	 * If the ballot ID is lower, it sends a REJECT message.
	 *
	 * This method is synchronized to ensure thread-safe access to shared resources.
	 *
	 * @param msg The PaxosMessage containing the proposal from a proposer.
	 */
	private synchronized void receivePropose(PaxosMessage msg) {
		// be invoked immediately when a process receives a propose message.
		failCheck.checkFailure(FailCheck.FailureType.RECEIVEPROPOSE);
		if (this.maxBallotID == null || msg.getBallotID().compareTo(this.maxBallotID) > 0) {
			// The incoming ballot ID is higher than the maxBallotID
			// The value will be sent along, don't need to check it again
			this.maxBallotID = msg.getBallotID();  // Update the highest seen BallotID
			PaxosMessage promiseMessage = new PaxosMessage(
					PaxosMessage.MessageType.PROMISE,
					msg.getBallotID(),
					this.value,
					myProcess,
					this.maxBallotID
			);
			gcl.sendMsg(promiseMessage, msg.getProposer());  // Send promise to proposer
		} else {
			// Incoming BallotID is smaller, reject it
			PaxosMessage rejectMessage = new PaxosMessage(
					PaxosMessage.MessageType.REJECT,
					this.maxBallotID,  // Send the current highest ballotID to proposer
					null,
					myProcess,
					this.maxBallotID
			);
			gcl.sendMsg(rejectMessage, msg.getProposer());
		}
		//invoked immediately AFTER a process sends out its vote (promise or refuse) for leader election
		failCheck.checkFailure(FailCheck.FailureType.AFTERSENDVOTE);
	}

	/**
	 * Handles the reception of an ACCEPT message in the Paxos protocol.
	 * If the received accept message has a ballot ID matching the maxBallotID,
	 * it accepts the value and sends an ACCEPT_ACK message back to the proposer.
	 * If the ballot ID is different, it sends a REJECT message.
	 *
	 * This method is synchronized to ensure thread-safe access to shared resources.
	 *
	 * @param msg The PaxosMessage containing the accept request from a proposer.
	 */
	private synchronized void receiveAccept(PaxosMessage msg) {
		if (msg.getBallotID().equals(this.maxBallotID)) {
			this.value = msg.getValue();  // Accept the value
			PaxosMessage acceptAckMessage = new PaxosMessage(
					PaxosMessage.MessageType.ACCEPT_ACK,
					msg.getBallotID(),
					this.value,
					myProcess,
					this.maxBallotID
			);
			gcl.sendMsg(acceptAckMessage, msg.getProposer());
		} else {
			PaxosMessage rejectMessage = new PaxosMessage(
					PaxosMessage.MessageType.REJECT,
					this.maxBallotID,  // Send the current highest ballotID to proposer
					null, // TODO: figure out whether we should include info when rejecting
					myProcess,
					this.maxBallotID
			);
			gcl.sendMsg(rejectMessage, msg.getProposer());  // Send reject message to proposer
		}
	}

	/**
	 * Handles the reception of a CONFIRM message in the Paxos protocol.
	 * When a CONFIRM message is received with a matching ballot ID, the confirmed move
	 * is returned, which contains game move information.
	 *
	 * @param msg The PaxosMessage containing the confirmation message from the proposer.
	 * @return The move information, typically an Object array containing [playerNum, direction].
	 * @throws RuntimeException if the ballot ID in the CONFIRM message does not match the local maxBallotID.
	 */
	private Object[] receiveConfirm(PaxosMessage msg) {
		if (msg.getBallotID().equals(this.maxBallotID)) {
			// This message contains the move to apply to the game map
			Object[] moveInfo = (Object[]) msg.getValue(); // moveInfo contains [playerNum, direction]
			return moveInfo;
		}
		throw new RuntimeException("Confirm phase: local ballot id and msg ballot id are different.");
	}

	//TODO:
	// Add any of your own shutdown code into this method.
	public void shutdownPaxos()
	{
		gcl.shutdownGCL();
	}


}


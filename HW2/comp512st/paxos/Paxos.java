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
	private volatile boolean isShuttingDown = false;


	private int proposalSlot = 0;  // Local sequence for proposals
	private final Map<Integer, Object> acceptedValues = new HashMap<>();
	private final BlockingQueue<PaxosMessage> proposeQueue = new LinkedBlockingQueue<>();
	private final BlockingQueue<PaxosMessage> proposeResponseQueue = new LinkedBlockingQueue<>();
	private final BlockingQueue<PaxosMessage> acceptRequestQueue = new LinkedBlockingQueue<>();
	private final BlockingQueue<PaxosMessage> acceptResponseQueue = new LinkedBlockingQueue<>();
	private final BlockingQueue<PaxosMessage> confirmQueue = new LinkedBlockingQueue<>();
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

		new Thread(this::messageDispatcher).start();

	}

	private void messageDispatcher() {
		while (!isShuttingDown) {
			try {
				PaxosMessage message = (PaxosMessage) gcl.readGCMessage().val;

				// Distribute messages based on their type
				switch (message.getType()) {
					case PROPOSE:
						proposeQueue.put(message); // Place in promise queue
						break;
					case PROMISE:
					case REJECT_PROPOSE:
						proposeResponseQueue.put(message); // Place in promise queue
						break;
					case ACCEPT:
						acceptRequestQueue.put(message);
						break;
					case ACCEPT_ACK:
					case REJECT_ACCEPT:
						acceptResponseQueue.put(message); // Place in promise queue
						break;
					case CONFIRM:
						confirmQueue.put(message); // Place in confirm queue
						break;
					default:
						logger.warning("Unhandled message type: " + message.getType());
						break;
				}
			} catch (InterruptedException e) {
				if (isShuttingDown) {
					logger.info("Message dispatcher shutting down.");
					break; // Exit loop if shutdown is in progress.
				} else {
					logger.severe("Message dispatcher interrupted: " + e.getMessage());
					Thread.currentThread().interrupt(); // Restore interrupted status
				}
			}
		}
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

		if (val == null) {
			logger.severe("Broadcast value is null");
			return;
		}


		boolean accepted = false;
		int retryInterval = 100;

		try{
			proposalQueue.put(val);

			while(!proposalQueue.isEmpty() && !isShuttingDown){

				Object proposalValue = proposalQueue.peek();

				proposalSlot++;

				BallotID ballotID = new BallotID(proposalSlot, myProcess);
				PaxosMessage proposal = new PaxosMessage(
						PaxosMessage.MessageType.PROPOSE, ballotID, myProcess, val
				);

				logger.info("Broadcasting proposal: " + proposal);

				// Simulate failure before broadcasting
				failCheck.checkFailure(FailCheck.FailureType.AFTERSENDPROPOSE);


				if (isShuttingDown) {
					logger.warning("Proposer can't broadcast message, system is shutting down.");
					return;
				}

				gcl.broadcastMsg(proposal);

				accepted = waitMajorPromises(ballotID, val);

				// if installed successfully, remove the proposal
				// else, try again
				if (accepted) {
					proposalQueue.poll();
				} else {
					logger.warning("Retrying proposal: " + proposalValue);
					clearOldMessages(ballotID);
//					Thread.sleep((long) (retryInterval * Math.random()));

				}
			}
		}catch(InterruptedException e) {
			logger.severe("Failed to queue proposal: " + e.getMessage());
			Thread.currentThread().interrupt();
		}
	}

	private void clearOldMessages(BallotID ballotID){
		proposeResponseQueue.removeIf(message -> message.getBallotID().compareTo(ballotID) == 0);
		acceptResponseQueue.removeIf(message -> message.getBallotID().compareTo(ballotID) == 0);
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
		Set<PaxosMessage> promises = new HashSet<>();
		Set<PaxosMessage> rejections = new HashSet<>();

		try{
			while(promises.size() <= allGroupProcesses.length/2 ){

				PaxosMessage response = proposeResponseQueue.take(); // Block until promise is available

//				PaxosMessage response = (PaxosMessage) gcl.readGCMessage().val;

				// Ignore messages that belong to an older ballot
				if (response.getBallotID().compareTo(ballotID) < 0) {
					logger.info("Ignoring stale message with lower ballotID: " + response.getBallotID());
					continue;
				}

				logger.info("Received promise-phase response: " + response);

				if(response.getType() == PaxosMessage.MessageType.REJECT_PROPOSE){
					rejections.add(response);
				}

				// if we get a promise message, and the promise is made on the current ballot ID
				// then count it toward the majority
				if(response.getType() == PaxosMessage.MessageType.PROMISE){
					if(response.getBallotID().compareTo(ballotID) > 0 || this.value != null){
						logger.warning("Higher ballotID detected. Giving up current proposal.");
						return false;
					}
					promises.add(response);
				}

				// Either final value is the one we proposed, or the highest accepted value
				if(promises.size() > allGroupProcesses.length/2){
					logger.info("Majority promises received for ballotID: " + ballotID);
					failCheck.checkFailure(FailCheck.FailureType.AFTERBECOMINGLEADER);

					return sendAcceptRequest(ballotID, value);

				}else if(rejections.size() > allGroupProcesses.length/2){
					logger.info("At least half of the proposals were rejected for ballot ID:" + ballotID);
					return false;
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
	private boolean sendAcceptRequest(BallotID ballotID, Object val){
		PaxosMessage acceptRequest = new PaxosMessage(
				PaxosMessage.MessageType.ACCEPT, ballotID, myProcess, val
		);

		logger.info("Sending accept request: " + acceptRequest);

		if (isShuttingDown) {
			logger.warning("Proposer can't broadcast message, system is shutting down.");
			return false;
		}

		gcl.broadcastMsg(acceptRequest);

		return waitAcceptAcks(ballotID, val);
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
	private boolean waitAcceptAcks(BallotID ballotID, Object val){
		Set<PaxosMessage> acceptAcks = new HashSet<>();
		Set<PaxosMessage> rejections = new HashSet<>();

		try{
			while(acceptAcks.size() <= allGroupProcesses.length / 2){

				PaxosMessage response = acceptResponseQueue.take();
//				PaxosMessage response = (PaxosMessage) gcl.readGCMessage().val;

				if(response.getType() == PaxosMessage.MessageType.ACCEPT_ACK &&
						response.getBallotID().compareTo(ballotID) == 0
				){
					acceptAcks.add(response);
				}

				if(response.getType() == PaxosMessage.MessageType.REJECT_ACCEPT){
					rejections.add(response);
				}


				if (acceptAcks.size() > allGroupProcesses.length / 2) {

					logger.info("Majority AcceptAck received for ballotID: " + ballotID);

					failCheck.checkFailure(FailCheck.FailureType.AFTERVALUEACCEPT);

					sendConfirm(ballotID, val);

					return true;
				} else if (rejections.size() > allGroupProcesses.length/2){
					logger.info("At least half of accept requests werer rejected for ballotID:" + ballotID);
					return false;
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
	private void sendConfirm(BallotID ballotID, Object val) {
		PaxosMessage confirmMessage = new PaxosMessage(
				PaxosMessage.MessageType.CONFIRM, ballotID, myProcess, val
		);

		if (isShuttingDown) {
			logger.warning("Proposer can't broadcast confirm message, system is shutting down.");
			return;
		}

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
		// re-initialize
		this.value = null;

		Object confirmedMove = null;
		while (confirmedMove == null) {
			// Check all queues for the appropriate message to process
			if (!proposeQueue.isEmpty()) {

				PaxosMessage proposeMessage = proposeQueue.take();
				receivePropose(proposeMessage);

			} else if (!acceptRequestQueue.isEmpty()) {

				PaxosMessage acceptMessage = acceptRequestQueue.take();
				receiveAccept(acceptMessage);

			} else if (!confirmQueue.isEmpty()) {

				PaxosMessage confirmMessage = confirmQueue.take();
				confirmedMove = receiveConfirm(confirmMessage);

			}

			// Failure simulation after receiving value for testing
			failCheck.checkFailure(FailCheck.FailureType.AFTERVALUEACCEPT);

		}
		return confirmedMove;
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

		if (isShuttingDown) {
			logger.warning("Acceptor can't send message, system is shutting down.");
			return;
		}

		if (this.maxBallotID == null || msg.getBallotID().compareTo(this.maxBallotID) > 0) {
			// The incoming ballot ID is higher than the maxBallotID
			// The value will be sent along, don't need to check it again
			this.maxBallotID = msg.getBallotID();  // Update the highest seen BallotID
			PaxosMessage promiseMessage = new PaxosMessage(
					PaxosMessage.MessageType.PROMISE,
					msg.getBallotID(),
					msg.getProposer(),
					this.value,
					this.maxBallotID
			);
			gcl.sendMsg(promiseMessage, msg.getProposer());  // Send promise to proposer
		} else {
			// Incoming BallotID is smaller, reject it
			PaxosMessage rejectMessage = new PaxosMessage(
					PaxosMessage.MessageType.REJECT_PROPOSE,
					this.maxBallotID,  // Send the current highest ballotID to proposer
					msg.getProposer(),
					msg.getValue(),
					this.maxBallotID
			);

			gcl.sendMsg(rejectMessage, msg.getProposer());
		}
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
					msg.getProposer(),
					this.value,
					this.maxBallotID
			);
			gcl.sendMsg(acceptAckMessage, msg.getProposer());
		} else {
			PaxosMessage rejectMessage = new PaxosMessage(
					PaxosMessage.MessageType.REJECT_ACCEPT,
					this.maxBallotID,  // Send the current highest ballotID to proposer
					msg.getProposer(),
					null, // TODO: figure out whether we should include info when rejecting
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
//		if (msg.getBallotID().equals(this.maxBallotID)) {
//			// This message contains the move to apply to the game map
//			Object[] moveInfo = (Object[]) msg.getValue(); // moveInfo contains [playerNum, direction]
//
//			if (moveInfo == null) {
//				logger.severe("Received CONFIRM message with null value");
//			} else {
//				logger.info("Confirmed move: " + moveInfo[0] + " moves " + moveInfo[1]);
//			}
//
//			return moveInfo;
//		}
//		throw new RuntimeException("Confirm phase: local ballot id and msg ballot id are different.");
		Object[] moveInfo = (Object[]) msg.getValue(); // moveInfo contains [playerNum, direction]

		if (moveInfo == null) {
			logger.severe("Received CONFIRM message with null value");
		} else {
			logger.info("Confirmed move: " + moveInfo[0] + " moves " + moveInfo[1]);
		}

		return moveInfo;
	}

	//TODO:
	// Add any of your own shutdown code into this method.
	public void shutdownPaxos()
	{
		isShuttingDown = true;

		// give time to dispatcher to stop
		try{
			Thread.sleep(500);
		} catch (InterruptedException e){
			Thread.currentThread().interrupt();
		}

		gcl.shutdownGCL();
	}
}


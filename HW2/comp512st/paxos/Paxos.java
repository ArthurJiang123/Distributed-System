package comp512st.paxos;

// Access to the GCL layer
import comp512.gcl.*;

import comp512.utils.*;

// Any other imports that you may need.
import java.io.*;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;
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
	private final BlockingQueue<PaxosMessage> proposeResponseQueue = new LinkedBlockingQueue<>();
	private final BlockingQueue<PaxosMessage> acceptResponseQueue = new LinkedBlockingQueue<>();
	private final BlockingQueue<PaxosMessage> confirmQueue = new LinkedBlockingQueue<>();
	private final BlockingQueue<Object> proposalQueue = new LinkedBlockingQueue<>();
	private BallotID maxBallotID;
	private Object value;
	int timeout;
	int retryInterval = 800;

	private final MessageDispatcher messageDispatcher;

	public Paxos(String myProcess, String[] allGroupProcesses, Logger logger, FailCheck failCheck) throws IOException, UnknownHostException
	{
		this.failCheck = failCheck;

		this.gcl = new GCL(myProcess, allGroupProcesses, null, logger) ;

		this.myProcess = myProcess;
		this.allGroupProcesses = allGroupProcesses;
		this.logger = logger;

		timeout = 6000 + allGroupProcesses.length * 500;

		messageDispatcher = new MessageDispatcher();
		new Thread(messageDispatcher).start();
	}

	/**
	 * Listening on incoming GC messages.
	 */
	class MessageDispatcher implements Runnable{
		private final ExecutorService proposerExecutor;
		private final ExecutorService acceptorExecutor;

		public MessageDispatcher() {
			proposerExecutor = Executors.newFixedThreadPool(allGroupProcesses.length + 1);
			acceptorExecutor = Executors.newFixedThreadPool(allGroupProcesses.length + 1);
		}
		@Override
		public void run() {
			while (!isShuttingDown) {
				try {
					if (isShuttingDown) break;
					PaxosMessage message = (PaxosMessage) gcl.readGCMessage().val;

					// If the msg should be sent to acceptors（PROPOSE, ACCEPT)
					if (message.getType() == PaxosMessage.MessageType.PROPOSE
							|| message.getType() == PaxosMessage.MessageType.ACCEPT
							|| message.getType() == PaxosMessage.MessageType.CONFIRM) {
						acceptorExecutor.submit(() -> receiveFromProposers(message));
					} else {
						// If the msg should be sent to proposers（PROMISE, REJECT_PROPOSE, ACCEPT_ACK, REJECT_ACCEPT, CONFIRM)
						proposerExecutor.submit(() -> receiveFromAcceptors(message));
					}
				} catch (InterruptedException e) {
					if (isShuttingDown) {
						logger.info("Message dispatcher shutting down.");
						break;
					} else {
						logger.severe("Message dispatcher interrupted: " + e.getMessage());
						Thread.currentThread().interrupt();
					}
				}
			}
		}
		private void receiveFromAcceptors(PaxosMessage message) {
			if (isShuttingDown) return;

			try {
				switch (message.getType()) {
					case PROMISE:
					case REJECT_PROPOSE:
						proposeResponseQueue.put(message);
						break;
					case ACCEPT_ACK:
					case REJECT_ACCEPT:
						acceptResponseQueue.put(message);
						break;
					default:
						logger.warning("Unhandled proposer message type: " + message.getType());
				}
			} catch (InterruptedException e) {
				logger.severe("Failed to handle proposer message: " + e.getMessage());
				Thread.currentThread().interrupt();
			}
		}

		private void receiveFromProposers(PaxosMessage message) {
			try {
				switch (message.getType()) {
					case PROPOSE:
						receivePropose(message);
						break;
					case ACCEPT:
						receiveAccept(message);
						break;
					case CONFIRM:
						confirmQueue.put(message);
						break;
					default:
						logger.warning("Unhandled acceptor message type: " + message.getType());
				}
			} catch (Exception e) {
				logger.severe("Failed to handle acceptor message: " + e.getMessage());
			}
		}

		public void shutdown(){
			proposerExecutor.shutdown();
			acceptorExecutor.shutdown();
		}
	}



	// This is what the application layer is going to call to send a message/value, such as the player and the move
	public void broadcastTOMsg(Object val)
	{
		if (isShuttingDown) return;

		boolean accepted = false;

		// Record start time
		long startTime = System.currentTimeMillis();

		try {
			proposalQueue.put(val);

			while (!proposalQueue.isEmpty() && !isShuttingDown) {

				Object proposalValue = proposalQueue.peek();

				synchronized (this) {
					proposalSlot = maxBallotID == null ? proposalSlot + 1 : maxBallotID.getValue() + 1;
				}

				BallotID ballotID = new BallotID(proposalSlot, myProcess);
				PaxosMessage proposal = new PaxosMessage(
						PaxosMessage.MessageType.PROPOSE, ballotID, myProcess, val
				);

				logger.info("Broadcasting proposal with ballot id: " + ballotID + " - " + proposal);

				if (isShuttingDown) {
					logger.warning("Proposer can't broadcast message, system is shutting down.");
					return;
				}

				gcl.broadcastMsg(proposal);

				// Simulate failure
				failCheck.checkFailure(FailCheck.FailureType.AFTERSENDPROPOSE);
				if (isShuttingDown) return;

				accepted = propose(ballotID, val);

				// If installed successfully, remove the proposal
				// Else, try again
				if (accepted) {
					proposalQueue.poll();

					// Record end time and log move time
					long endTime = System.currentTimeMillis();
					long timeTaken = endTime - startTime;

					logMoveTime(timeTaken);  // Log the time and move details
				} else {
					logger.warning("Retrying proposal: " + proposalValue);
					clearOldMessages(ballotID);
					Thread.sleep((long) (100 + retryInterval * Math.random()));
				}
			}
		} catch (InterruptedException e) {
			logger.severe("Failed to queue proposal: " + e.getMessage());
			Thread.currentThread().interrupt();
		}
	}

	private void clearOldMessages(BallotID ballotID) {
		proposeResponseQueue.removeIf(message ->
				message.getBallotID().compareTo(ballotID) <= 0
		);
		acceptResponseQueue.removeIf(message ->
				message.getBallotID().compareTo(ballotID) <= 0
		);
	}

	/**
	 * propose a move
	 * @param ballotID
	 * @param value
	 * @return
	 */
	private boolean propose(BallotID ballotID, Object value){
		Set<PaxosMessage> promises = new HashSet<>();
		Set<PaxosMessage> rejections = new HashSet<>();

		long startTime = System.currentTimeMillis();

		try{
			while(promises.size() <= allGroupProcesses.length/2 && !isShuttingDown ){

				if (System.currentTimeMillis() - startTime > timeout) {
					logger.warning("Timed out waiting for majority promises for ballotID: " + ballotID);
					return false;
				}

				PaxosMessage response = proposeResponseQueue.poll(timeout, TimeUnit.MILLISECONDS);

				if (response == null || isShuttingDown) {
					return false;
				}

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
				// otherwise, it gives up ( avoid loss & duplicates of proposals)
				if(response.getType() == PaxosMessage.MessageType.PROMISE){
					if(response.getAcceptedID().compareTo(ballotID) > 0 || response.getValue() != null){
						logger.warning("Higher ballotID detected. Giving up current proposal.");
						return false;
					}
					promises.add(response);
				}

				// if becoming the leader, send accept request
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
	 * wait for a majority of accept_acks from acceptors
	 * @param ballotID
	 * @param val
	 * @return
	 */
	private boolean waitAcceptAcks(BallotID ballotID, Object val){
		if (isShuttingDown) {
			logger.warning("Proposer is shutting down.");
			return false;
		}

		Set<PaxosMessage> acceptAcks = new HashSet<>();
		Set<PaxosMessage> rejections = new HashSet<>();

		long startTime = System.currentTimeMillis();

		try{
			while(acceptAcks.size() <= allGroupProcesses.length / 2 && !isShuttingDown ){

				// upon timeout, give up
				if (System.currentTimeMillis() - startTime > timeout) {
					logger.warning("Timed out waiting for majority acceptance for ballotID: " + ballotID);
					return false;
				}

				PaxosMessage response = acceptResponseQueue.poll(timeout, TimeUnit.MILLISECONDS);

				// after timeout, stop polling messages. Then give up
				if (response == null || isShuttingDown){
					return false;
				}

				if (response.getBallotID().compareTo(ballotID) < 0) {
					logger.info("Ignoring stale message with lower ballotID: " + response.getBallotID());
					continue;
				}

				if(response.getType() == PaxosMessage.MessageType.ACCEPT_ACK){
					acceptAcks.add(response);
				}

				if(response.getType() == PaxosMessage.MessageType.REJECT_ACCEPT){
					rejections.add(response);
				}
				// after being accepted by the majority, send confirmation messages
				if (acceptAcks.size() > allGroupProcesses.length / 2) {

					logger.info("Majority AcceptAck received for ballotID: " + ballotID);

					failCheck.checkFailure(FailCheck.FailureType.AFTERVALUEACCEPT);

					sendConfirm(ballotID, val);

					return true;
				} else if (rejections.size() > allGroupProcesses.length/2){
					logger.info("At least half of accept requests were rejected for ballotID:" + ballotID);
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
	 * Send confirmation messages to acceptors
	 * @param ballotID
	 * @param val
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

	// NOTE: Messages delivered in ALL the processes in the group should deliver this in the same order.
	public Object acceptTOMsg() throws InterruptedException {
		Object confirmedMove = null;
		long startTime = System.currentTimeMillis();
		// listens for confirmQueue
		// before timeout, if a confirmation appears in the queue, it will be processed
		// after timeout, it will reset the value variable and retry
		while (confirmedMove == null && !isShuttingDown) {

			synchronized (this){
				if(System.currentTimeMillis() - startTime > timeout && this.value != null){
					logger.info("Acceptor: Current proposal time out. Reset value");
					this.value = null;
					startTime = System.currentTimeMillis();
				}
			}

			if (!confirmQueue.isEmpty()) {
				PaxosMessage confirmMessage = confirmQueue.take();
				confirmedMove = receiveConfirm(confirmMessage);

				synchronized (this){
					this.value = null;
				}
			}
		}
		return confirmedMove;
	}

	/**
	 * Upon receiving a proposal from a proposer
	 * @param msg
	 */
	private void receivePropose(PaxosMessage msg) {

		failCheck.checkFailure(FailCheck.FailureType.RECEIVEPROPOSE);

		if (isShuttingDown) {
			logger.warning("Acceptor can't send message, system is shutting down.");
			return;
		}
		boolean accept = true;

		BallotID acceptedID;
		Object curValue;
		synchronized (this){
			if (this.maxBallotID == null || msg.getBallotID().compareTo(this.maxBallotID) > 0 ) {
				if(this.value != null){
					acceptedID = this.maxBallotID;
				}else{
					acceptedID = msg.getBallotID();
					this.maxBallotID = msg.getBallotID();
				}
			}else{
				accept = false;
				acceptedID = this.maxBallotID;
			}
			curValue = value;
		}

		if(accept){
			PaxosMessage promiseMessage = new PaxosMessage(
					PaxosMessage.MessageType.PROMISE,
					msg.getBallotID(), // promised ID
					msg.getProposer(), // proposer
					curValue, // curValue is null if acceptor did not accept others
					acceptedID // already-accepted ID
			);
			if (isShuttingDown) {
				logger.warning("Acceptor is shutting down.");
				return;
			}
			gcl.sendMsg(promiseMessage, msg.getProposer());
		}else{
			PaxosMessage rejectMessage = new PaxosMessage(
					PaxosMessage.MessageType.REJECT_PROPOSE,
					msg.getBallotID(),  // Send the original ballotID to proposer
					msg.getProposer(),
					msg.getValue(),
					acceptedID // Send the accepted ballotID to proposer
			);

			if (isShuttingDown) {
				logger.warning("Acceptor is shutting down.");
				return;
			}
			gcl.sendMsg(rejectMessage, msg.getProposer());
		}
	}

	/**
	 * Upon receiving an accept request from a proposer
	 * @param msg
	 */
	private void receiveAccept(PaxosMessage msg) {

		if (isShuttingDown) {
			logger.warning("Acceptor is shutting down.");
			return;
		}

		boolean accept = true;

		BallotID curID;
		Object curValue;

		synchronized (this){
			if (msg.getBallotID().equals(this.maxBallotID)) {
				this.value = msg.getValue();
			}else{
				accept = false;
			}
			curID = this.maxBallotID;
			curValue = this.value;
		}

		if(accept){
			PaxosMessage acceptAckMessage = new PaxosMessage(
					PaxosMessage.MessageType.ACCEPT_ACK,
					msg.getBallotID(),
					msg.getProposer(),
					curValue,
					curID
			);
			if (isShuttingDown) {
				logger.warning("Acceptor is shutting down.");
				return;
			}
			gcl.sendMsg(acceptAckMessage, msg.getProposer());
		}else{
			PaxosMessage rejectMessage = new PaxosMessage(
					PaxosMessage.MessageType.REJECT_ACCEPT,
					msg.getBallotID(),
					msg.getProposer(),
					null,
					curID
			);
			if (isShuttingDown) {
				logger.warning("Acceptor is shutting down.");
				return;
			}
			gcl.sendMsg(rejectMessage, msg.getProposer());
		}
		failCheck.checkFailure(FailCheck.FailureType.AFTERSENDVOTE);
	}

	/**
	 * Upon receiving a confirmation message from a proposer
	 * @param msg
	 * @return
	 */
	private synchronized Object[] receiveConfirm(PaxosMessage msg) {
		Object[] moveInfo = (Object[]) msg.getValue(); // moveInfo contains [playerNum, direction]

		if (moveInfo == null) {
			logger.severe("Received CONFIRM message with null value");
		} else {
			logger.info("Confirmed move: " + moveInfo[0] + " moves " + moveInfo[1]);
		}

		return moveInfo;
	}

	public void shutdownPaxos()
	{
		isShuttingDown = true;

		gcl.shutdownGCL();
		messageDispatcher.shutdown();

		// time for dispatcher to stop
		try{
			Thread.sleep(600);
		} catch (InterruptedException e){
			Thread.currentThread().interrupt();
		}

		proposeResponseQueue.clear();
		acceptResponseQueue.clear();
		confirmQueue.clear();
	}

	private void logMoveTime(long timeTaken) {
		// Extract player and interval information
		String port = myProcess.split(":")[1];

		// Get the player number
		// Extract the third digit from the port number
		char playerNumber = port.charAt(2); // Index 2 is the third character

		String logFileName = "player" + "_" + playerNumber + ".log";

		// Log the move with timing information
		String logEntry = String.format("Move by %s | Time Taken: %d ms ",
				myProcess, timeTaken);

		try (FileWriter logWriter = new FileWriter(logFileName, true)) {
			logWriter.write(logEntry + System.lineSeparator());
//			System.out.println("Log entry written: " + logEntry);
		} catch (IOException e) {
			logger.severe("Failed to write move log: " + e.getMessage());
		}
	}
}


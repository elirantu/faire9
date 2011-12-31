/*
 * Eyal Zohar, The Open University of Israel, 2008
 * Updated by Eliran Turgeman, The Open University of Israel, 2011
 */
package il.ac.openu.eyalzo.sim3;

/**
 * Main.
 * <p>
 * Change the parameters in the code if needed.
 * 
 * @author Eyal Zohar
 */
public class Main {
	private Crowd crowd;

	//
	// Parameters
	//
	/**
	 * Maximum number of download slots.
	 */
	// public final static int downSlotsNum = 6;
	/**
	 * Maximum number of upload slots.
	 */
	// public final static int upSlotsNum = 6;
	/**
	 * Maximum number of upload slots for the initial source. Normally higher
	 * than normal peers, because otherwise some pieces will be missing in the
	 * network while the network can distribute faster.
	 */
	// public final static int initialSourceUpSlotsNum = upSlotsNum * 2;
	/**
	 * Normal peer's download bandwidth Mu
	 */
	public final static int peerDownloadMu = 10;
	/**
	 * Normal peer's download bandwidth Sigma
	 */
	public final static int peerDownloadSigma = 4;
	/**
	 * Normal peer's upload bandwidth Mu
	 */
	public final static int peerUploadMu = 10;
	/**
	 * Normal peer's upload bandwidth Sigma
	 */
	public final static int peerUploadSigma = 4;
	/**
	 * Initial source's upload bandwidth Mu
	 */
	public final static int sourceUploadMu = 30;
	/**
	 * Initial source's upload bandwidth Sigma
	 */
	public final static int sourceUploadSigma = 4;
	/**
	 * Number of peers participating in the distibution.
	 */
	public final static int peersNum = 10000;
	/**
	 * Type of P2P network.
	 */
	public final static P2pType p2pType = P2pType.FAIRE9BUTTERFLY;
	/**
	 * Maximum number of known peers each peer can acquire.
	 */
	public final static int knownPeersMax = 20;
	/**
	 * Maximum number of pending sent requests each peers is allowed to have.
	 */
	public final static int downPendingMax = 100;
	/**
	 * Number of pieces in the file.
	 */
	public final static int piecesNum = 200;
	/**
	 * Number of known peers acquired on join.
	 */
	public final static int initialKnownPeersNum = 5;
	/**
	 * How long a request should stay pending until it is abandoned.
	 */
	public final static int requestTtl = 200;
	/**
	 * Optional detailed print for debug.
	 */
	public final static boolean print = false;
	/**
	 * The chance a seeder will leave on a piece-time (percents).
	 */
	public final static int churnSeederLeavePercents = 0;
	/**
	 * Number of special peers.
	 * 
	 * @see #specialType
	 */
	public final static int specialPeersNum = 0;
	/**
	 * Type of special peers, used when a specific property is under test.
	 */
	public final static SpecialType specialType = SpecialType.FREE_RIDERS;
	/**
	 * How often to perform a peer-exchange, in terms of piece-time.
	 */
	public final static int roundsPerPeerExchange = 2;

	public Main() {

		//
		// For display only
		//
		int slotSpeedKbps = 500;
		int fileSizeMB = 250;

		//
		// Prepare
		//
		int pieceSizeBytes = (fileSizeMB * 1000 * 1000 / piecesNum);
		int pieceSlotTimeSec = pieceSizeBytes * 8 / (slotSpeedKbps * 1000);

		crowd = new Crowd(sourceUploadMu, sourceUploadSigma);
		crowd.addPeers(peersNum - 1, peerDownloadMu, peerDownloadSigma,
				peerUploadMu, peerUploadSigma, downPendingMax, knownPeersMax,
				requestTtl);
		crowd.setSpecialPeers(specialType, specialPeersNum);

		long time2 = System.currentTimeMillis();
		long memory2 = (Runtime.getRuntime().totalMemory() - Runtime
				.getRuntime().freeMemory());

		//
		// Download pending
		//
		int rounds = crowd.play(print, churnSeederLeavePercents,
				pieceSlotTimeSec, roundsPerPeerExchange, initialKnownPeersNum);

		if (print
				&& (p2pType == P2pType.FAIRE9 || p2pType == P2pType.FAIRE9PLUS || p2pType == P2pType.FAIRE9BUTTERFLY)) {
			System.out.println("\nUploads by Position\n===================");
			System.out.println(crowd.getUploadsCount().toString());
		}

		long time3 = System.currentTimeMillis();
		long memory3 = (Runtime.getRuntime().totalMemory() - Runtime
				.getRuntime().freeMemory());

		System.out.println(String.format("\nRun time: %,d mSec",
				(time3 - time2)));
		System.out.println(String.format("Memory: %,d bytes",
				(memory3 - memory2)));
		System.out.println();

		// Summary for Excel
		System.out
				.println(String
						.format("Rounds\tLatency\tLatency stdev\tUp stdev\tUp max\tUp uniq\tSystem\tPeers\tPieces\t"
								+ "Down BW\tUp BW\tDown pending\tSources max\tSources init\t"
								+ "Request TTL\tSeeders churn\tSpecial\tRounds exchange\tReq spec\tReq any\tBitmaps"));

		float latency = crowd.getAverageLatency();
		System.out.println(String.format(
				"%,d\t%5.2f\t%8.2f\t%8.2f\t%6d\t%7.2f\t%s\t%,d\t%,d\t"
						+ "%,7d\t%,5d\t%,12d\t%,11d\t%,12d\t"
						+ "%,11d\t%,13d\t%,7d\t%,13d\t%,8d\t%,7d\t%,7d",
				rounds, latency, crowd.getLatencyStdev(latency),
				crowd.getUploadsStdev(), crowd.getUploadsMax(),
				crowd.getUploadsDistinctPiecesAverage(), p2pType.name(),
				peersNum, piecesNum, 0, 0, downPendingMax,
				knownPeersMax, initialKnownPeersNum, requestTtl,
				churnSeederLeavePercents, specialPeersNum,
				roundsPerPeerExchange, Crowd.statRequestsSpecificPiece,
				Crowd.statRequestsAnyPiece, Crowd.statBitmapExchange));

		if (specialPeersNum > 0) {
			crowd.printSpecialPeersSummary();
		}
	}

	public static void main(String[] args) {
		new Main();
	}
}

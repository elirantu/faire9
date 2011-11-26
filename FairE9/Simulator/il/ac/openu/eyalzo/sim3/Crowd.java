/*
 * Eyal Zohar, The Open University of Israel, 2008
 */
package il.ac.openu.eyalzo.sim3;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Random;

/**
 * @author Eyal Zohar
 */
public class Crowd
{
    /**
     * The peers, inclusing the initial source. Key is the peer serial.
     */
    private HashMap<Integer, Peer> peers = new HashMap<Integer, Peer>();
    /**
     * The chances that a seeder will leave the system in a round.
     */
    private int churnSeederLeavePercents;
    /**
     * Random for churn.
     */
    private static Random rand = new Random(System.currentTimeMillis());
    /**
     * All the peers that are active, and therefore can be added as known peers.
     */
    private LinkedList<Peer> activePeers = new LinkedList<Peer>();
    /**
     * The initial source.
     */
    public static Peer initialSource;
    /**
     * Type of special peers, if any.
     */
    private SpecialType specialType;

    //
    // Statistics
    //
    static int statRequestsSpecificPiece = 0;
    static int statRequestsAnyPiece = 0;
    static int statBitmapExchange = 0;

    /**
     * @param initialSourceUpSlotsNum
     *                Maximum simultaneous uploads performed by the initial
     *                source.
     */
    public Crowd(int initialSourceUpSlotsNum)
    {

	//
	// Prepare the initial source (the first peer)
	//
	initialSource = new Peer(1, 0, initialSourceUpSlotsNum, 0, 0, 0);
	initialSource.setAsInitialSource(initialSourceUpSlotsNum);
	peers.put(1, initialSource);
    }

    /**
     * Add the normal peers.
     * 
     * @param peersNum
     *                Number of peers to add.
     * @return List of all the peers added so far.
     */
    public LinkedList<Peer> addPeers(int peersNum, int downSlotsNum, int upSlotsNum, int downPendingMax,
	    int knownPeersMax, int requestTtl)
    {
	LinkedList<Peer> result = new LinkedList<Peer>();

	int lastPeerSerial = peers.size() + peersNum;

	//
	// Add all the peers as normal peers first
	//
	for (int i = (peers.size() + 1); i <= lastPeerSerial; i++)
	{
	    Peer peer = new Peer(i, downSlotsNum, upSlotsNum, downPendingMax, knownPeersMax, requestTtl);

	    // Add the new peer
	    this.peers.put(i, peer);

	    result.add(peer);
	}

	return result;
    }

    /**
     * Set the special peers, that are some of the peers already defined.
     * 
     * @param specialType
     *                Type of special peers.
     * @param specialPeersNum
     *                Number of special peers to set.
     */
    @SuppressWarnings("fallthrough")
    public void setSpecialPeers(SpecialType specialType, int specialPeersNum)
    {
	// Save for later print
	this.specialType = specialType;

	float factorList[] =
	{ 0.2f, 0.4f, 0.5f, 2f, 3f, 5f };
	int groupSize = specialPeersNum / factorList.length;

	int curSpecialIndex = (peers.size() - specialPeersNum) / 2;
	Peer normalPeer = peers.get(2);

	switch (specialType)
	{
	case PENDING_AND_KNOWN:
	    int normalPending = normalPeer.getPendingMax();
	    int normalKnown = normalPeer.getKnownPeersMax();
	    for (float curFactor : factorList)
	    {
		for (int i = 0; i < groupSize; i++, curSpecialIndex++)
		{
		    Peer curPeer = peers.get(curSpecialIndex);
		    curPeer.setKnownPeersMax((int) (curFactor * normalKnown));
		    curPeer.setDownPendingMax((int) (curFactor * normalPending));
		}
	    }
	    break;

	case NEW_COMERS:
	    groupSize = specialPeersNum / 10;
	    for (int j = 1; j <= 10; j++)
	    {
		int startRound = 1 + j * 3;
		for (int i = 0; i < groupSize; i++, curSpecialIndex++)
		{
		    Peer curPeer = peers.get(curSpecialIndex);
		    curPeer.setActive(false);
		    curPeer.setStartRound(startRound);
		}
	    }
	    break;

	case FREE_RIDERS:
	    for (int i = 0; i < specialPeersNum; i++, curSpecialIndex++)
	    {
		Peer curPeer = peers.get(curSpecialIndex);
		curPeer.setValue(specialType, 0);
	    }
	    break;

	case UP_SLOTS:
	    // Free-rider
	    factorList[factorList.length - 1] = 0f;
	    factorList[factorList.length - 2] = 0.75f;
	case DOWN_SLOTS:
	    int normalValue = normalPeer.getValue(specialType);
	    for (float curFactor : factorList)
	    {
		for (int i = 0; i < groupSize; i++, curSpecialIndex++)
		{
		    Peer curPeer = peers.get(curSpecialIndex);
		    curPeer.setValue(specialType, (int) (curFactor * normalValue));
		}
	    }
	    break;
	}
    }

    /**
     * Activate the peers that are inactive now and suppose to start now.
     * 
     * @param round
     *                Current round.
     */
    private void activatePeers(int round, int initialKnownPeersNum)
    {
	// If all are already active
	if (activePeers.size() == peers.size())
	    return;

	for (Peer curPeer : peers.values())
	{
	    if (curPeer.getStartRound() == round)
	    {
		curPeer.setActive(true);
		// Must be called before added to active peers
		setPeerInitialKnownPeers(curPeer, initialKnownPeersNum, round);
		activePeers.add(curPeer);
	    }
	}
    }

    private void setPeerInitialKnownPeers(Peer curPeer, int initialKnownPeersNum, int roundToFirstUse)
    {
	Random rand = new Random(System.currentTimeMillis());
	int activePeersNum = activePeers.size();
	for (int i = 0; i < initialKnownPeersNum && i < activePeersNum; i++)
	{
	    // Get one of the known peers that was not added before
	    int pos = rand.nextInt(activePeersNum - i);
	    Peer knownPeer = activePeers.get(pos);
	    curPeer.addKnownPeer(knownPeer, roundToFirstUse);
	    // Move the known peer to the end
	    activePeers.remove(knownPeer);
	    activePeers.add(knownPeer);
	}
    }

    /**
     * @param specialPeersNum
     *                How many special peers to run for each factor.
     * @return Number of rounds in game.
     */
    public int play(boolean print, int churnSeederLeavePercents, int pieceSlotTimeSec, int roundsPerPeerExchange,
	    int initialKnownPeersNum)
    {
	this.churnSeederLeavePercents = churnSeederLeavePercents;

	System.out
		.println("Round\tSeconds\tProgress\tPeers\tSeeds\tSources\tRequests\tUploads\tExchanges\tKnown\tUp-pending\tMin-piece\tBehind-20\tBehind-40\tMemory-MB");

	int seeders = 1;

	int round;
	for (round = 1;; round++)
	{
	    int requests = 0;
	    int exchanges = 0;

	    activatePeers(round, initialKnownPeersNum);

	    for (Peer peer : peers.values())
	    {
		if (!peer.isActive())
		    continue;

		// Download requests
		requests += peer.performSendDownloadRequests(round);
		// Single random peer exchange, once per 3 rounds
		if (rand.nextInt(roundsPerPeerExchange) == 0)
		{
		    exchanges += peer.performPeerExchange(round + 1);
		}
	    }

	    int uploads = 0;
	    for (Peer peer : peers.values())
	    {
		if (!peer.isActive())
		    continue;

		// Uploads
		uploads += peer.performUpload(round, print);
	    }

	    // Count seeders and activate churn on them
	    seeders = performSeedersChurn();

	    float downloadedPiecesAverage = getDownloadedPiecesAverage();

	    System.out.println(String.format(
		    "%,5d\t%,7d\t%8.2f\t%,5d\t%,5d\t%,5d\t%,8d\t%,7d\t%,5d\t%11.1f\t%10.1f\t%9d\t%,9d\t%,9d\t%,9d",
		    round, round * pieceSlotTimeSec, downloadedPiecesAverage * 100 / Main.piecesNum,
		    activePeers.size(), seeders, getSourcesCount(), requests, uploads, exchanges,
		    getKnownPeersAverage(), getUpPendingAverage(), getPiecesMin(),
		    getPeersBehind((int) (downloadedPiecesAverage - 20)),
		    getPeersBehind((int) (downloadedPiecesAverage - 40)), (Runtime.getRuntime().totalMemory() - Runtime
			    .getRuntime().freeMemory()) / 1024 / 1024));

	    if (seeders == activePeers.size())
		break;
	}

	return round;
    }

    /**
     * Print summary of the special peers, if there were any.
     */
    public void printSpecialPeersSummary()
    {
	if (specialType == null)
	    return;

	Peer normalPeer = peers.get(2);
	int normalValue = normalPeer.getValue(specialType);

	MapCounter<Float> mapLatency = new MapCounter<Float>();
	MapCounter<Float> mapUploads = new MapCounter<Float>();
	MapCounter<Float> mapOccurrences = new MapCounter<Float>();

	for (Peer curPeer : peers.values())
	{
	    // Skip the initial source
	    if (curPeer.isInitialSource())
		continue;

	    float curFactor = ((float) curPeer.getValue(specialType)) / normalValue;
	    mapLatency.add(curFactor, curPeer.getLatency());
	    mapUploads.add(curFactor, curPeer.getUploads());
	    mapOccurrences.inc(curFactor);
	}

	System.out.println("\nSpecial peers, " + specialType);
	System.out.println("================================");
	System.out.println("Factor\tSum\tPeers\tLatency");
	System.out.println(mapLatency.toString(mapOccurrences));

	System.out.println("\nSpecial peers, " + specialType);
	System.out.println("================================");
	System.out.println("Factor\tSum\tPeers\tUploads");
	System.out.println(mapUploads.toString(mapOccurrences));
    }

    /**
     * @return Average number of known peers per peer.
     */
    public float getKnownPeersAverage()
    {
	long sum = 0;

	for (Peer curPeer : peers.values())
	{
	    sum += curPeer.getKnownPeersCount();
	}

	return (float) sum / peers.size();
    }

    public float getUpPendingAverage()
    {
	long sum = 0;

	for (Peer curPeer : peers.values())
	{
	    sum += curPeer.getUpPendingCount();
	}

	return (float) sum / peers.size();
    }

    public float getDownPendingAverage()
    {
	long sum = 0;

	for (Peer curPeer : peers.values())
	{
	    sum += curPeer.getDownPendingCount();
	}

	return (float) sum / peers.size();
    }

    public float getDownloadedPiecesAverage()
    {
	long sum = 0;

	for (Peer curPeer : peers.values())
	{
	    sum += curPeer.getDownloadedPiecesCount();
	}

	// Minus the initial source
	sum -= initialSource.getDownloadedPiecesCount();

	return ((float) sum / peers.size());
    }

    public int getPeersBehind(int minPieces)
    {
	int result = 0;

	for (Peer curPeer : peers.values())
	{
	    if (curPeer.getDownloadedPiecesCount() < minPieces)
	    {
		result++;
	    }
	}

	return result;
    }

    /**
     * @return Number of active peers after churn, that already have everything.
     */
    public int performSeedersChurn()
    {
	int sum = 0;

	for (Peer curPeer : peers.values())
	{
	    if (curPeer.isSeeder() && curPeer.isActive())
	    {
		if (!curPeer.isInitialSource() && shouldChurn(churnSeederLeavePercents))
		{
		    // Also update the number of active peers
		    performPeerLeaving(curPeer);
		} else
		{
		    sum++;
		}
	    }
	}

	return sum;
    }

    /**
     * @return Number of active peers that already have something.
     */
    public int getSourcesCount()
    {
	int sum = 0;

	for (Peer curPeer : peers.values())
	{
	    if (curPeer.isSource() && curPeer.isActive())
	    {
		sum++;
	    }
	}

	return sum;
    }

    public float getAverageLatency()
    {
	int sum = 0;
	int count = 0;

	for (Peer curPeer : peers.values())
	{
	    int latency = curPeer.getLatency();
	    if (latency <= 0)
		continue;

	    count++;
	    sum += latency;
	}

	if (count <= 0)
	    return 0;

	return (float) sum / count;
    }

    public double getUploadsStdev()
    {
	float average = (peers.size() - 1) * Main.piecesNum / (float) peers.size();

	double sum = 0;
	for (Peer curPeer : peers.values())
	{
	    if (curPeer == initialSource)
		continue;

	    sum += Math.pow(curPeer.getUploads() - average, 2);
	}

	return Math.sqrt(sum / (peers.size() - 1));
    }

    public double getLatencyStdev(float average)
    {
	double sum = 0;
	for (Peer curPeer : peers.values())
	{
	    if (curPeer == initialSource)
		continue;

	    sum += Math.pow(curPeer.getLatency() - average, 2);
	}

	return Math.sqrt(sum / (peers.size() - 1));
    }

    public float getUploadsDistinctPiecesAverage()
    {
	int sum = 0;

	for (Peer curPeer : peers.values())
	{
	    if (curPeer == initialSource)
		continue;

	    sum += curPeer.getUploadsDistinctPieces();
	}

	return (float) sum / (peers.size() - 1);
    }

    public int getUploadsMax()
    {
	int result = 0;

	for (Peer curPeer : peers.values())
	{
	    if (curPeer == initialSource)
		continue;

	    result = Math.max(result, curPeer.getUploads());
	}

	return result;
    }

    public int getPiecesMin()
    {
	int result = Main.piecesNum;

	for (Peer curPeer : peers.values())
	{
	    result = Math.min(result, curPeer.getDownloadedPiecesCount());
	}

	return result;
    }

    public MapCounter<Integer> getUploadsCount()
    {
	MapCounter<Integer> result = new MapCounter<Integer>();

	for (Peer curPeer : peers.values())
	{
	    result.addAll(curPeer.getUploadsCount());
	}

	return result;
    }

    /**
     * Set peer as inactive. Other peers will know about it only when they will
     * try to download or upload.
     * 
     * @param curPeer
     *                The peer that is about to leave.
     */
    private void performPeerLeaving(Peer peerLeaving)
    {
	peerLeaving.setActive(false);
	activePeers.remove(peerLeaving);
	for (Peer curPeer : peers.values())
	{
	    curPeer.cleanupLeavingPeer(peerLeaving);
	}
    }

    public boolean shouldChurn(int churnSeederLeavePercents)
    {
	return rand.nextInt(100) < churnSeederLeavePercents;
    }

    public void printPeerDetails(int peerSerial)
    {
	Peer peer = peers.get(peerSerial);
	if (peer == null)
	    return;

	peer.printDetails();
    }
}

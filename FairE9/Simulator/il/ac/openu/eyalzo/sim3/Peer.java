/*
 * Eyal Zohar, The Open University of Israel, 2008
 */
package il.ac.openu.eyalzo.sim3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Random;
import java.util.TreeSet;
import java.util.Map.Entry;

/**
 * Peer records and logic.
 * 
 * @author Eyal Zohar
 */
public class Peer
{
	/**
	 * 1-based serial number of peer.
	 */
	private int								serial;
	/**
	 * Maximum number of peers to add on each peer-exchange.
	 */
	private static final int				MAX_PEERS_TO_ADD_ON_EXCHANGE	= 50;
	/**
	 * Flag for churn. True for active, or false if not active yet or left
	 * already.
	 */
	private boolean							active;
	/**
	 * Round when this peer should start: get its initial known peers and set
	 * {@link #active} to true. Default is round 1.
	 */
	private int								startRound						= 1;
	private int								requestTtl;
	private static Random rand = new Random(System.currentTimeMillis());

	//
	// Pieces
	//
	/**
	 * Weight of piece by 1-based index of piece. If null, this means that the
	 * system is not "weights".
	 */
	private int								weightByPiece[];
	/**
	 * Piece that has a given weight.
	 */
	private int								pieceByWeight[];
	/**
	 * Pieces that were already downloaded. Key is piece-serial. Value is round.
	 * Order is maintained for rotation of pieces to upload.
	 */
	private LinkedHashMap<Integer, Integer>	downloadedPieces				= new LinkedHashMap<Integer, Integer>();

	//
	// Download from others
	//
	/**
	 * Download Bandwidth 
	 */
	private int downSlotsMu;
	private int downSlotsSigma;
	/**
	 * Max number of downloads to perform at once.
	 */
	private int								downSlotsMax;
	private int								downSlotsOccupied;
	/**
	 * Max number of pending requests at once, each for a different peer.
	 */
	private int								downPendingMax;
	/**
	 * Pending download requests, by peer. Value is the piece-serial.
	 */
	private HashMap<Peer, PendingRequest>	downPending						= new HashMap<Peer, PendingRequest>();

	//
	// Upload to others
	//
	/**
	 * Pending upload requests. Keep the order for random and emule, where the
	 * requesters are simply moved to the end after every download.
	 */
	private LinkedHashMap<Peer, UpRequest>	upPending						= new LinkedHashMap<Peer, UpRequest>();
	/**
	 * Upload Bandwidth 
	 */
	private int upSlotsMu;
	private int upSlotsSigma;
	
	/**
	 * Max number of uploads to serve at once (upload bandwidth).
	 */
	private int								upSlotsMax;

	//
	// Other peers
	//
	private int								knownPeersMax;
	/**
	 * Peers known to this peer (accumulative). Value is the first round to use
	 * the peer. Ordered so when doing peer-exchange it will return the first
	 * peers.
	 */
	private LinkedHashMap<Peer, Integer>	knownPeers						= new LinkedHashMap<Peer, Integer>();
	/**
	 * The {@link #knownPeers} that are not in {@link #downPending}.
	 */
	private LinkedList<Peer>				freePeers						= new LinkedList<Peer>();
	/**
	 * Number of uploads performed by this peer.
	 */
	private int								uploads;
	/**
	 * Which pieces have been uploaded. Piece serial or weight (FairE9 only).
	 */
	private MapCounter<Integer>				uploadsPieces					= new MapCounter<Integer>();
	/**
	 * Positive credit, when the other peer uploaded to this.
	 */
	private HashMap<Peer, Integer>			creditsPos						= new HashMap<Peer, Integer>();
	/**
	 * Negative credit, when the other peer downloaded from this.
	 */
	private HashMap<Peer, Integer>			creditsNeg						= new HashMap<Peer, Integer>();
	/**
	 * Round when this peer became seeder.
	 */
	private int								completedRound;

	public Peer(int peerSerial, int downSlotsMu, int downSlotsSigma,
			int upSlotsMu, int upSlotsSigma,
			int downPendingMax, int knownPeersMax, int requestTtl)
	{
		this.serial = peerSerial;
		this.downSlotsMu = downSlotsMu;
		this.downSlotsSigma = downSlotsSigma;
		this.upSlotsMu = upSlotsMu;
		this.upSlotsSigma = upSlotsSigma;
		
		//this.downSlotsMax = downSlotsNum;
		//this.upSlotsMax = upSlotsMax;
		this.downPendingMax = downPendingMax;
		this.knownPeersMax = knownPeersMax;
		this.requestTtl = requestTtl;
		// Just for now, as it may join later
		this.active = true;

		//
		// Pieces
		//
		if (isFairE9())
		{
			initPiecesWeight();
		}
	}

	private void initPiecesWeight()
	{
		// Make it 1-based array
		weightByPiece = new int[Main.piecesNum + 1];
		pieceByWeight = new int[Main.piecesNum + 1];

		if (Main.p2pType == P2pType.FAIRE9BUTTERFLY)
		{
			weightByPiece = Permutation.generatePermutation(Main.piecesNum);
		} else
		{
			// Prepare a random list of weights (piece permutation)
			for (int i = 1; i <= Main.piecesNum; i++)
			{
				// 1-based position, up to i, inclusive
				int pos = rand.nextInt(i) + 1;
				if (pos == i)
				{
					weightByPiece[i] = i;
				} else
				{
					weightByPiece[i] = weightByPiece[pos];
					weightByPiece[pos] = i;
				}
			}
		}

		//
		// [i] is the piece with weight i
		//
		for (int i = 1; i <= Main.piecesNum; i++)
		{
			pieceByWeight[weightByPiece[i]] = i;
		}
	}

	public boolean hasPiece(int piece)
	{
		return downloadedPieces.containsKey(piece);
	}

	/**
	 * @return True if peer already downloaded everything.
	 */
	public boolean isSeeder()
	{
		return completedRound != 0;
	}

	/**
	 * @return True if peer already downloaded at least one piece.
	 */
	public boolean isSource()
	{
		return !downloadedPieces.isEmpty();
	}

	/**
	 * @return True if there is at least one free download slot.
	 */
	public boolean hasFreeDownSlot()
	{
		return downSlotsOccupied < downSlotsMax;
	}

	public void setAsInitialSource()//int initialSourceUpSlotsMu,int initialSourceUpSlotsSigma)
	{
		//upSlotsMu = initialSourceUpSlotsMu;
		//upSlotsSigma = initialSourceUpSlotsSigma;
		//upSlotsMax = initialSourceUpSlotsNum;
		// Set to non-zero so it will be considered as seeder
		completedRound = -1;
		for (int pieceSerial = 1; pieceSerial <= Main.piecesNum; pieceSerial++)
		{
			downloadedPieces.put(pieceSerial, 0);
		}
	}

	public int getSerial()
	{
		return this.serial;
	}

	/**
	 * Add new peer to the known list, only if it was not known before.
	 * 
	 * @return True if peer is new to known-peers list.
	 */
	public boolean addKnownPeer(Peer peer, int roundToFirstUse)
	{
		if (knownPeers.size() >= knownPeersMax)
			return false;

		if (knownPeers.containsKey(peer) || this == peer)
			return false;

		knownPeers.put(peer, roundToFirstUse);
		freePeers.add(peer);

		return true;
	}

	/**
	 * Add a request to pending upload requests. Also add the peer to
	 * known-peers if it was not known before. Override former request from the
	 * same peer, if there was such a request for another piece. There is no
	 * limit on the number of pending upload requests.
	 */
	public void acceptRequest(Peer fromPeer, int pieceSerial, int beforeRound)
	{
		// Check if there is a former request from the same peer
		UpRequest existingRequest = upPending.get(fromPeer);

		// No former request, or former request to another piece
		if (existingRequest == null
				|| existingRequest.pieceSerial != pieceSerial)
		{
			int weight;
			if (Main.p2pType == P2pType.BT)
			{
				weight = 0;
			} else if ((Main.p2pType == P2pType.FAIRE9
					|| Main.p2pType == P2pType.FAIRE9PLUS || Main.p2pType == P2pType.FAIRE9BUTTERFLY)
					&& pieceSerial != 0)
			{
				weight = this.weightByPiece[pieceSerial]
						+ fromPeer.weightByPiece[pieceSerial];
			} else
			{
				weight = beforeRound;
			}
			upPending.put(fromPeer, new UpRequest(fromPeer, pieceSerial,
					weight, beforeRound));
		}

		// Add to known peers
		this.addKnownPeer(fromPeer, beforeRound);
	}

	/**
	 * Free download slots and send download requests, until the pending list is
	 * full.
	 * 
	 * @param beforeRound
	 *            The round when the pieces may be downloaded.
	 */
	public int performSendDownloadRequests(int beforeRound)
	{
		if (this.isSeeder())
			return 0;

		cleanupExpiredDown(beforeRound - requestTtl);

		this.downSlotsOccupied = 0;

		int freeDownPending = downPendingMax - downPending.size();
		if (freeDownPending <= 0)
			return 0;

		if (isFairE9())
			return performSendDownloadRequestsFairE9(beforeRound);

		int result = 0;

		// Free peers have order, so each time it may get others
		Iterator<Peer> it = freePeers.iterator();
		while (it.hasNext() && downPending.size() < downPendingMax)
		{
			Peer peer = it.next();

			int pieceSerial = 0;
			downPending.put(peer, new PendingRequest(pieceSerial, beforeRound));
			peer.acceptRequest(this, pieceSerial, beforeRound);
			Crowd.statRequestsAnyPiece++;

			it.remove();

			result++;
		}

		return result;
	}

	private int performSendDownloadRequestsFairE9(int beforeRound)
	{
		// Always send to the initial source
		if (freePeers.contains(Crowd.initialSource))
		{
			downPending.put(Crowd.initialSource, new PendingRequest(0,
					beforeRound));
			Crowd.initialSource.acceptRequest(this, 0, beforeRound);
			freePeers.remove(Crowd.initialSource);
			Crowd.statRequestsAnyPiece++;
		}

		int result = 0;

		//
		// Prepare list of missing pieces sorted by weight
		//
		LinkedHashMap<Integer, Integer> missing = new LinkedHashMap<Integer, Integer>();
		for (int i = 1; i < pieceByWeight.length; i++)
		{
			int curPiece = pieceByWeight[i];

			if (hasPiece(curPiece))
				continue;

			// Pending requests for this piece
			missing.put(curPiece, 0);
		}

		//
		// Fill with updated count of pending per piece
		//
		for (PendingRequest curRequest : downPending.values())
		{
			int curPiece = curRequest.pieceSerial;
			// Special case of any piece from the initial source
			if (curPiece == 0)
				continue;
			int count = missing.get(curPiece);
			missing.put(curPiece, count + 1);
		}

		int target = Math.max(2, (int) Math.ceil((float) downPendingMax
				/ missing.size()));
		for (Entry<Integer, Integer> entry : missing.entrySet())
		{
			if (downPending.size() >= downPendingMax || freePeers.isEmpty())
				break;

			int count = entry.getValue();
			int toSend = target - count;
			if (toSend > 0)
			{
				// Send several download requests for a specific piece to the
				// best free peers
				int pieceSerial = entry.getKey();
				// System.out.print("Peer " + this.toString() + ", piece " +
				// pieceSerial + ", send " + toSend);
				int sent = sendDownloadRequestsForPieceFairE9(pieceSerial,
						toSend, beforeRound);
				result += sent;
				// System.out.println(": " + sent);
				if (sent < toSend)
					break;
			}

			// Double for the next piece
			// target *= 2;
			// target += 2;
		}

		return result;
	}

	/**
	 * Send multiple requests to several free peers to the same piece. Will be
	 * selected by weight, from low to high.
	 * 
	 * @param pieceSerial
	 *            Piece serial, 1-bsed.
	 * @param toSend
	 *            Number of requests/peers to send.
	 */
	private int sendDownloadRequestsForPieceFairE9(int pieceSerial, int toSend,
			int round)
	{
		int sent = 0;

		while (sent < toSend)
		{
			if (downPending.size() >= downPendingMax)
				return sent;

			if (!sendDownloadRequestForPieceFairE9(pieceSerial, round))
				return sent;

			sent++;
		}

		return sent;
	}

	private boolean sendDownloadRequestForPieceFairE9(int pieceSerial, int round)
	{
		Peer bestPeer = null;
		int lowestPos = Integer.MAX_VALUE;
		for (Peer curPeer : freePeers)
		{
			int curPos = curPeer.weightByPiece[pieceSerial];
			// Position 1 is the minimum
			if (curPos == 1)
			{
				bestPeer = curPeer;
				break;
			}
			if (curPos < lowestPos)
			{
				bestPeer = curPeer;
				lowestPos = curPos;
			}
		}

		// If not even one was found
		if (bestPeer == null)
			return false;

		freePeers.remove(bestPeer);
		downPending.put(bestPeer, new PendingRequest(pieceSerial, round));
		bestPeer.acceptRequest(this, pieceSerial, round);
		Crowd.statRequestsSpecificPiece++;

		return true;
	}

	/**
	 * @return Random active known peer. If the random and its subsequent are
	 *         not active, return null.
	 */
	private Peer getRandomKnownPeer()
	{
		// If there are no known peers, return null that will be handled by the
		// caller
		if (knownPeers.isEmpty())
			return null;

		Random rand = new Random(System.currentTimeMillis());
		int num = rand.nextInt(knownPeers.size());
		Iterator<Peer> it = knownPeers.keySet().iterator();
		while (it.hasNext())
		{
			Peer peer = it.next();
			if (num <= 0)
				return peer;
			num--;
		}
		// This will not happen
		return null;
	}

	/**
	 * Get all the peers known to another peer.
	 * 
	 * @param peerToQuery
	 *            The other peer, to query about the peers known to it.
	 * @param roundToFirstUse
	 *            First round when the newly acquired peers can be used.
	 */
	private int getKnownPeersFrom(Peer peerToQuery, int roundToFirstUse,
			int maxNewPeersToAdd)
	{
		int result = 0;

		for (Peer peerReceived : peerToQuery.knownPeers.keySet())
		{
			if (this.addKnownPeer(peerReceived, roundToFirstUse))
			{
				result++;
				if (result >= maxNewPeersToAdd)
					return result;
			}
		}

		return result;
	}

	public int performPeerExchange(int roundToFirstUse)
	{
		// Skip if already have enough known peers
		if (knownPeers.size() >= knownPeersMax)
			return 0;

		// The random peer may be dead, because this may be known only later
		Peer peer = getRandomKnownPeer();
		if (peer == null)
		{
			peer = Crowd.initialSource;
			addKnownPeer(peer, roundToFirstUse);
		}

		int result = 0;

		// Add to this peer some peers from the other (some may be inactive)
		result += getKnownPeersFrom(peer, roundToFirstUse,
				MAX_PEERS_TO_ADD_ON_EXCHANGE);

		// Add myself to the other side
		if (peer.addKnownPeer(this, roundToFirstUse))
		{
			result++;
		}

		return result;
	}

	public int getKnownPeersCount()
	{
		return knownPeers.size();
	}

	public int getDownloadedPiecesCount()
	{
		return downloadedPieces.size();
	}

	public int getUpPendingCount()
	{
		return upPending.size();
	}

	public int getDownPendingCount()
	{
		return downPending.size();
	}

	/**
	 * Cleanup expired pending downloads, and their matching expired pending
	 * uploads.
	 * 
	 * @param expireRound
	 *            The round that only requests that were sent after it will be
	 *            left for now.
	 */
	private void cleanupExpiredDown(int expireRound)
	{
		Iterator<Entry<Peer, PendingRequest>> it = downPending.entrySet()
				.iterator();
		while (it.hasNext())
		{
			Entry<Peer, PendingRequest> entry = it.next();
			PendingRequest request = entry.getValue();
			// If expired, or we already have the piece
			if (request.round <= expireRound
					|| (request.pieceSerial > 0 && hasPiece(request.pieceSerial)))
			{
				it.remove();

				Peer peer = entry.getKey();
				// Remove from pending up too, which is like doing cleanup there
				peer.upPending.remove(this);

				if (Main.p2pType == P2pType.FAIRE9PLUS
						&& request.round <= expireRound)
				{
					// Don't use again, to fight free-riders
					knownPeers.remove(peer);
				} else
				{
					// Put the peer back into free-peers
					freePeers.add(peer);
				}
			}
		}
	}

	/**
	 * Perform uploads, by going over the pending uploads and picking the
	 * relevant requests and random pieces that we have and the other does not.
	 * If more free slots are available after the first round, than it tries
	 * over and over again until the list or slots are exhausted.
	 * 
	 * @param round
	 *            Uploads round.
	 * @param print
	 *            If to print detailed list of all the uploads.
	 * @return Number of uploads performed.
	 */
	public int performUpload(int round, boolean print)
	{
		if (isInitialSource())
			return performUploadFromInitialSource(round, print);

		LinkedList<UpRequest> requests = this.getRelevantUpRequests();
		if (requests == null)
			return 0;

		int freeSlots = upSlotsMax;

		int result = 0;
		boolean firstRound = true;

		// As long as there are free slots and pending requests
		while (freeSlots > 0)
		{
			// Iterate over and over again, until no more free slots, or can't
			// find someone to upload to
			Iterator<UpRequest> it = requests.iterator();
			boolean uploaded = false;
			while (freeSlots > 0 && it.hasNext())
			{
				//
				// Process the request
				//
				UpRequest request = it.next();
				Peer peer = request.peer;

				// Check if the requester has free download slots now
				if (!peer.hasFreeDownSlot())
					continue;

				// Random piece (0) or a specific piece
				int pieceSerial = request.pieceSerial;
				if (pieceSerial == 0 || !firstRound)
				{
					pieceSerial = getPieceOtherMissing(peer, round);
					// If did not find such a piece
					if (pieceSerial == 0)
						continue;
				} else
				{
					// Will not get to this line in case the
					// uploader has more free slots
					Integer downloadRound = downloadedPieces.get(pieceSerial);
					// If we do not have the piece
					if (downloadRound == null)
						continue;
					// If the piece was downloaded in this round (in progress)
					if (downloadRound == round)
						continue;
					// Check if specific piece that the other already got from
					// somewhere else
					if (peer.hasPiece(pieceSerial))
						continue;
				}

				freeSlots--;

				//
				// Other peer
				//
				peer.performDownload(this, pieceSerial, round);

				if (Main.p2pType == P2pType.EMULE || Main.p2pType == P2pType.BT)
				{
					// Add to known, so we can use the credit
					if (addKnownPeer(peer, round))
					{
						freePeers.add(0, peer);
					}
					// If in free peers, then put it first for use next time
					else if (freePeers.remove(peer))
					{
						freePeers.add(0, peer);
					}
				}

				uploaded = true;
				result++;

				if (Main.p2pType == P2pType.FAIRE9
						|| Main.p2pType == P2pType.FAIRE9PLUS
						|| Main.p2pType == P2pType.FAIRE9BUTTERFLY)
				{
					this.uploadsPieces.inc(weightByPiece[pieceSerial]);
				} else
				{
					this.uploadsPieces.inc(pieceSerial);
				}

				if (print)
				{
					System.out.println("    n" + peer.serial + " <= n"
							+ this.serial + " (" + pieceSerial + ")");
				}
			}

			if (firstRound)
			{
				// If not by weight, and nothing was uploaded, then quit
				if (!uploaded && !isFairE9())
					break;
				firstRound = false;
			}
			// If a walk over the pending list did not result in even one upload
			// this time, then do not try again
			else if (!uploaded)
				break;
		}

		// Update uploads counter
		this.uploads += result;

		return result;
	}

	private void performDownload(Peer fromPeer, int pieceSerial, int round)
	{
		downSlotsOccupied++;
		// Piece
		downloadedPieces.put(pieceSerial, round);
		// Check if seeder
		if (downloadedPieces.size() == Main.piecesNum)
		{
			this.completedRound = round;
			removeAllFreeAndDownPending();
		}
		// Peers
		freePeers.add(fromPeer);
		downPending.remove(fromPeer);
		// Credit in the other side
		if (Main.p2pType == P2pType.EMULE || Main.p2pType == P2pType.BT)
		{
			addCreditPos(fromPeer);
			fromPeer.addCreditNeg(this);
		}
	}

	/**
	 * To be called when a peer become seeder.
	 */
	private void removeAllFreeAndDownPending()
	{
		freePeers.clear();
		// Clear the upload pending of the other side
		for (Peer curPeer : downPending.keySet())
		{
			curPeer.upPending.remove(this);
		}
		// Clear the down pending
		downPending.clear();
	}

	/**
	 * @return Sorted list of upload requests by weight (FairE9), time and
	 *         credit (eMule) or completely random (Random).
	 */
	public LinkedList<UpRequest> getRelevantUpRequests()
	{
		// If no pieces or no pending requests
		if (downloadedPieces.isEmpty() || upPending.isEmpty())
			return null;

		LinkedList<UpRequest> result = new LinkedList<UpRequest>();

		Iterator<UpRequest> it = upPending.values().iterator();
		while (it.hasNext())
		{
			UpRequest curRequest = it.next();

			// Check if the requester has free download slots now
			if (!curRequest.peer.hasFreeDownSlot())
				continue;

			// Check if the requester has pending download from this
			if (!curRequest.peer.downPending.containsKey(this))
			{
				it.remove();
				continue;
			}

			// For emule set the weights now
			if (Main.p2pType == P2pType.EMULE)
			{
				Integer curCredit = creditsPos.get(curRequest.peer);
				if (curCredit != null)
				{
					// 60 seconds for each uploaded piece
					curRequest.weight = curRequest.beforeRound - 3 * curCredit
							/ 10;
				}
			} else if (Main.p2pType == P2pType.BT)
			{
				Integer curCreditPos = creditsPos.get(curRequest.peer);
				if (curCreditPos == null)
				{
					curRequest.weight = 0;
				} else
				{
					Integer curCreditNeg = creditsNeg.get(curRequest.peer);
					if (curCreditNeg == null)
					{
						curRequest.weight = -curCreditPos;
					} else
					{
						curRequest.weight = -curCreditPos * 10 / curCreditNeg;
					}
				}
			}

			result.add(curRequest);
		}

		if (result.size() > 1)
		{
			// If random that make that a random list
			if (Main.p2pType == P2pType.RANDOM)
			{
				Collections.shuffle(result);
			} else
			{
				Collections.sort(result);
			}
		}

		return result;
	}

	/**
	 * @return Piece that I have before the given round, and the other side do
	 *         not have. Zero if no such piece was found.
	 */
	private int getPieceOtherMissing(Peer downloadingPeer, int round)
	{
		Crowd.statBitmapExchange++;

		// RANDOM or EMULE
		if (Main.p2pType != P2pType.FAIRE9
				&& Main.p2pType != P2pType.FAIRE9PLUS
				&& Main.p2pType != P2pType.FAIRE9BUTTERFLY)
			return getPieceToUploadRandom(downloadingPeer, round);

		// FAIRE9
		return getPieceToUploadFairE9(downloadingPeer, round);
	}

	private int getPieceToUploadRandom(Peer downloadingPeer, int round)
	{
		// Prepare array for pieces the uploader has and the downloader do not
		ArrayList<Integer> candidatePieces = new ArrayList<Integer>(
				downloadedPieces.size());

		Iterator<Entry<Integer, Integer>> it = downloadedPieces.entrySet()
				.iterator();
		while (it.hasNext())
		{
			Entry<Integer, Integer> entry = it.next();
			int pieceSerial = entry.getKey();

			// If the other side already has it
			if (downloadingPeer.hasPiece(pieceSerial))
				continue;

			// If the piece was downloaded in this round (in progress)
			if (entry.getValue() == round)
				continue;

			candidatePieces.add(pieceSerial);
		}

		// If there is not even one such piece
		if (candidatePieces.isEmpty())
			return 0;

		// Get a random piece
		return candidatePieces.get(rand.nextInt(candidatePieces.size()));
	}

	/**
	 * Get random piece to upload from regular source to a downloader that takes
	 * advantage of a clear slot and gets the chance to download what it wants.
	 * 
	 * @return Zero or the lightest missing piece that this peer has.
	 */
	private int getPieceToUploadFairE9(Peer downloadingPeer, int round)
	{
		int bestPiece = 0;
		int bestWeight = Integer.MAX_VALUE;

		Iterator<Entry<Integer, Integer>> it = downloadedPieces.entrySet()
				.iterator();
		while (it.hasNext())
		{
			Entry<Integer, Integer> entry = it.next();
			int pieceSerial = entry.getKey();

			// If the other side already has it
			if (downloadingPeer.hasPiece(pieceSerial))
				continue;

			// If the piece was downloaded in this round (in progress)
			if (entry.getValue() == round)
				continue;

			int curWeight = downloadingPeer.weightByPiece[pieceSerial];
			if (curWeight < bestWeight)
			{
				bestPiece = pieceSerial;
				bestWeight = curWeight;
			}
		}

		return bestPiece;
	}

	/**
	 * Upload from the initial source.
	 * 
	 * @return Number of uploads performed.
	 */
	private int performUploadFromInitialSource(int round, boolean print)
	{
		int freeSlots = upSlotsMax;

		int result = 0;
		int piecesLeftToCheck = downloadedPieces.size();

		// As long as there are free slots and pending requests
		while (freeSlots > 0 && !upPending.isEmpty() && piecesLeftToCheck > 0)
		{
			piecesLeftToCheck--;
			// Get only the key, because the value is the round which is always
			// zero
			int pieceSerial = downloadedPieces.keySet().iterator().next();
			// Rotate the list for the next piece
			downloadedPieces.remove(pieceSerial);
			downloadedPieces.put(pieceSerial, 0);
			// Get target peer
			Peer peer = getPeerForUploadFromInitialSource(pieceSerial);
			// This cannot happen forever, since pending requesters always need
			// something
			if (peer == null)
				continue;
			// Rotate the pending, for random/emule
			// upPending.put(peer, upPending.remove(peer));
			upPending.remove(peer);
			// The other side
			peer.performDownload(this, pieceSerial, round);
			// No need to count uploads for the initial source
			result++;
			freeSlots--;
			// Print for debug
			if (print)
			{
				System.out.println("    n" + peer.serial + " <= s ("
						+ pieceSerial + ")");
			}
		}

		return result;
	}

	/**
	 * Get peer to upload a specific piece from the initial source.
	 * 
	 * @return Null if none of the requesting peers needs this piece.
	 */
	private Peer getPeerForUploadFromInitialSource(int piece)
	{
		Peer bestPeer = null;
		int lowestWeight = Integer.MAX_VALUE;

		// Look for the best request for this piece
		for (Peer curPeer : upPending.keySet())
		{
			// Skip peers that already have this piece
			if (curPeer.hasPiece(piece))
				continue;

			// Skip peers that are too busy in this round
			if (!curPeer.hasFreeDownSlot())
				continue;

			// With random systems just return the first
			if (Main.p2pType != P2pType.FAIRE9
					&& Main.p2pType != P2pType.FAIRE9PLUS
					&& Main.p2pType != P2pType.FAIRE9BUTTERFLY)
				return curPeer;

			int curWeight = curPeer.weightByPiece[piece];
			if (curWeight == 1)
				return curPeer;

			if (curWeight < lowestWeight)
			{
				bestPeer = curPeer;
				lowestWeight = curWeight;
			}
		}

		return bestPeer;
	}

	@Override
	public String toString()
	{
		return Integer.toString(this.serial);
	}

	public void printDetails()
	{
		//
		// Pieces
		//
		System.out.print("   Pieces: ");
		for (int i = 1; i <= Main.piecesNum; i++)
		{
			System.out.print(" " + i + (this.hasPiece(i) ? "*" : ""));
		}
		System.out.println();

		//
		// Known peers
		//
		System.out.print("   Known: ");
		TreeSet<Integer> sortedList = new TreeSet<Integer>();
		for (Peer peer : knownPeers.keySet())
		{
			sortedList.add(peer.serial);
		}
		System.out.println(sortedList.toString());

		//
		// Free peers
		//
		System.out.print("   Free peers: ");
		sortedList = new TreeSet<Integer>();
		for (Peer peer : freePeers)
		{
			sortedList.add(peer.serial);
		}
		System.out.println(sortedList.toString());

		//
		// Upload pending
		//
		System.out.print("   Up pending: ");
		sortedList = new TreeSet<Integer>();
		for (Peer peer : upPending.keySet())
		{
			sortedList.add(peer.serial);
		}
		System.out.println(sortedList.toString());

		//
		// Download pending
		//
		System.out.print("   Down pending: ");
		sortedList = new TreeSet<Integer>();
		for (Peer peer : downPending.keySet())
		{
			sortedList.add(peer.serial);
		}
		System.out.println(sortedList.toString());
	}

	public boolean isActive()
	{
		return this.active;
	}

	public void setActive(boolean active)
	{
		this.active = active;
	}

	/**
	 * @return True if this is the initial source.
	 */
	public boolean isInitialSource()
	{
		return serial == 1;
	}

	/**
	 * @return Latency, which is the round in which the peer completed. Zero if
	 *         initial source or did not complete.
	 */
	public int getLatency()
	{
		if (!isSeeder() || isInitialSource())
			return 0;

		return completedRound - startRound + 1;
	}

	public int getKnownPeersMax()
	{
		return this.knownPeersMax;
	}

	public void setKnownPeersMax(int knownPeersMax)
	{
		this.knownPeersMax = knownPeersMax;
	}

	public void setDownPendingMax(int downPendingMax)
	{
		this.downPendingMax = downPendingMax;
	}

	public int getPendingMax()
	{
		return this.downPendingMax;
	}

	public int getUploads()
	{
		return uploads;
	}

	private boolean isFairE9()
	{
		return (Main.p2pType == P2pType.FAIRE9
				|| Main.p2pType == P2pType.FAIRE9PLUS || Main.p2pType == P2pType.FAIRE9BUTTERFLY);
	}

	public int getUploadsDistinctPieces()
	{
		return uploadsPieces.size();
	}

	public void addCreditPos(Peer toPeer)
	{
		Integer curCredit = creditsPos.get(toPeer);
		creditsPos.put(toPeer, curCredit == null ? 10 : curCredit + 10);
	}

	public void addCreditNeg(Peer toPeer)
	{
		Integer curCredit = creditsNeg.get(toPeer);
		creditsNeg.put(toPeer, curCredit == null ? 10 : curCredit + 10);
	}

	public void cleanupLeavingPeer(Peer leavingPeer)
	{
		downPending.remove(leavingPeer);
		freePeers.remove(leavingPeer);
		upPending.remove(leavingPeer);
		knownPeers.remove(leavingPeer);
		creditsPos.remove(leavingPeer);
		creditsNeg.remove(leavingPeer);
	}

	public MapCounter<Integer> getUploadsCount()
	{
		return uploadsPieces;
	}

	public int getValue(SpecialType specialType)
	{
		switch (specialType)
		{
		case PENDING_AND_KNOWN:
			return this.downPendingMax;
		case FREE_RIDERS:
		case UP_SLOTS:
			return this.upSlotsMax;
		case NEW_COMERS:
			return this.startRound;
		case DOWN_SLOTS:
			return this.downSlotsMax;
		default:
			// Should not happen
			return 0;
		}
	}

	public void setValue(SpecialType specialType, int newValue)
	{
		switch (specialType)
		{
		case PENDING_AND_KNOWN:
			this.downPendingMax = newValue;
			return;
		case FREE_RIDERS:
			this.upSlotsMax = 0;
			return;
		case UP_SLOTS:
			this.upSlotsMax = newValue;
			return;
		case NEW_COMERS:
			this.startRound = newValue;
			return;
		case DOWN_SLOTS:
			this.downSlotsMax = newValue;
			return;
		}
	}

	public int getStartRound()
	{
		return startRound;
	}

	public void setStartRound(int startRound)
	{
		this.startRound = startRound;
	}

	public void resetBandwidths() {
		downSlotsMax = Gaussian.nextGaussian(downSlotsMu, downSlotsSigma);
		upSlotsMax = Gaussian.nextGaussian(upSlotsMu, upSlotsSigma);
		//System.out.println("down " + downSlotsMax + " up " + upSlotsMax);
	}
}

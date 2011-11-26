/*
 * Eyal Zohar, The Open University of Israel, 2008
 */
package il.ac.openu.eyalzo.sim3;

/**
 * Pending download request, sent to another peer, held in the pending request
 * list of the sending peer.
 * 
 * @author Eyal Zohar
 */
public class PendingRequest implements Comparable<PendingRequest>
{
    /**
     * 1-based serial of round (piece-time) when the request was sent (before
     * the round interval).
     */
    int round;
    /**
     * Serial number of piece. Relevant to FairE9 only.
     */
    int pieceSerial;
    /**
     * Weight of the request sent, according to system.
     */
    int weight;

    /**
     * @param pieceSerial
     *                Serial number of piece. Relevant to FairE9 only.
     * @param beforeRound
     *                1-based serial of round (piece-time) when the request was
     *                sent (before the round interval).
     */
    public PendingRequest(int pieceSerial, int beforeRound)
    {
	this.pieceSerial = pieceSerial;
	this.round = beforeRound;
    }

    public int compareTo(PendingRequest o)
    {
	return ((Integer) this.weight).compareTo(o.weight);
    }
}

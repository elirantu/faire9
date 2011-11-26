/*
 * Eyal Zohar, The Open University of Israel, 2008
 */
package il.ac.openu.eyalzo.sim3;

/**
 * Request to upload from this peer. Each request has a peer and optionally a
 * piece (depend on system). It also has a weight to make it suitable for sort
 * by the uploading peer.
 * 
 * @author Eyal Zohar
 */
public class UpRequest implements Comparable<UpRequest>
{
    /**
     * The requesting peer.
     */
    Peer peer;
    /**
     * Piece he asked for. Relevant for FairE9 only, as the other systems do not
     * specify piece number.
     */
    int pieceSerial;
    /**
     * 1-based round (piece-time) number that is the first when the request is
     * considered.
     */
    int beforeRound;
    /**
     * Weight of request, according to the P2P system in use.
     */
    int weight;

    public UpRequest(Peer peer, int pieceSerial, int weight, int beforeRound)
    {
	this.peer = peer;
	this.pieceSerial = pieceSerial;
	this.beforeRound = beforeRound;
	this.weight = weight;
    }

    /**
     * For sort by weight, ascending.
     */
    public int compareTo(UpRequest o)
    {
	return ((Integer) this.weight).compareTo(o.weight);
    }

    /**
     * Serial number of peer, serial of piece and weight of request.
     */
    @Override
    public String toString()
    {
	return peer.getSerial() + "-" + pieceSerial + "-" + weight;
    }
}

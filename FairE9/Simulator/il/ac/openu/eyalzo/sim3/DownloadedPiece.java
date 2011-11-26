/*
 * Eyal Zohar, The Open University of Israel, 2008
 */
package il.ac.openu.eyalzo.sim3;

/**
 * @author Eyal Zohar
 */
public class DownloadedPiece extends Piece
{
    /**
     * 1-based round (piece-time) when the piece was downloaded.
     */
    private int downloadRound;

    /**
     * @param serial
     *                1-based serial number of piece.
     * @param downloadRound
     *                1-based round (piece-time) when the piece was downloaded.
     */
    public DownloadedPiece(int pieceSerial, int downloadRound)
    {
	super(pieceSerial);
	this.downloadRound = downloadRound;
    }

    /**
     * @return 1-based round (piece-time) when the piece was downloaded.
     */
    public int getDownloadRound()
    {
	return this.downloadRound;
    }
}

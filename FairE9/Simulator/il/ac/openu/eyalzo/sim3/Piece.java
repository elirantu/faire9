/*
 * Eyal Zohar, The Open University of Israel, 2008
 */
package il.ac.openu.eyalzo.sim3;

/**
 * Piece object is merely a wrap around the serial integer. It is intended for
 * more elegant and clear code.
 * 
 * @author Eyal Zohar
 */
public class Piece
{
    protected int serial;

    /**
     * @param serial
     *                1-based serial number of the piece.
     */
    public Piece(int serial)
    {
	this.serial = serial;
    }

    /**
     * @return 1-based serial number of the piece.
     */
    public int getSerial()
    {
	return this.serial;
    }

    @Override
    public boolean equals(Object obj)
    {
	return this.serial == ((Piece) obj).serial;
    }
}

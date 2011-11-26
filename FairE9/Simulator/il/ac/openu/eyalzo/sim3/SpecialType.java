/*
 * Eyal Zohar, The Open University of Israel, 2008
 */
package il.ac.openu.eyalzo.sim3;

public enum SpecialType
{
    /**
     * Aggressiveness by multiplying the number of maximum known peers and
     * maximum pending requests.
     */
    PENDING_AND_KNOWN,
    /**
     * Incentives to share by multiplying the number of upload slots, meaning
     * the upload bandwidth.
     */
    UP_SLOTS, DOWN_SLOTS,
    /**
     * Setting a large group of peers as free-riders.
     */
    FREE_RIDERS,
    /**
     * Some peers become active later than others.
     */
    NEW_COMERS;
}

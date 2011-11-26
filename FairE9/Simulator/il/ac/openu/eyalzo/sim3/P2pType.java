/*
 * Eyal Zohar, The Open University of Israel, 2008
 */
package il.ac.openu.eyalzo.sim3;

/**
 * Different P2P systems. Each may have a different strategy and algorithms.
 * 
 * @author Eyal
 */
public enum P2pType {
	/**
	 * Random selection of peers and pieces.
	 */
	RANDOM,
	/**
	 * FairE9 according to the protocol specifications but without cleanup of
	 * known peers that have timeouts for pending requests.
	 */
	FAIRE9,
	/**
	 * FairE9 according to the protocol specifications.
	 */
	FAIRE9PLUS,
	/**
	 * FairE9 according to the protocol specifications, but with butterfly
	 * permutation.
	 */
	FAIRE9BUTTERFLY,
	/**
	 * eMule style.
	 */
	EMULE,
	/**
	 * BT style.
	 */
	BT;
}

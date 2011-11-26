package il.ac.openu.eyalzo.sim3;

import java.util.LinkedList;
import java.util.Random;

public class Permutation
{
	private static Random	rand	= new Random(System.currentTimeMillis());

	/**
	 * @param piecesNum
	 * @return 1-based array, meaning that the first element at index 0 should
	 *         be skipped.
	 */
	public static int[] generatePermutation(int piecesNum)
	{
		long maxPeerId = (long) (Math.pow(2, piecesNum));
		long peerId = rand.nextLong() % maxPeerId;
		return generatePermutation(piecesNum, peerId);
	}

	/**
	 * @param piecesNum
	 * @param peerId
	 * @return 1-based array, meaning that the first element at index 0 should
	 *         be skipped.
	 */
	public static int[] generatePermutation(int piecesNum, long peerId)
	{
		// Make it 1-based
		int[] result = new int[piecesNum + 1];
		LinkedList<Integer> list = new LinkedList<Integer>();
		// Occupy the first cell, to make the array 1-based
		list.add(0);
		for (int i = 1; i <= piecesNum; i++)
		{
			list.add(i);
		}

		// Generate the permutation with a list
		listPermutation(list, peerId);

		// The array is 1-based
		for (int i = 1; i <= piecesNum; i++)
		{
			result[i] = list.get(i);
		}

		return result;
	}

	/**
	 * @param list
	 *            1-based list, meaning that it has one extra (first) cell in
	 *            index 0.
	 * @param peerId
	 */
	private static void listPermutation(LinkedList<Integer> list, long peerId)
	{
		listPermutation(list, 1, list.size() - 1, peerId);
	}

	/**
	 * @param list
	 *            1-based list, meaning that it has one extra (first) cell in
	 *            index 0.
	 * @param from
	 *            1-based.
	 * @param to
	 *            1-based.
	 */
	private static long listPermutation(LinkedList<Integer> list, int from,
			int to, long peerIdReminder)
	{
		if (to <= from)
			return peerIdReminder;

		//
		// Use the LSB
		//
		boolean evenLeft = (peerIdReminder & 0x00000001L) == 0;
		long nextPeerIdReminder = (peerIdReminder >> 1);

		//
		// Rearrange according to random
		//
		int mid = from + (to - from) / 2;
		if (evenLeft)
		{
			// The first element is already in place
			int loops = (to - from) / 2;
			for (int i = 1; i <= loops; i++)
			{
				int evenPos = from + i * 2;
				// Position is 0-based
				int evenElement = list.remove(evenPos);
				list.add(from + i, evenElement);
			}
		} else
		{
			int loops = (to - from + 1) / 2;
			// Here we need also to move the first element
			for (int i = 1; i <= loops; i++)
			{
				int oddPos = from + i * 2 - 1;
				// Position is 0-based
				int evenElement = list.remove(oddPos);
				list.add(from + i - 1, evenElement);
			}
		}

		//
		// Recursive calls
		//

		// Recursive call for the left part
		nextPeerIdReminder = listPermutation(list, from, mid,
				nextPeerIdReminder);
		// Recursive call for the right part
		nextPeerIdReminder = listPermutation(list, mid + 1, to,
				nextPeerIdReminder);

		return nextPeerIdReminder;
	}

	public static void printPermutation(int[] permutation)
	{
		boolean first = true;
		for (int i : permutation)
		{
			if (first)
			{
				first = false;
				continue;
			}
			System.out.print(i);
			System.out.print(" ");
		}
		System.out.println();
	}
}

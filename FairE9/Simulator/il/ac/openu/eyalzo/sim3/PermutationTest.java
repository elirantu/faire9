package il.ac.openu.eyalzo.sim3;

import org.junit.Test;

public class PermutationTest
{
	@Test
	public void testGeneratePermutation()
	{
		//
		// All options for X pieces
		//
		//runTest(3);
		runTest(4);
		//runTest(8);
	}

	private void runTest(int piecesNum)
	{
		int maxPeerId = (int) Math.pow(2, piecesNum - 1);
		for (int i = 0; i < maxPeerId; i++)
		{
			int[] result = Permutation.generatePermutation(piecesNum, i);
			System.out.print(i);
			System.out.print(": ");
			Permutation.printPermutation(result);
		}
	}
}

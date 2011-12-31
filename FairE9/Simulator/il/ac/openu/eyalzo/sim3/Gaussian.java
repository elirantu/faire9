package il.ac.openu.eyalzo.sim3;

import java.util.Random;

public class Gaussian {

	static int nextGaussian(int mu,int sigma) {
		Random rand = new Random();
		double n = rand.nextGaussian();
		n *= Math.sqrt(sigma);
		n += mu;
		return (int)n;
	}
}

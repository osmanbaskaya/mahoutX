package com.eniyitavsiye.mahoutx.svdextension.online;

import java.util.Collection;
import org.apache.mahout.cf.taste.common.Refreshable;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.impl.recommender.svd.Factorization;
import org.apache.mahout.cf.taste.recommender.Recommender;

public class StochasticGradientDescentUpdater implements UserFactorUpdater {

	public static final int DEFAULT_ITERATION_COUNT = 1;
	
	private double gamma;
	private double lambda;
	private int iterationCount;
	private Recommender recommender;
	
	public StochasticGradientDescentUpdater(Recommender recommender, double gamma, 
					double lambda, int iterationCount) {
		this.recommender = recommender;
		this.gamma = gamma;
		this.lambda = lambda;
		this.iterationCount = iterationCount;
	}
	
	public StochasticGradientDescentUpdater(Recommender recommender, double gamma, double lambda) {
		this(recommender, gamma, lambda, DEFAULT_ITERATION_COUNT);
	}
	
	@Override
	public void updateUserFactor(double[] userFactors,
			Factorization availableFactorization, long userID, long itemID,
			float rating) throws TasteException {
		
		double[] itemFactors = availableFactorization.getItemFeatures(itemID);
		
		for (int iter = 0; iter < iterationCount; ++iter) {
			double error = rating - recommender.estimatePreference(userID, itemID);
			for (int i = 0; i < userFactors.length; i++) {
				userFactors[i] += gamma * (error * itemFactors[i] - lambda * userFactors[i]);
			}
		}
		
	}
	
	@Override
	public void refresh(Collection<Refreshable> alreadyRefreshed) {
		// nothing to do here 
	}

}

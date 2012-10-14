package com.eniyitavsiye.svdextension;

import java.util.Collection;

import org.apache.mahout.cf.taste.common.Refreshable;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.impl.recommender.svd.Factorization;

public class StochasticGradientDescentUpdater implements UserFactorUpdater {
	
	private double gamma;
	private double lambda;
	private int iterationCount;
	
	public StochasticGradientDescentUpdater(double gamma, double lambda, int iterationCount) {
		this.gamma = gamma;
		this.lambda = lambda;
		this.iterationCount = iterationCount;
	}
	
	public StochasticGradientDescentUpdater(double gamma, double lambda) {
		this(gamma, lambda, 1);
	}
	
	@Override
	public void updateUserFactor(double[] userFactors,
			Factorization availableFactorization, long userID, long itemID,
			float rating, double error) throws TasteException {
		
		double[] itemFactors = availableFactorization.getItemFeatures(itemID);
		
		for (int iter = 0; iter < iterationCount; ++iter) {
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

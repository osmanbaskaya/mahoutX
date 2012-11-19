package com.eniyitavsiye.mahoutx.svdextension;

import org.apache.mahout.cf.taste.common.Refreshable;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.impl.recommender.svd.Factorization;

public interface UserFactorUpdater extends Refreshable {

	// TODO change error from double to a double => double function
	void updateUserFactor(double[] userFactors, Factorization availableFactorization, long userID, long itemID, float rating, double error) throws TasteException;
	
}

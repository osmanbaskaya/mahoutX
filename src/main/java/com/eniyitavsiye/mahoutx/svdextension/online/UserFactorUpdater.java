package com.eniyitavsiye.mahoutx.svdextension.online;

import org.apache.mahout.cf.taste.common.Refreshable;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.impl.recommender.svd.Factorization;

public interface UserFactorUpdater extends Refreshable {

	void updateUserFactor(double[] userFactors, Factorization availableFactorization, long userID, long itemID, float rating) throws TasteException;
	
}

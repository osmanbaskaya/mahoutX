package com.eniyitavsiye.mahoutx.svdextension.online;

import com.eniyitavsiye.mahoutx.svdextension.FactorizationCachingFactorizer;
import java.io.File;
import org.apache.commons.math.linear.ArrayRealVector;
import org.apache.commons.math.linear.OpenMapRealVector;
import org.apache.commons.math.linear.RealMatrix;
import org.apache.commons.math.linear.RealVector;
import org.apache.commons.math.linear.SparseRealVector;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.example.grouplens.GroupLensDataModel;
import org.apache.mahout.cf.taste.example.kddcup.track1.svd.ParallelArraysSGDFactorizer;
import org.apache.mahout.cf.taste.impl.common.FastByIDMap;
import org.apache.mahout.cf.taste.impl.recommender.svd.Factorization;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.model.Preference;
import org.apache.mahout.cf.taste.model.PreferenceArray;
import org.apache.mahout.common.distance.CosineDistanceMeasure;

public class OnlineSVDRecommenderTest {

	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception {
		DataModel model = new GroupLensDataModel(new File("/home/ceyhun/Dropbox/Projects/doctoral/dataset/MovieLens/1m/ratings.dat"));

		FactorizationCachingFactorizer cachingFactorizer = 
						new FactorizationCachingFactorizer(
						new ParallelArraysSGDFactorizer(model, 10, 20));

		OnlineSVDRecommender recommender = new OnlineSVDRecommender(model, cachingFactorizer, true);
		Factorization fact = cachingFactorizer.getCachedFactorization();

		RealMatrix vk = recommender.vk;
		RealMatrix vTransposeRightInverse = recommender.vTransposeRightInverse;
		FastByIDMap<Integer> itemOrder = recommender.itemOrder;

		double defaultRating = 0;
		
		for (long userID = 1; userID <= 10; ++userID) {
			System.out.println("");
			System.out.println("==================================================");
			System.out.println("==================================================");
			System.out.println("User " + userID);
		
			RealVector trainedUserFeatures = new ArrayRealVector(fact.getUserFeatures(userID));

			RealVector availableUserRatings = availableUserRatings(userID, model, itemOrder, defaultRating);
			RealVector artificialRatings = inferUserRatings(trainedUserFeatures, vk);

			System.out.println("user rating count : " + model.getPreferencesFromUser(userID).length());
			System.out.println("");
			System.out.println("");
			System.out.println("");

			RealVector inferFeaturesFromArtificialRatings = vTransposeRightInverse.preMultiply(artificialRatings);
			RealVector inferFeaturesFromAvailableRatings = vTransposeRightInverse.preMultiply(availableUserRatings);

			System.out.println("trainedUserFeatures tf: \n" + trainedUserFeatures);
			System.out.println("");
			System.out.println("");

			System.out.println("inferred features from artificial ratings af: \n" + inferFeaturesFromArtificialRatings);
			System.out.println("");
			System.out.println("");

			System.out.println("inferred features from available ratings rf: \n" + inferFeaturesFromAvailableRatings);
			System.out.println("");
			System.out.println("");

			System.out.println("dist between tf af : " + distStat(trainedUserFeatures, inferFeaturesFromArtificialRatings));
			System.out.println("dist between tf rf : " + distStat(trainedUserFeatures, inferFeaturesFromAvailableRatings));
			System.out.println("dist between af rf : " + distStat(inferFeaturesFromArtificialRatings, inferFeaturesFromAvailableRatings));
		}
	}

	private static RealVector availableUserRatings(long userId, DataModel model, 
					FastByIDMap<Integer> itemOrder, double defaultVal) 
					throws TasteException	{
		PreferenceArray preferences = model.getPreferencesFromUser(userId);
		SparseRealVector r = new OpenMapRealVector(model.getNumItems(), preferences.length());
		r.set(defaultVal);
		for (Preference p : preferences) {
			r.setEntry(itemOrder.get(p.getItemID()), p.getValue());
		}
		return r;
	}

	private static RealVector inferUserRatings(RealVector uk_u, RealMatrix vk) {
		return vk.transpose().preMultiply(uk_u);
	}

	private static String distStat(RealVector v1, RealVector v2) {
		return "cos: " + CosineDistanceMeasure.distance(v1.getData(), v2.getData()) + ", euc:" + v1.getDistance(v2);
	}

}

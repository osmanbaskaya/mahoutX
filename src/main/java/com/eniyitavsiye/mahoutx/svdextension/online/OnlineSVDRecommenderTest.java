package com.eniyitavsiye.mahoutx.svdextension.online;

import java.io.File;
import java.util.ArrayList;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.mahout.cf.taste.common.Refreshable;
import org.apache.mahout.cf.taste.example.grouplens.GroupLensDataModel;
import org.apache.mahout.cf.taste.example.kddcup.track1.svd.ParallelArraysSGDFactorizer;
import org.apache.mahout.cf.taste.impl.recommender.svd.Factorizer;
import org.apache.mahout.common.RandomUtils;

public class OnlineSVDRecommenderTest {

	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception {

		Logger log = Logger.getLogger(OnlineSVDRecommender.class.getName());

		GroupLensDataModel model = new GroupLensDataModel(new File("/home/ceyhun/Dropbox/Projects/doctoral/dataset/1m/ratings.dat"));

		//long userID = model.getUserIDs().next();
		long userID = 8000; // new user!
		
		
		log.log(Level.INFO, "First user is {0}", userID);
		//log.info("Following items to be added: " + Arrays.toString(nonRatedItems));
		
		/*
		float val = model.getPreferenceValue(userID, itemID);
		log.info("value is " + val);
		System.exit(0);
		*/
		
		Factorizer factorizer = new ParallelArraysSGDFactorizer(model, 10, 15);
  	RandomUtils.useTestSeed();
		UserFactorUpdater userFactorUpdater = new StochasticGradientDescentUpdater(
				0.05, 0.02);
		
		OnlineSVDRecommender onlineSVDRecommender = new OnlineSVDRecommender(
				model, factorizer, userFactorUpdater);
		 onlineSVDRecommender.refresh(new ArrayList<Refreshable>());

		log.log(Level.INFO, "User {0} rated {1} items.\n", new Object[]{userID, model.getItemIDsFromUser(userID).size()});
		
		log.log(Level.INFO, "Before adding :{0}", onlineSVDRecommender.recommend(userID, 10));
		
		Random r = new Random();
		for (long itemID = 8; itemID < 18; ++itemID) {
			float rat = 5;//r.nextInt(5)+1;
			onlineSVDRecommender.addPreference(userID, itemID, rat);
			log.log(Level.INFO, "After adding {0} to {1}:{2}\n", new Object[]{rat, itemID, onlineSVDRecommender.recommend(userID, 10)});
		}
		
		//add manually while debugging
		onlineSVDRecommender.refresh(new ArrayList<Refreshable>());
	  log.log(Level.INFO, "After refreshing (offline) :{0}\n", onlineSVDRecommender.recommend(userID, 10));
		
		
		//[RecommendedItem[item:682, value:12.642227], RecommendedItem[item:2632, value:11.289652], RecommendedItem[item:1369, value:10.984646], RecommendedItem[item:167, value:10.554416], RecommendedItem[item:2557, value:10.230662], RecommendedItem[item:1519, value:9.688026], RecommendedItem[item:3905, value:9.599363], RecommendedItem[item:2192, value:9.453616], RecommendedItem[item:1796, value:9.429391], RecommendedItem[item:3085, value:9.414218]]
		//
	}

}

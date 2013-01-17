package com.eniyitavsiye.mahoutx.svdextension.online;

import com.eniyitavsiye.mahoutx.svdextension.FactorizationCachingFactorizer;
import java.util.Collection;
import java.util.List;
import org.apache.mahout.cf.taste.common.NoSuchUserException;
import org.apache.mahout.cf.taste.common.Refreshable;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.impl.common.FastByIDMap;
import org.apache.mahout.cf.taste.impl.common.FastIDSet;
import org.apache.mahout.cf.taste.impl.recommender.AbstractRecommender;
import org.apache.mahout.cf.taste.impl.recommender.svd.Factorization;
import org.apache.mahout.cf.taste.impl.recommender.svd.Factorizer;
import org.apache.mahout.cf.taste.impl.recommender.svd.SVDRecommender;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.recommender.IDRescorer;
import org.apache.mahout.cf.taste.recommender.RecommendedItem;

public class OnlineSVDRecommender extends AbstractRecommender {

	private static final int EXPECTED_NEW_USER_COUNT = 100;
	private final FactorizationCachingFactorizer factorizationCachingFactorizer;
	private final SVDRecommender delegateRecommender;
	private final int featureCount;
	private final UserFactorUpdater userFactorUpdater;
	/**
	 * We keep track of rated items after build, so that we don't recommend them
	 * back to user.
	 */
	private final FastByIDMap<FastIDSet> itemsOfUsers;
	
	private FastByIDMap<double[]> newUserFeatures;
	
	public OnlineSVDRecommender(DataModel dataModel, Factorizer factorizer, UserFactorUpdater userFactorUpdater) throws TasteException {
		super(dataModel);
		this.userFactorUpdater = userFactorUpdater;
		this.itemsOfUsers = new FastByIDMap<>();
		this.newUserFeatures = new FastByIDMap<>(EXPECTED_NEW_USER_COUNT);
		factorizationCachingFactorizer = new FactorizationCachingFactorizer(factorizer);
		delegateRecommender = new SVDRecommender(dataModel, factorizationCachingFactorizer);
		featureCount = factorizationCachingFactorizer.getCachedFactorization().numFeatures();
	}

	@Override
	public float estimatePreference(long userID, long itemID)
			throws TasteException {
		Factorization factorization = factorizationCachingFactorizer.getCachedFactorization();
		if (newUserFeatures.containsKey(userID)) {
			double[] userFeatures = newUserFeatures.get(userID);
			double[] itemFeatures = factorization.getItemFeatures(itemID);
		    double estimate = 0;
		    for (int feature = 0; feature < userFeatures.length; feature++) {
		      estimate += userFeatures[feature] * itemFeatures[feature];
		    }
			return (float) estimate;
		} else {
			return delegateRecommender.estimatePreference(userID, itemID);
		}
	}

	public void addPreference(long userID, long itemID, float rat) throws TasteException {
		Factorization factorization = factorizationCachingFactorizer.getCachedFactorization();
		FastIDSet preferredItems = itemsOfUsers.get(userID);
		if (preferredItems == null) {
			preferredItems = new FastIDSet();
			itemsOfUsers.put(userID, preferredItems);
		}
		preferredItems.add(itemID);

		//here we try to get the feature vector to update.
		double[] features;
		if (newUserFeatures.containsKey(userID)) { 
			// CASE 2 : User does not exist at build time, but not first encounter			
			// (rating after CASE 3)
			features = newUserFeatures.get(userID);
		} else {
			try {
				delegateRecommender.getDataModel().getPreferencesFromUser(userID);
				// CASE 1 : User that exists at build time gives new rating  
				features = factorization.getUserFeatures(userID);
			} catch (NoSuchUserException e) {
				// CASE 3 : User does not exist at build time, and first encounter
				features = new double[featureCount];
				newUserFeatures.put(userID, features);
			}
		}
		userFactorUpdater.updateUserFactor(features, factorization, userID, itemID, rat);
	}

/*
	@Override
	public List<RecommendedItem> recommend(final long userID, int howMany,
			IDRescorer rescorer) throws TasteException {
		//Preconditions.checkArgument(howMany >= 1, "howMany must be at least 1");
		//log.debug("Recommending items for user ID '{}'", userID);
	
	
	
		FastIDSet possibleItemIDs;
		PreferenceArray preferencesFromUser;
		FastIDSet newlyRatedItems = itemsOfUsers.get(userID); 
		if (newUserFeatures.containsKey(userID)) {
			preferencesFromUser = new GenericUserPreferenceArray(newlyRatedItems.size());
			int i = 0;
			for (final long id : newlyRatedItems) {
				preferencesFromUser.set(i++, new Preference() {
					
					@Override
					public void setValue(float value) {
						throw new UnsupportedOperationException("Cannot set!");
					}
					
					@Override
					public float getValue() {
						return 1;
					}
					
					@Override
					public long getUserID() {
						return userID;
					}
					
					@Override
					public long getItemID() {
						return id;
					}
				});
			}
		} else {
			preferencesFromUser = getDataModel().getPreferencesFromUser(userID);
		}

		// here we assume userID is not used by candidate item strategy!
		// we assumed PreferredItemsNeighborhoodCandidateItemsStrategy is used.
		possibleItemIDs = getAllOtherItems(userID, preferencesFromUser);
		//following might be unnecessary:
		if (newlyRatedItems != null) {
			possibleItemIDs.removeAll(newlyRatedItems);	
		}
		
		List<RecommendedItem> topItems = TopItems.getTopItems(howMany, possibleItemIDs.iterator(), rescorer,
				new TopItems.Estimator<Long>() {
			
			@Override
			public double estimate(Long itemID) throws TasteException {
				return estimatePreference(userID, itemID);
			}
		});
		//log.debug("Recommendations are: {}", topItems);

		return topItems;
	}
*/

	@Override
	public List<RecommendedItem> recommend(final long userID, int howMany, 
			final IDRescorer rescorer) throws TasteException {

		return delegateRecommender.recommend(userID, howMany, new IDRescorer() {

			@Override
			public double rescore(long id, double originalScore) {
				return rescorer == null ? 
								originalScore : 
								rescorer.rescore(id, originalScore);
			}

			@Override
			public boolean isFiltered(long id) {
				return itemsOfUsers.get(userID).contains(id) 
								|| (rescorer == null ? false : rescorer.isFiltered(id));
			}
		});
	}

	@Override
	public void refresh(Collection<Refreshable> alreadyRefreshed) {
		// rebuild system in an offline manner
		// cleanup every newuser related field.
		// TODO test!
		delegateRecommender.refresh(alreadyRefreshed);
		newUserFeatures.clear();
		itemsOfUsers.clear();
	}
		
}

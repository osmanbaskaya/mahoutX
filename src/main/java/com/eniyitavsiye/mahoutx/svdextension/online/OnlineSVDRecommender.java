package com.eniyitavsiye.mahoutx.svdextension.online;

import com.eniyitavsiye.mahoutx.svdextension.FactorizationCachingFactorizer;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.math.linear.Array2DRowRealMatrix;
import org.apache.commons.math.linear.LUDecompositionImpl;
import org.apache.commons.math.linear.OpenMapRealVector;
import org.apache.commons.math.linear.RealMatrix;
import org.apache.commons.math.linear.RealVector;
import org.apache.commons.math.linear.SingularMatrixException;
import org.apache.mahout.cf.taste.common.NoSuchItemException;
import org.apache.mahout.cf.taste.common.NoSuchUserException;
import org.apache.mahout.cf.taste.common.Refreshable;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.impl.common.FastByIDMap;
import org.apache.mahout.cf.taste.impl.common.FastIDSet;
import org.apache.mahout.cf.taste.impl.common.LongPrimitiveIterator;
import org.apache.mahout.cf.taste.impl.recommender.AbstractRecommender;
import org.apache.mahout.cf.taste.impl.recommender.AllUnknownItemsCandidateItemsStrategy;
import org.apache.mahout.cf.taste.impl.recommender.TopItems;
import org.apache.mahout.cf.taste.impl.recommender.TopItems.Estimator;
import org.apache.mahout.cf.taste.impl.recommender.svd.Factorization;
import org.apache.mahout.cf.taste.impl.recommender.svd.Factorizer;
import org.apache.mahout.cf.taste.impl.recommender.svd.SVDRecommender;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.model.Preference;
import org.apache.mahout.cf.taste.model.PreferenceArray;
import org.apache.mahout.cf.taste.recommender.IDRescorer;
import org.apache.mahout.cf.taste.recommender.RecommendedItem;

public class OnlineSVDRecommender extends AbstractRecommender {

	private static final Logger log = Logger.getLogger(OnlineSVDRecommender.class.getName());

	static {
		log.setLevel(Level.ALL);
	}
	private static final double MIN_FEAT_NORM = 0.0000000001;
	private final FactorizationCachingFactorizer factorizationCachingFactorizer;
	private final SVDRecommender delegateRecommender;
	private final int featureCount;
	//private final UserFactorUpdater userFactorUpdater;
	private double[] singularValues;
	/**
	 * We keep track of rated items after build, so that we don't recommend them
	 * back to user.
	 */
	private final FastByIDMap<FastIDSet> itemsOfUsers;
	private final FastByIDMap<double[]> newUserFeatures;
	private final FastIDSet foldInNecessaryUsers;

	private final boolean seanMethod;

	private RealMatrix vTransposeRightInverse;
	private FastByIDMap<Integer> itemOrder;

	private int numItems;

	public OnlineSVDRecommender(DataModel dataModel, Factorizer factorizer, boolean seanMethod)
					throws TasteException {
		super(dataModel, new AllUnknownItemsCandidateItemsStrategy());
		//this.userFactorUpdater = userFactorUpdater;
		this.itemsOfUsers = new FastByIDMap<>();
		this.newUserFeatures = new FastByIDMap<>();
		this.foldInNecessaryUsers = new FastIDSet();
		factorizationCachingFactorizer = new FactorizationCachingFactorizer(factorizer);
		delegateRecommender = new SVDRecommender(dataModel, factorizationCachingFactorizer);
		featureCount = factorizationCachingFactorizer.getCachedFactorization().numFeatures();
		numItems = dataModel.getNumItems();
		this.seanMethod = seanMethod;
		if (!seanMethod) {
			extractSingularValues(dataModel);
		} else {
			calculateVTransposeRightInverse(dataModel);
		}
	}

  private static FastByIDMap<Integer> createIDMapping(int size, LongPrimitiveIterator idIterator) {
    FastByIDMap<Integer> mapping = new FastByIDMap<>(size);
    int index = 0;
    while (idIterator.hasNext()) {
      mapping.put(idIterator.nextLong(), index++);
    }
    return mapping;
  }

	public double[] foldInSingular(long user, PreferenceArray ratings) {
		final int nf = featureCount;
		final double[] svals = singularValues;
		double featurePrefs[] = new double[nf];

		log.log(Level.INFO, "Folding in with preferences {0}.", ratings);

		Factorization fact = factorizationCachingFactorizer.getCachedFactorization();
		try {
			int i = 0;
			for (Preference pref : ratings) {
				long iid = pref.getItemID();
				double r = pref.getValue();
				for (int f = 0; f < nf; f++) {
					double fv = fact.getItemFeatures(iid)[f];
					featurePrefs[f] += r * fv / svals[f];
				}
				++i;
			}
			foldInNecessaryUsers.remove(user);
		} catch (NoSuchItemException ex) {
			log.log(Level.SEVERE, "Non-existent items!", ex);
			throw new RuntimeException("Non-existent items!", ex);
		} finally {
			//nothing to do here. :D
		}

		return featurePrefs;
	}

	public double[] foldInSean(long user, PreferenceArray ratings) {
		RealVector a_u = new OpenMapRealVector(numItems, ratings.length());
		for (Preference p : ratings) {
			a_u.setEntry(itemOrder.get(p.getItemID()), p.getValue());
		}
		return vTransposeRightInverse.preMultiply(a_u).getData();
	}

	public double[] foldIn(long user, PreferenceArray ratings) {
		if (seanMethod) {
			return foldInSean(user, ratings);
		} else {
			return foldInSingular(user, ratings);
		}
	}

	@Override
	public float estimatePreference(long userID, long itemID)
					throws TasteException {
		Factorization factorization = factorizationCachingFactorizer.getCachedFactorization();
		double[] userFeatures;
		if (foldInNecessaryUsers.contains(userID)) {
			userFeatures = foldIn(userID, getDataModel().getPreferencesFromUser(userID));
			try {
				System.arraycopy(userFeatures, 0, factorization.getUserFeatures(userID), 0, featureCount);
			} catch (NoSuchUserException e) {
				newUserFeatures.put(userID, userFeatures);
			}
		} else if (newUserFeatures.containsKey(userID)) {
			userFeatures = newUserFeatures.get(userID);
		} else {
			userFeatures = factorization.getUserFeatures(userID);
		}
		double[] itemFeatures = factorization.getItemFeatures(itemID);
		double estimate = 0;
		for (int feature = 0; feature < userFeatures.length; feature++) {
			double singVal = seanMethod ? 1 : singularValues[feature];
			estimate += userFeatures[feature] * singVal * itemFeatures[feature];
		}
		return (float) estimate;
	}

	public void addPreference(long userID, long itemID, float rat) throws TasteException {
		foldInNecessaryUsers.add(userID);
		/*
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
		 */
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

	/*
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
	 */
	@Override
	public void refresh(Collection<Refreshable> alreadyRefreshed) {
		// rebuild system in an offline manner
		// cleanup every newuser related field.
		// TODO test!
		delegateRecommender.refresh(alreadyRefreshed);
		itemsOfUsers.clear();
	}

	/**
	 * Extracts singular values S from existing user/item feature matrices and
	 * ratings.
	 *
	 * @param dataModel
	 * @throws TasteException
	 */
	private void extractSingularValues(DataModel dataModel) throws TasteException {
		this.singularValues = new double[featureCount];
		Factorization fact = factorizationCachingFactorizer.getCachedFactorization();
		for (int feature = 0; feature < featureCount; feature++) {
			double ussq = 0;
			LongPrimitiveIterator userIDs = dataModel.getUserIDs();
			while (userIDs.hasNext()) {
				long uid = userIDs.nextLong();
				double uf = fact.getUserFeatures(uid)[feature];
				ussq += uf * uf;
			}
			double unrm = (double) Math.sqrt(ussq);
			if (unrm > MIN_FEAT_NORM) {
				while (userIDs.hasNext()) {
					long uid = userIDs.nextLong();
					fact.getUserFeatures(uid)[feature] /= unrm;
				}
			}
			double issq = 0;
			LongPrimitiveIterator itemIDs = dataModel.getItemIDs();
			while (itemIDs.hasNext()) {
				long iid = itemIDs.nextLong();
				double fv = fact.getItemFeatures(iid)[feature];
				issq += fv * fv;
			}
			double inrm = (double) Math.sqrt(issq);
			if (inrm > MIN_FEAT_NORM) {
				while (itemIDs.hasNext()) {
					long iid = itemIDs.nextLong();
					fact.getItemFeatures(iid)[feature] /= inrm;
				}
			}
			singularValues[feature] = unrm * inrm;
		}
	}

	@Override
	public List<RecommendedItem> recommend(final long userID, int howMany,
					IDRescorer rescorer) throws TasteException {
		//Preconditions.checkArgument(howMany >= 1, "howMany must be at least 1");
		//log.debug("Recommending items for user ID '{}'", userID);

		PreferenceArray preferencesFromUser = getDataModel().getPreferencesFromUser(userID);
		FastIDSet possibleItemIDs = getAllOtherItems(userID, preferencesFromUser);

		List<RecommendedItem> topItems = TopItems.getTopItems(howMany, possibleItemIDs.iterator(), rescorer,
						new Estimator<Long>() {
							@Override
							public double estimate(Long itemID) throws TasteException {
								return estimatePreference(userID, itemID);
							}
						});
		//log.debug("Recommendations are: {}", topItems);

		return topItems;
	}

	private void calculateVTransposeRightInverse(DataModel dataModel) throws RuntimeException, NullPointerException, IllegalArgumentException, NoSuchItemException, TasteException {
		int numItems = dataModel.getNumItems();
		itemOrder = new FastByIDMap<>(numItems);
		double[][] array = new double[numItems][featureCount];
		Factorization fact = factorizationCachingFactorizer.getCachedFactorization();
		LongPrimitiveIterator itemIDIterator = dataModel.getItemIDs();
		int ind = 0;
		while (itemIDIterator.hasNext()) {
			long id = itemIDIterator.nextLong();
			array[ind] = fact.getItemFeatures(id);
			itemOrder.put(id, ind);
		}
		this.vTransposeRightInverse = new Array2DRowRealMatrix(array);
		RealMatrix inv = null;
		try {
			inv = new LUDecompositionImpl(vTransposeRightInverse.transpose().multiply(vTransposeRightInverse)).getSolver().getInverse();
		} catch (SingularMatrixException e) {
			log.log(Level.SEVERE, "Could not take right inverse of item feature matrix!", e);
			throw new RuntimeException(e);
		}
		vTransposeRightInverse = vTransposeRightInverse.multiply(inv);
	}
}

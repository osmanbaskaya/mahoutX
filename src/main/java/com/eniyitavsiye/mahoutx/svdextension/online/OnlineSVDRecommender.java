package com.eniyitavsiye.mahoutx.svdextension.online;

import com.eniyitavsiye.mahoutx.common.MutableGenericDataModel;
import com.eniyitavsiye.mahoutx.common.ReplaceableDataModel;
import com.eniyitavsiye.mahoutx.svdextension.FactorizationCachingFactorizer;
import org.apache.mahout.cf.taste.common.NoSuchUserException;
import org.apache.mahout.cf.taste.common.Refreshable;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.impl.common.FastByIDMap;
import org.apache.mahout.cf.taste.impl.common.FastIDSet;
import org.apache.mahout.cf.taste.impl.model.jdbc.ReloadFromJDBCDataModel;
import org.apache.mahout.cf.taste.impl.recommender.AbstractRecommender;
import org.apache.mahout.cf.taste.impl.recommender.TopItems;
import org.apache.mahout.cf.taste.impl.recommender.TopItems.Estimator;
import org.apache.mahout.cf.taste.impl.recommender.svd.Factorization;
import org.apache.mahout.cf.taste.impl.recommender.svd.Factorizer;
import org.apache.mahout.cf.taste.impl.recommender.svd.SVDRecommender;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.model.Preference;
import org.apache.mahout.cf.taste.model.PreferenceArray;
import org.apache.mahout.cf.taste.recommender.CandidateItemsStrategy;
import org.apache.mahout.cf.taste.recommender.IDRescorer;
import org.apache.mahout.cf.taste.recommender.RecommendedItem;

import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

public class OnlineSVDRecommender extends AbstractRecommender {

  private int iterationCount = 30;
  private double alpha = 0.01;
  private double lambda = 0.02;

  private static final Logger log = Logger.getLogger(OnlineSVDRecommender.class.getName());

  static {
    log.setLevel(Level.ALL);
  }

  private final FactorizationCachingFactorizer factorizationCachingFactorizer;
  private final SVDRecommender delegateRecommender;
  private final int featureCount;
  //private final UserFactorUpdater userFactorUpdater;
  /**
   * We keep track of rated items after build, so that we don't recommend them
   * back to user.
   */
  //private final FastByIDMap<FastIDSet> itemsOfUsers;
  private final FastByIDMap<PreferenceArray> itemsOfUsers;
  private final FastByIDMap<double[]> newUserFeatures;
  private final FastIDSet foldInNecessaryUsers;

  public FactorizationCachingFactorizer getFactorizationCachingFactorizer() {
    return factorizationCachingFactorizer;
  }

  public int getIterationCount() {
    return iterationCount;
  }

  public void setIterationCount(int iterationCount) {
    this.iterationCount = iterationCount;
  }

  public double getAlpha() {
    return alpha;
  }

  public void setAlpha(double alpha) {
    this.alpha = alpha;
  }

  public double getLambda() {
    return lambda;
  }

  public void setLambda(double lambda) {
    this.lambda = lambda;
  }

  public OnlineSVDRecommender(DataModel dataModel, Factorizer factorizer,
                              CandidateItemsStrategy strategy) throws TasteException {
    super(dataModel, strategy);
    //this.userFactorUpdater = userFactorUpdater;
    this.itemsOfUsers = new FastByIDMap<>();
    this.newUserFeatures = new FastByIDMap<>();
    this.foldInNecessaryUsers = new FastIDSet();
    factorizationCachingFactorizer = new FactorizationCachingFactorizer(factorizer);
    delegateRecommender = new SVDRecommender(dataModel, factorizationCachingFactorizer);
    featureCount = factorizationCachingFactorizer.getCachedFactorization().numFeatures();
  }

  public double[] foldIn(long user, PreferenceArray ratings) {
    foldInNecessaryUsers.remove(user);
    final int nf = featureCount;
    double userFeatures[] = new double[nf];
    // TODO initialize with some mean and std dev.
    Random rGen = new Random();
    for (int i = 0; i < nf; ++i) {
      userFeatures[i] = rGen.nextGaussian()*0.3;
    }

//    log.log(Level.INFO, "Folding in with preferences {0}.", ratings);

    Factorization fact = factorizationCachingFactorizer.getCachedFactorization();
    double lr = alpha;
    for (int w = 0; w < iterationCount; ++w) {
      for (int i = 0; i < ratings.length(); ++i) {
        // TODO shuffle ratings
        Preference pref = ratings.get(i);
        long iid = pref.getItemID();
        double r = pref.getValue();
        double[] itemFeatures;
        try {
          itemFeatures = fact.getItemFeatures(iid);
        } catch (Exception e) {
          continue;
        }
        double estimate = 0;
        for (int feature = 0; feature < userFeatures.length; feature++) {
          estimate += userFeatures[feature] * itemFeatures[feature];
        }
        double e = r - estimate;
        for (int f = 0; f < nf; f++) {
          userFeatures[f] += lr * (e * itemFeatures[f] - lambda * userFeatures[f]);
        }
      }
    }

    return userFeatures;
  }

  public double[] updateUserWithFoldIn(long userID, PreferenceArray userPrefs) {
    Factorization factorization = factorizationCachingFactorizer.getCachedFactorization();
    double[] userFeatures = foldIn(userID, userPrefs);
    try {
      //user was available during build process
      System.arraycopy(userFeatures, 0, factorization.getUserFeatures(userID), 0, featureCount);
    } catch (NoSuchUserException e) {
      //user is new.
      newUserFeatures.put(userID, userFeatures);
    }
    return userFeatures;
  }

  @Override
  public float estimatePreference(long userID, long itemID)
          throws TasteException {
    Factorization factorization = factorizationCachingFactorizer.getCachedFactorization();
    double[] userFeatures;
    // TODO maybe handle new users with no rating
    if (foldInNecessaryUsers.contains(userID)) {       // any user with changed ratings
      PreferenceArray userPrefs = tryToGetFreshPreferences(userID);
      userFeatures = updateUserWithFoldIn(userID, userPrefs);
    } else if (newUserFeatures.containsKey(userID)) {   // new user tries to estimate
      // preference without change in ratings
      userFeatures = newUserFeatures.get(userID);
    } else {
      try {
        //user was available during build process, but no change in ratings
        userFeatures = factorization.getUserFeatures(userID);
      } catch (NoSuchUserException e) {
        //illegal!
        log.log(Level.SEVERE, "A new user without rating data tries to estimate preference! {0} {1}",
                new Object[]{userID, itemID});
        return -1;
      }
    }
    double[] itemFeatures;
    try {
      itemFeatures = factorization.getItemFeatures(itemID);
    } catch (Exception e) {
        log.log(Level.FINE, "A requested preference estimation for a new item! user: {0}, item: {1}",
                new Object[]{userID, itemID});
        return -1;
    }
    double estimate = 0;
    for (int feature = 0; feature < userFeatures.length; feature++) {
      estimate += userFeatures[feature] * itemFeatures[feature];
    }
    return (float) estimate;
  }

  public DataModel userPreferenceChanged(long userID, long itemID, float rat) throws TasteException {
    foldInNecessaryUsers.add(userID);
    DataModel model = getDataModel();
/*    log.log(Level.INFO, "user: {}, item: {}, rating: {}, Model : {}, ratings before: {}",
            new Object[]{
                    userID,
                    itemID,
                    rat,
                    model.getClass(),
                    model.getPreferencesFromUser(userID).length()});
    if (model instanceof ReplaceableDataModel) {
      ReplaceableDataModel replaceableDataModel = (ReplaceableDataModel) model;
      DataModel newDelegate = DataModelUtilities.addPreferece(replaceableDataModel, new GenericPreference(userID, itemID, rat));
      log.log(Level.INFO, "Ratings after: {}, new rating: {}",
              new Object[]{
                      model.getPreferencesFromUser(userID).length(),
                      model.getPreferenceValue(userID, itemID)});
      return newDelegate;
    }*/
    if (model instanceof MutableGenericDataModel) {
      model.setPreference(userID, itemID, rat);
    }
    return model;
  }

    /*
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
	}
		 */

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

  @Override
  public List<RecommendedItem> recommend(final long userID, int howMany,
                                         IDRescorer rescorer) throws TasteException {
    //Preconditions.checkArgument(howMany >= 1, "howMany must be at least 1");
    //log.debug("Recommending items for user ID '{}'", userID);

  //  log.log(Level.INFO, "Beginning generation of recommendations for user : {0}", userID);
    PreferenceArray preferencesFromUser = tryToGetFreshPreferences(userID);
//    log.log(Level.INFO, "User preferences: {0}", preferencesFromUser);
    FastIDSet possibleItemIDs = getAllOtherItems(userID, preferencesFromUser);
//    log.log(Level.INFO, "Candidtate items for user: {0}", possibleItemIDs);

    List<RecommendedItem> topItems = TopItems.getTopItems(howMany, possibleItemIDs.iterator(), rescorer,
            new Estimator<Long>() {
              @Override
              public double estimate(Long itemID) throws TasteException {
                return estimatePreference(userID, itemID);
              }
            });
//    log.log(Level.INFO, "Recommendations are: {0}", topItems);

    return topItems;
  }

  private PreferenceArray tryToGetFreshPreferences(long userID) throws TasteException {
    // TODO always test this after changing fetchData web method!
    // TODO handle non-existent user query from in-memory data models.
    // try to get fresh data from db, if underlying DataModel somehow permits.
    DataModel model = getDataModel();
    PreferenceArray userPrefs;
    //if underlying model is in-memory data model
    if (true) {
      return model.getPreferencesFromUser(userID);
    } else if (model instanceof ReloadFromJDBCDataModel) {
      //then it must have a JDBCDataModel as a delegate, query from that.
      userPrefs = ((ReloadFromJDBCDataModel) model).getDelegate().getPreferencesFromUser(userID);
      //if the model is replaceable data model,
    } else if (model instanceof ReplaceableDataModel) {
      ReplaceableDataModel replaceableDataModel = (ReplaceableDataModel) model;
      //then check if it has a MySQLJDBCDataModel as its delegate,
      if (replaceableDataModel.getDelegate() instanceof ReloadFromJDBCDataModel) {
        // if so, query from that delegate
        ReloadFromJDBCDataModel reloadFromJDBCDataModel =
                ((ReloadFromJDBCDataModel) replaceableDataModel.getDelegate());
        userPrefs = reloadFromJDBCDataModel.getDelegate().getPreferencesFromUser(userID);
      } else {
        //otherwise nothing to do, query from the available model.
        userPrefs = replaceableDataModel.getPreferencesFromUser(userID);
      }
    } else { //some data model we currently don't know about.
      //again take what current model gives.
      userPrefs = model.getPreferencesFromUser(userID);
    }
    return userPrefs;
  }

  public int getNumAdditionalUsers() {
    return newUserFeatures.size();
  }

}

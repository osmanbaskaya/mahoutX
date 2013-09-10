/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.eniyitavsiye.mahoutx.svd;

import com.google.common.base.Preconditions;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import org.apache.mahout.cf.taste.common.Refreshable;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.impl.common.FastIDSet;
import org.apache.mahout.cf.taste.impl.common.RefreshHelper;
import org.apache.mahout.cf.taste.impl.recommender.AbstractRecommender;
import org.apache.mahout.cf.taste.impl.recommender.TopItems;
import org.apache.mahout.cf.taste.impl.recommender.svd.Factorization;
import org.apache.mahout.cf.taste.impl.recommender.svd.Factorizer;
import org.apache.mahout.cf.taste.impl.recommender.svd.NoPersistenceStrategy;
import org.apache.mahout.cf.taste.impl.recommender.svd.PersistenceStrategy;
import org.apache.mahout.cf.taste.impl.recommender.svd.SVDRecommender;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.model.PreferenceArray;
import org.apache.mahout.cf.taste.recommender.CandidateItemsStrategy;
import org.apache.mahout.cf.taste.recommender.IDRescorer;
import org.apache.mahout.cf.taste.recommender.RecommendedItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author tevfik
 */
public class NewSVDRecommender extends AbstractRecommender {

    private NewFactorization factorization;
    private final Factorizer factorizer;
    private static final Logger log = LoggerFactory.getLogger(SVDRecommender.class);

    public NewSVDRecommender(DataModel dataModel, Factorizer factorizer) throws TasteException {
        this(dataModel, factorizer, getDefaultCandidateItemsStrategy(), getDefaultPersistenceStrategy());
    }

    public NewSVDRecommender(DataModel dataModel, Factorizer factorizer, CandidateItemsStrategy candidateItemsStrategy,
            PersistenceStrategy persistenceStrategy) throws TasteException {
        super(dataModel, candidateItemsStrategy);
        this.factorizer = factorizer;
        train();

    }

    private void train() throws TasteException {
      //  factorization = factorizer.factorize();
    }

    static PersistenceStrategy getDefaultPersistenceStrategy() {
        return new NoPersistenceStrategy();
    }

    @Override
    public List<RecommendedItem> recommend(long userID, int howMany, IDRescorer rescorer) throws TasteException {
        Preconditions.checkArgument(howMany >= 1, "howMany must be at least 1");
        log.debug("Recommending items for user ID '{}'", userID);

        PreferenceArray preferencesFromUser = getDataModel().getPreferencesFromUser(userID);
        FastIDSet possibleItemIDs = getAllOtherItems(userID, preferencesFromUser);

        List<RecommendedItem> topItems = TopItems.getTopItems(howMany, possibleItemIDs.iterator(), rescorer,
                new NewSVDRecommender.Estimator(userID));
        log.debug("Recommendations are: {}", topItems);

        return topItems;
    }

    /**
     * a preference is estimated by computing the dot-product of the user and
     * item feature vectors
     */
    @Override
    public float estimatePreference(long userID, long itemID) throws TasteException {
        double[] userFeatures = factorization.getUserFeatures(userID);
        double[] itemFeatures = factorization.getItemFeatures(itemID);
        double estimate = 0;
        for (int feature = 0; feature < userFeatures.length; feature++) {
            estimate += userFeatures[feature] * itemFeatures[feature];
        }
        return (float) estimate;
    }

    private final class Estimator implements TopItems.Estimator<Long> {

        private final long theUserID;

        private Estimator(long theUserID) {
            this.theUserID = theUserID;
        }

        @Override
        public double estimate(Long itemID) throws TasteException {
            return estimatePreference(theUserID, itemID);
        }
    }

  

    @Override
    public void refresh(Collection<Refreshable> alreadyRefreshed) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
}

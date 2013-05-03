package com.eniyitavsiye.mahoutx.common.evaluation;

import com.eniyitavsiye.mahoutx.svdextension.online.OnlineSVDRecommender;
import com.google.common.collect.Lists;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.common.TopK;
import org.apache.mahout.cf.taste.eval.DataModelBuilder;
import org.apache.mahout.cf.taste.eval.IRStatistics;
import org.apache.mahout.cf.taste.eval.RecommenderBuilder;
import org.apache.mahout.cf.taste.impl.common.*;
import org.apache.mahout.cf.taste.impl.model.GenericDataModel;
import org.apache.mahout.cf.taste.impl.model.GenericPreference;
import org.apache.mahout.cf.taste.impl.model.GenericUserPreferenceArray;
import org.apache.mahout.cf.taste.impl.recommender.AllUnknownItemsCandidateItemsStrategy;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.model.Preference;
import org.apache.mahout.cf.taste.model.PreferenceArray;
import org.apache.mahout.cf.taste.recommender.CandidateItemsStrategy;
import org.apache.mahout.cf.taste.recommender.IDRescorer;
import org.apache.mahout.cf.taste.recommender.Recommender;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class KorenIRStatsWithFoldInEvaluator {

    private static final Logger log = Logger.getLogger(KorenIRStatsEvaluator.class.getName());
    private static final CandidateItemsStrategy strategy = new AllUnknownItemsCandidateItemsStrategy();

    private Random random = new Random();
    private double trainingPercentage;
    private double foldInUserPercentage;
    private int nUnratedItems;

    private float percentIncrement = 0.05f;

    public void setPercentIncrement(float percentIncrement) {
        if (percentIncrement > 1 || percentIncrement < 0) {
            throw new IllegalArgumentException("percentIncrement should be in [0,1]: " +
                    percentIncrement);
        }
        this.percentIncrement = percentIncrement;
    }

    public KorenIRStatsWithFoldInEvaluator(double trainingPercentage, double foldInUserPercentage, int nUnratedItems) {
        this.trainingPercentage = trainingPercentage;
        this.nUnratedItems = nUnratedItems;
        this.foldInUserPercentage = foldInUserPercentage;
    }

    public String evaluateFoldIn(
            RecommenderBuilder recommenderBuilder,
            DataModelBuilder dataModelBuilder,
            DataModel dataModel,
            IDRescorer rescorer,
            int at,
            double relevanceThreshold,
            double evaluationPercentage) throws TasteException {


        int numUsers = dataModel.getNumUsers();
        FastByIDMap<PreferenceArray> trainingWOFoldInPrefs = new FastByIDMap<>(
                1 + (int) (evaluationPercentage * numUsers));
        FastByIDMap<PreferenceArray> trainingWFoldInPrefs = new FastByIDMap<>(
                1 + (int) (evaluationPercentage * numUsers));
        FastByIDMap<PreferenceArray> foldInPrefs = new FastByIDMap<>(
                1 + (int) (evaluationPercentage * numUsers));
        FastByIDMap<PreferenceArray> testPrefs = new FastByIDMap<>(
                1 + (int) (evaluationPercentage * numUsers));


        log.log(Level.INFO,  "Starting to divide users into training and test...");

        LongPrimitiveIterator it = dataModel.getUserIDs();
        while (it.hasNext()) {
            long userID = it.nextLong();
            if (random.nextDouble() < evaluationPercentage) {
                splitOneUsersPrefs(trainingPercentage,
                        trainingWOFoldInPrefs, trainingWFoldInPrefs, testPrefs, foldInPrefs,
                        userID, dataModel, foldInUserPercentage);
            }
        }

        return "{Normal Recall =" +
                buildAndEvaluateFor(dataModel, recommenderBuilder,
                        trainingWOFoldInPrefs, null, testPrefs,
                        relevanceThreshold, at) + ", " +
                "Fold-in Recall = " +
                buildAndEvaluateFor(dataModel, recommenderBuilder,
                        trainingWFoldInPrefs, foldInPrefs, testPrefs,
                        relevanceThreshold, at) + "}";
    }

    private double buildAndEvaluateFor(DataModel dataModel,
         RecommenderBuilder recommenderBuilder,
         FastByIDMap<PreferenceArray> trainingPrefs,
         FastByIDMap<PreferenceArray> foldIn,
         FastByIDMap<PreferenceArray> testPrefs,
         double relevanceThreshold,
         int at) throws TasteException {

        log.log(Level.INFO,  "Training size: {0}, Test size: {1}.",
                new Object[] {trainingPrefs.size(), testPrefs.size()});

        DataModel trainingDataModel = new GenericDataModel(trainingPrefs);

        log.log(Level.INFO, "Building model...");
        final Recommender recommender = recommenderBuilder.buildRecommender(trainingDataModel);
        log.log(Level.INFO, "Model build complete, finding relevant items...");

        if (foldIn != null) {
            LongPrimitiveIterator iterator = foldIn.keySetIterator();
            OnlineSVDRecommender onlineSVDRecommender = (OnlineSVDRecommender) recommender;
            while (iterator.hasNext()) {
                long userID = iterator.next();
                PreferenceArray prefs = foldIn.get(userID);
                onlineSVDRecommender.updateUserWithFoldIn(userID, prefs);
            }
        }

        List<Preference> relevantPreferences = new ArrayList<>();

        LongPrimitiveIterator iterator = testPrefs.keySetIterator();
        while (iterator.hasNext()) {
            long userID = iterator.nextLong();
            PreferenceArray prefs = testPrefs.get(userID);
            for (Preference pref : prefs) {
                if (pref.getValue() >= relevanceThreshold &&
                        existsInData(trainingDataModel, pref.getItemID())) {
                    relevantPreferences.add(pref);
                }
            }
        }

        int nRelevantPrefs = relevantPreferences.size();
        log.log(Level.INFO, "#relevant preferences: {0}.", nRelevantPrefs);

        final RunningAverage recall = new FullRunningAverage();

        float nextPercent = 0;
        int i = 0;
        for (Preference pref : relevantPreferences) {
            final long userID = pref.getUserID();

            List<Long> candidateItems = sieveNRandomItems(
                    strategy.getCandidateItems(userID, dataModel.getPreferencesFromUser(userID), trainingDataModel),
                    nUnratedItems);
            candidateItems.add(pref.getItemID());

            TopK<Long> topN = new TopK<>(at, new Comparator<Long>() {
                @Override
                public int compare(Long o1, Long o2) {
                    float r1;
                    float r2;
                    try {
                        r1 = recommender.estimatePreference(userID, o1);
                    } catch (TasteException e) {
                        r1 = 0;
                    }
                    try {
                        r2 = recommender.estimatePreference(userID, o2);
                    } catch (TasteException e) {
                        r2 = 0;
                    }
                    if (r1 < r2) {
                        return -1;
                    } else if (r1 > r2) {
                        return 1;
                    } else {
                        return 0;
                    }
                }
            });

            for (long id : candidateItems) {
                topN.offer(id);
            }

            int hit = topN.retrieve().contains(pref.getItemID()) ? 1 : 0;
            recall.addDatum(hit);

            float currentPercent = (float) i / nRelevantPrefs;
            if (currentPercent > nextPercent) {
                nextPercent += percentIncrement;
                log.log(Level.INFO, "Current pref : {0}/{1} (%{2}).", new Object[] { i, nRelevantPrefs, currentPercent * 100 });
            }

        }
        log.log(Level.INFO, "Recall evaluation is complete, recall: {0}.", recall.getAverage());

        return recall.getAverage();
    }

    private List<Long> sieveNRandomItems(FastIDSet candidateItems, int nUnratedItems) {
        //TODO maybe make this more efficient later.

        LongPrimitiveIterator it = candidateItems.iterator();
        List<Long> list = Lists.newArrayList();
        while (it.hasNext()) {
            list.add(it.nextLong());
        }
        Collections.shuffle(list);

        return list.subList(0, Math.min(list.size(), nUnratedItems));
    }

    private boolean existsInData(DataModel trainingDataModel, long itemID) {
        try {
            trainingDataModel.getPreferencesForItem(itemID);
            return true;
        } catch (TasteException e) {
            return false;
        }
    }

    private void splitOneUsersPrefs(double trainingPercentage,
                                    FastByIDMap<PreferenceArray> trainingWithoutFoldInPrefs,
                                    FastByIDMap<PreferenceArray> trainingWithFoldInPrefs,
                                    FastByIDMap<PreferenceArray> testPrefs,
                                    FastByIDMap<PreferenceArray> foldInPrefs,
                                    long userID,
                                    DataModel dataModel,
                                    double foldInUserPercentage) throws TasteException {

        boolean foldInUser = false;
        if (random.nextDouble() < foldInUserPercentage) {
            foldInUser = true;
        }

        List<Preference> oneUserTrainingWOFoldInPrefs = null;
        List<Preference> oneUserTrainingWFoldInPrefs = null;
        List<Preference> oneUserTestPrefs = null;
        List<Preference> oneUserFoldInPrefs = null;


        PreferenceArray prefs = dataModel.getPreferencesFromUser(userID);
        int size = prefs.length();
        for (int i = 0; i < size; i++) {
            boolean isTrainingPref = random.nextDouble() < trainingPercentage;
            Preference newPref = new GenericPreference(userID, prefs.getItemID(i), prefs.getValue(i));

            if (foldInUser) {
                if (isTrainingPref) {

                    if (oneUserTrainingWOFoldInPrefs == null) {
                        oneUserTrainingWOFoldInPrefs = Lists.newArrayListWithCapacity(3);
                    }
                    oneUserTrainingWOFoldInPrefs.add(newPref);

                    if (oneUserFoldInPrefs == null) {
                        oneUserFoldInPrefs =  Lists.newArrayList();
                    }
                    oneUserFoldInPrefs.add(newPref);
                } else {

                    if (oneUserTestPrefs == null) {
                        oneUserTestPrefs = Lists.newArrayListWithCapacity(3);
                    }
                    oneUserTestPrefs.add(newPref);
                }
            } else { //non-foldin user
                if (oneUserTrainingWOFoldInPrefs == null) {
                    oneUserTrainingWOFoldInPrefs = Lists.newArrayListWithCapacity(3);
                }
                oneUserTrainingWOFoldInPrefs.add(newPref);

                if (oneUserTrainingWFoldInPrefs == null) {
                    oneUserTrainingWFoldInPrefs =  Lists.newArrayList();
                }
                oneUserTrainingWFoldInPrefs.add(newPref);
            }
        }


        if (oneUserTrainingWOFoldInPrefs != null
                && oneUserFoldInPrefs != null
                && testPrefs != null
                && trainingWithFoldInPrefs != null) {
            trainingWithoutFoldInPrefs.put(userID, new GenericUserPreferenceArray(oneUserTrainingWOFoldInPrefs));
            foldInPrefs.put(userID, new GenericUserPreferenceArray(oneUserFoldInPrefs));
            testPrefs.put(userID, new GenericUserPreferenceArray(oneUserTestPrefs));
            trainingWithFoldInPrefs.put(userID, new GenericUserPreferenceArray(oneUserTrainingWFoldInPrefs));
        }
    }

}

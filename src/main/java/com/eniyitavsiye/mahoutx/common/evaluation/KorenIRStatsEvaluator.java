package com.eniyitavsiye.mahoutx.common.evaluation;

import com.google.common.collect.Lists;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.common.TopK;
import org.apache.mahout.cf.taste.eval.DataModelBuilder;
import org.apache.mahout.cf.taste.eval.IRStatistics;
import org.apache.mahout.cf.taste.eval.RecommenderBuilder;
import org.apache.mahout.cf.taste.eval.RecommenderIRStatsEvaluator;
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

public class KorenIRStatsEvaluator implements RecommenderIRStatsEvaluator {

    private static final CandidateItemsStrategy strategy = new AllUnknownItemsCandidateItemsStrategy();

    private Random random = new Random();
    private double trainingPercentage;
    private int nUnratedItems;


    public KorenIRStatsEvaluator(double trainingPercentage, int nUnratedItems) {
        this.trainingPercentage = trainingPercentage;
        this.nUnratedItems = nUnratedItems;
    }

    @Override
    public IRStatistics evaluate(
            RecommenderBuilder recommenderBuilder,
            DataModelBuilder dataModelBuilder,
            DataModel dataModel,
            IDRescorer rescorer,
            int at,
            double relevanceThreshold,
            double evaluationPercentage) throws TasteException {


        int numUsers = dataModel.getNumUsers();
        FastByIDMap<PreferenceArray> trainingPrefs = new FastByIDMap<>(
                1 + (int) (evaluationPercentage * numUsers));
        FastByIDMap<PreferenceArray> testPrefs = new FastByIDMap<>(
                1 + (int) (evaluationPercentage * numUsers));



        LongPrimitiveIterator it = dataModel.getUserIDs();
        while (it.hasNext()) {
            long userID = it.nextLong();
            if (random.nextDouble() < evaluationPercentage) {
                splitOneUsersPrefs(trainingPercentage, trainingPrefs, testPrefs, userID, dataModel);
            }
        }

        DataModel trainingDataModel = new GenericDataModel(trainingPrefs);
        final Recommender recommender = recommenderBuilder.buildRecommender(trainingDataModel);

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

        final RunningAverage recall = new FullRunningAverage();

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
        }

        return new IRStatistics() {
            @Override
            public double getPrecision() {
                throw new UnsupportedOperationException("Precision not supported!");
            }

            @Override
            public double getRecall() {
                return recall.getAverage();
            }

            @Override
            public double getFallOut() {
                throw new UnsupportedOperationException("FallOut not supported!");
            }

            @Override
            public double getF1Measure() {
                throw new UnsupportedOperationException("F1Measure not supported!");
            }

            @Override
            public double getFNMeasure(double n) {
                throw new UnsupportedOperationException("FNMeasure not supported!");
            }

            @Override
            public double getNormalizedDiscountedCumulativeGain() {
                throw new UnsupportedOperationException("NormalizedDiscountedCumulativeGain not supported!");
            }

            @Override
            public double getReach() {
                throw new UnsupportedOperationException("Reach not supported!");
            }
        };
    }

    private List<Long> sieveNRandomItems(FastIDSet candidateItems, int nUnratedItems) {
        //TODO maybe make this more efficient later.

        LongPrimitiveIterator it = candidateItems.iterator();
        List<Long> list = Lists.newArrayList();
        while (it.hasNext()) {
            list.add(it.nextLong());
        }
        Collections.shuffle(list);

        return list.subList(0, nUnratedItems);
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
                                    FastByIDMap<PreferenceArray> trainingPrefs,
                                    FastByIDMap<PreferenceArray> testPrefs,
                                    long userID,
                                    DataModel dataModel) throws TasteException {
        List<Preference> oneUserTrainingPrefs = null;
        List<Preference> oneUserTestPrefs = null;
        PreferenceArray prefs = dataModel.getPreferencesFromUser(userID);
        int size = prefs.length();
        for (int i = 0; i < size; i++) {
            Preference newPref = new GenericPreference(userID, prefs.getItemID(i), prefs.getValue(i));
            if (random.nextDouble() < trainingPercentage) {
                if (oneUserTrainingPrefs == null) {
                    oneUserTrainingPrefs = Lists.newArrayListWithCapacity(3);
                }
                oneUserTrainingPrefs.add(newPref);
            } else {
                if (oneUserTestPrefs == null) {
                    oneUserTestPrefs = Lists.newArrayListWithCapacity(3);
                }
                oneUserTestPrefs.add(newPref);
            }
        }
        if (oneUserTrainingPrefs != null) {
            trainingPrefs.put(userID, new GenericUserPreferenceArray(oneUserTrainingPrefs));
            if (oneUserTestPrefs != null) {
                testPrefs.put(userID, new GenericUserPreferenceArray(oneUserTestPrefs));
            }
        }
    }

}


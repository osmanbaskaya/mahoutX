/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.eniyitavsiye.mahoutx.svd;

import com.google.common.collect.Lists;
import java.util.List;
import java.util.Random;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.impl.common.FullRunningAverage;
import org.apache.mahout.cf.taste.impl.common.LongPrimitiveIterator;
import org.apache.mahout.cf.taste.impl.common.RunningAverage;
import org.apache.mahout.cf.taste.impl.model.GenericPreference;
import org.apache.mahout.cf.taste.impl.recommender.svd.AbstractFactorizer;
import org.apache.mahout.cf.taste.impl.recommender.svd.Factorization;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.model.Preference;
import org.apache.mahout.common.RandomUtils;

/**
 *
 * @author tevfik
 */
public class TevfikSVDFactorizer extends AbstractFactorizer {

    private final double learningRate;
    /**
     * used to prevent overfitting.
     */
    private final double regularization;
    /**
     * number of features used to compute this factorization
     */
    private final int numFeatures;
    /**
     * number of iterations
     */
    private final int numIterations;
    private final double randomNoise;
    private final DataModel dataModel;
    private static final double DEFAULT_LEARNING_RATE = 0.005;
    private static final double DEFAULT_REGULARIZATION = 0.02;
    private static final double DEFAULT_RANDOM_NOISE = 0.005;
    private double[][] userVectors;
    private double[][] itemVectors;
    private double defaultValue;
    private double interval;
    private List<GenericPreference> preferences;

    public TevfikSVDFactorizer(DataModel dataModel, int numFeatures, int numIterations) throws TasteException {
        this(dataModel, numFeatures, DEFAULT_LEARNING_RATE, DEFAULT_REGULARIZATION, DEFAULT_RANDOM_NOISE,
                numIterations);
    }

    public TevfikSVDFactorizer(DataModel dataModel, int numFeatures, double learningRate, double regularization,
            double randomNoise, int numIterations) throws TasteException {
        super(dataModel);
        this.dataModel = dataModel;
        this.numFeatures = numFeatures;
        this.numIterations = numIterations;
        this.learningRate = learningRate;
        this.regularization = regularization;
        this.randomNoise = randomNoise;

    }

    @Override
    public Factorization factorize() throws TasteException {
        Random random = RandomUtils.getRandom();
        userVectors = new double[dataModel.getNumUsers()][numFeatures];
        itemVectors = new double[dataModel.getNumItems()][numFeatures];

        double average = getAveragePreference();

        double prefInterval = dataModel.getMaxPreference() - dataModel.getMinPreference();
        defaultValue = Math.sqrt((average - prefInterval * 0.1) / numFeatures);
        
        interval = prefInterval * 0.1 / numFeatures;

        for (int feature = 0; feature < numFeatures; feature++) {
            for (int userIndex = 0; userIndex < dataModel.getNumUsers(); userIndex++) {
                userVectors[userIndex][feature] = defaultValue + (random.nextDouble() - 0.5) * interval * randomNoise;
            }
            for (int itemIndex = 0; itemIndex < dataModel.getNumItems(); itemIndex++) {
                itemVectors[itemIndex][feature] = defaultValue + (random.nextDouble() - 0.5) * interval * randomNoise;
            }
        }

        preferences = Lists.newArrayListWithCapacity(dataModel.getNumUsers());
        getPreferences();

        for (int iter = 0; iter < numIterations; iter++) {
            double avgError = 0;
            double avgAbsError = 0;
            for (GenericPreference pref : preferences) {
                int useridx = userIndex(pref.getUserID());
                int itemidx = itemIndex(pref.getItemID());
                //System.out.println(i+" "+user+" "+item);
                double error = pref.getValue() - predictRating(useridx, itemidx);

                double[] temp = userVectors[useridx].clone();

                for (int j = 0; j < numFeatures; j++) {
                    userVectors[useridx][j] = userVectors[useridx][j] + DEFAULT_LEARNING_RATE * (error * itemVectors[itemidx][j] - DEFAULT_REGULARIZATION * userVectors[useridx][j]);
                    itemVectors[itemidx][j] = itemVectors[itemidx][j] + DEFAULT_LEARNING_RATE * (error * temp[j] - DEFAULT_REGULARIZATION * itemVectors[itemidx][j]);
                }
                avgAbsError = avgAbsError + Math.abs(error);
                avgError = avgError + error;
                //		if (i % 1000 == 0)
                //			System.out.println("AvgError: "+avgAbsError/(i+1));
            }
            avgError = avgError / preferences.size();
            System.out.println("AvgError (Training set): " + avgAbsError / preferences.size());
        }

        return createFactorization(userVectors, itemVectors);
    }

    private double predictRating(int userIndex, int itemIndex) {
        double sum = 0;
        for (int i = 0; i < numFeatures; i++) {
            sum = sum + userVectors[userIndex][i] * itemVectors[itemIndex][i];
        }

        return sum;
    }

    double getAveragePreference() throws TasteException {
        RunningAverage average = new FullRunningAverage();
        LongPrimitiveIterator userIDs = dataModel.getUserIDs();
        while (userIDs.hasNext()) {
            for (Preference preference : dataModel.getPreferencesFromUser(userIDs.nextLong())) {
                average.addDatum(preference.getValue());
            }
        }
        return average.getAverage();
    }

    private void getPreferences() throws TasteException {
        preferences.clear();
        LongPrimitiveIterator userIDs = dataModel.getUserIDs();
        while (userIDs.hasNext()) {
            for (Preference pref : dataModel.getPreferencesFromUser(userIDs.nextLong())) {
                preferences.add(new GenericPreference(pref.getUserID(), pref.getItemID(), pref.getValue()));
            }
        }
    }
}

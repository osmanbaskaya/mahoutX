package com.eniyitavsiye.mahoutx.svdextension.tag.simitemfact;




import com.google.common.base.Preconditions;
import java.util.List;
import java.util.Random;

import com.google.common.collect.Lists;
import java.util.Collections;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.example.kddcup.track1.svd.ParallelArraysSGDFactorizer;
import org.apache.mahout.cf.taste.impl.common.FullRunningAverage;
import org.apache.mahout.cf.taste.impl.common.LongPrimitiveIterator;
import org.apache.mahout.cf.taste.impl.common.RunningAverage;
import org.apache.mahout.cf.taste.impl.model.GenericPreference;
import org.apache.mahout.cf.taste.impl.recommender.svd.AbstractFactorizer;
import org.apache.mahout.cf.taste.impl.recommender.svd.ExpectationMaximizationSVDFactorizer;
import org.apache.mahout.cf.taste.impl.recommender.svd.Factorization;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.model.Preference;
import org.apache.mahout.common.RandomUtils;
import org.apache.mahout.math.DenseVector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Calculates the SVD using an Expectation Maximization algorithm. */
public final class SimultaneousTagsRatingsFactorizer extends AbstractFactorizer {

  private static final Logger log = LoggerFactory.getLogger(ExpectationMaximizationSVDFactorizer.class);

  private final double learningRate;
  /** Parameter used to prevent overfitting. 0.02 is a good value. */
  private final double preventOverfitting;
  /** number of features used to compute this factorization */
  private final int numFeatures;
  /** number of iterations */
  private final int numIterations;
  private final double randomNoise;
  /** user singular vectors */
  private double[][] U;
  /** item singular vectors */
  private double[][] I;
  private double[][] W;
  private final DataModel dataModel;
  private List<SVDPreference> cachedPreferences;
  private double defaultValue;
  private double interval;

  public SimultaneousTagsRatingsFactorizer(DataModel dataModel,
                                              int numFeatures,
                                              int numIterations) throws TasteException {
    // use the default parameters from the old SVDRecommender implementation
    this(dataModel, numFeatures, 0.005, 0.02, 0.005, numIterations);
  }

  public SimultaneousTagsRatingsFactorizer(DataModel dataModel,
                                              int numFeatures,
                                              double learningRate,
                                              double preventOverfitting,
                                              double randomNoise,
                                              int numIterations) throws TasteException {
    super(dataModel);
    this.dataModel = dataModel;
    this.numFeatures = numFeatures;
    this.numIterations = numIterations;

    this.learningRate = learningRate;
    this.preventOverfitting = preventOverfitting;
    this.randomNoise = randomNoise;

  }

  @Override
  public Factorization factorize() throws TasteException {
    Random random = RandomUtils.getRandom();
    U = new double[dataModel.getNumUsers()][numFeatures];
    I = new double[dataModel.getNumItems()][numFeatures];
		//FIXME create W with lines as many as number of tags.
    W = new double[dataModel.getNumItems()][numFeatures];


    double average = getAveragePreference();

    double prefInterval = dataModel.getMaxPreference() - dataModel.getMinPreference();
    defaultValue = Math.sqrt((average - prefInterval * 0.1) / numFeatures);
    interval = prefInterval * 0.1 / numFeatures;

    for (int feature = 0; feature < numFeatures; feature++) {
      for (int userIndex = 0; userIndex < dataModel.getNumUsers(); userIndex++) {
        U[userIndex][feature] = defaultValue + (random.nextDouble() - 0.5) * interval * randomNoise;
      }
      for (int itemIndex = 0; itemIndex < dataModel.getNumItems(); itemIndex++) {
        I[itemIndex][feature] = defaultValue + (random.nextDouble() - 0.5) * interval * randomNoise;
      }
			//FIXME create W with lines as many as number of tags.
      for (int tagIndex = 0; tagIndex < dataModel.getNumItems(); tagIndex++) {
        W[tagIndex][feature] = defaultValue + (random.nextDouble() - 0.5) * interval * randomNoise;
      }
    }
    cachedPreferences = Lists.newArrayListWithCapacity(dataModel.getNumUsers());
    cachePreferences();
    double rmse = dataModel.getMaxPreference() - dataModel.getMinPreference();
    for (int ii = 0; ii < numFeatures; ii++) {
      Collections.shuffle(cachedPreferences, random);
      for (int i = 0; i < numIterations; i++) {
        double err = 0.0;
        for (SVDPreference pref : cachedPreferences) {
          int useridx = userIndex(pref.getUserID());
          int itemidx = itemIndex(pref.getItemID());
          err += Math.pow(train(useridx, itemidx, ii, pref), 2.0);
        }
        rmse = Math.sqrt(err / cachedPreferences.size());
      }
      if (ii < numFeatures - 1) {
        for (SVDPreference pref : cachedPreferences) {
          int useridx = userIndex(pref.getUserID());
          int itemidx = itemIndex(pref.getItemID());
          buildCache(useridx, itemidx, ii, pref);
        }
      }
      log.info("Finished training feature {} with RMSE {}.", ii, rmse);
    }
    return createFactorization(U, I);
  }

  double getAveragePreference() throws TasteException {
    RunningAverage average = new FullRunningAverage();
    LongPrimitiveIterator it = dataModel.getUserIDs();
    while (it.hasNext()) {
      for (Preference pref : dataModel.getPreferencesFromUser(it.nextLong())) {
        average.addDatum(pref.getValue());
      }
    }
    return average.getAverage();
  }

  private double train(int i, int j, int f, SVDPreference pref) {
    double[] uI = U[i];
    double[] iJ = I[j];
    double[] wI = W[i];
		double uDotI = new DenseVector(uI).dot(new DenseVector(iJ));
		double wDotI = new DenseVector(wI).dot(new DenseVector(iJ));
		double rErr = uDotI - pref.getValue();
		/*
		double tErr = wDotI - pref.getTagValue();
		uI[f] += rErr * iJ[f] + preventOverfitting * uI[f];
		iJ[f] += rErr * uI[f] + kappa * tErr * wI[f] + preventOverfitting * iJ[f];
		wI[f] += kappa * tErr * iJ[f] + preventOverfitting * wI[f];

    leftVectorI[f] += learningRate * (err * rightVectorJ[f] - preventOverfitting * leftVectorI[f]);
    rightVectorJ[f] += learningRate * (err * leftVectorIF - preventOverfitting * rightVectorJ[f]);
    return err;
		*/
		return 0;
  }

  private void buildCache(int i, int j, int k, SVDPreference pref) {
    pref.setCache(predictRating(i, j, k, pref, false));
  }

  private double predictRating(int i, int j, int f, SVDPreference pref, boolean trailing) {
    float minPreference = dataModel.getMinPreference();
    float maxPreference = dataModel.getMaxPreference();
    double sum = pref.getCache();
    sum += U[i][f] * I[j][f];
    if (trailing) {
      sum += (numFeatures - f - 1) * (defaultValue + interval) * (defaultValue + interval);
      if (sum > maxPreference) {
        sum = maxPreference;
      } else if (sum < minPreference) {
        sum = minPreference;
      }
    }
    return sum;
  }

  private void cachePreferences() throws TasteException {
    cachedPreferences.clear();
    LongPrimitiveIterator it = dataModel.getUserIDs();
    while (it.hasNext()) {
      for (Preference pref : dataModel.getPreferencesFromUser(it.nextLong())) {
        cachedPreferences.add(new SVDPreference(pref.getUserID(), pref.getItemID(), pref.getValue(), 0.0));
      }
    }
  }

	final class SVDPreference extends GenericPreference {

		private double cache;

		SVDPreference(long userID, long itemID, float value, double cache) {
			super(userID, itemID, value);
			setCache(cache);
		}

		public double getCache() {
			return cache;
		}

		public void setCache(double value) {
			Preconditions.checkArgument(!Double.isNaN(value), "NaN cache value");
			this.cache = value;
		}

	}

}

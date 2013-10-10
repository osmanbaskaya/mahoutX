/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.eniyitavsiye.mahoutx.common.evaluation;



import com.eniyitavsiye.mahoutx.svd.TevfikSVDFactorizer;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.eval.RecommenderBuilder;
import org.apache.mahout.cf.taste.eval.RecommenderEvaluator;
import org.apache.mahout.cf.taste.impl.eval.AverageAbsoluteDifferenceRecommenderEvaluator;
import org.apache.mahout.cf.taste.impl.model.file.FileDataModel;
import org.apache.mahout.cf.taste.impl.neighborhood.NearestNUserNeighborhood;
import org.apache.mahout.cf.taste.impl.recommender.GenericUserBasedRecommender;
import org.apache.mahout.cf.taste.impl.similarity.PearsonCorrelationSimilarity;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.neighborhood.UserNeighborhood;
import org.apache.mahout.cf.taste.recommender.Recommender;
import org.apache.mahout.cf.taste.similarity.UserSimilarity;
import org.apache.mahout.common.RandomUtils;

import java.io.File;
import org.apache.mahout.cf.taste.eval.IRStatistics;
import org.apache.mahout.cf.taste.example.grouplens.GroupLensDataModel;
import org.apache.mahout.cf.taste.impl.recommender.svd.SVDRecommender;
import org.apache.mahout.cf.taste.impl.recommender.svd.FunkSVDFactorizer;
import org.apache.mahout.cf.taste.impl.recommender.svd.ParallelSGDFactorizer;
import org.apache.mahout.cf.taste.impl.recommender.svd.SVDPlusPlusFactorizer;

class EvaluatorIntro {

  private EvaluatorIntro() {
  }

  public static void main(String[] args) throws Exception {
    //RandomUtils.useTestSeed();
    //DataModel model = new FileDataModel(new File("/home/tevfik/Documents/workspace/datasets/ml-1m/intro.csv"));
    //DataModel model = new GroupLensDataModel(new File("/home/tevfik/Documents/workspace/datasets/ml-1m/ratings.dat"));
    DataModel model = new GroupLensDataModel(new File("/home/tevfik/datasets/ml-1m/ratings.dat"));

    //RecommenderEvaluator evaluator =
    //  new AverageAbsoluteDifferenceRecommenderEvaluator();
    // Build the same recommender for testing that we did last time:
    RecommenderBuilder recommenderBuilder = new RecommenderBuilder() {
      @Override
      public Recommender buildRecommender(DataModel model) throws TasteException {
     //   UserSimilarity similarity = new PearsonCorrelationSimilarity(model);
     //   UserNeighborhood neighborhood =
     //     new NearestNUserNeighborhood(2, similarity, model);
     //   return new GenericUserBasedRecommender(model, neighborhood, similarity);
          //return new SVDRecommender(model, new FunkSVDFactorizer(model, 10, 10) );
          return new SVDRecommender(model, new ParallelSGDFactorizer(model, 20, 0.02, 20) );
          //return new SVDRecommender(model, new TevfikSVDFactorizer(model, 20, 100) );
          //return new SVDRecommender(model, new SVDPlusPlusFactorizer(model, 10, 10) );
      }
    };
    // Use 70% of the data to train; test using the other 30%.
    //double score = evaluator.evaluate(recommenderBuilder, null, model, 0.7, 1.0);
    KorenIRStatsEvaluator kirse = new KorenIRStatsEvaluator(
                                0.7, 1000);
                        IRStatistics rs = kirse.evaluate(
                                recommenderBuilder, null,
                                model, null,
                                20, 5, 0.01);
                        //file.write("Recall:" + recallResult+"\n");
    //System.out.println("MAE: "+score);
    
  }
}
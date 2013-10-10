/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.eniyitavsiye.mahoutx.common.evaluation;

import com.eniyitavsiye.mahoutx.common.Util;
import com.eniyitavsiye.mahoutx.svdextension.online.OnlineSVDRecommender;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.eval.RecommenderBuilder;
import org.apache.mahout.cf.taste.eval.RecommenderEvaluator;
import org.apache.mahout.cf.taste.example.grouplens.GroupLensDataModel;
import org.apache.mahout.cf.taste.impl.eval.AverageAbsoluteDifferenceRecommenderEvaluator;
import org.apache.mahout.cf.taste.impl.recommender.AllUnknownItemsCandidateItemsStrategy;
import org.apache.mahout.cf.taste.impl.recommender.svd.ParallelSGDFactorizer;
import org.apache.mahout.cf.taste.impl.recommender.svd.SVDPlusPlusFactorizer;
import org.apache.mahout.cf.taste.impl.recommender.svd.SVDRecommender;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.recommender.Recommender;

/**
 *
 * @author tevfik
 */
public class GeneralExperiment {

    private static final Logger log = Logger.getLogger(GeneralExperiment.class.getName());

    private GeneralExperiment() {
    }

    public static void main(String[] args) throws Exception {
        BufferedWriter file;

        try {
            file = new BufferedWriter(new FileWriter("/home/tevfik/Documents/log.txt"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        DataModel model = new GroupLensDataModel(new File("/home/tevfik/datasets/ml-1m/ratings.dat"));

        // Build the same recommender for testing that we did last time:



        // log.log(Level.INFO, "runGeneralExperiment method starts");
        String nFactorsArrayString = "300";
        String nIterationsArrayString = "0,10,30,50,70,90";
        String foldInIterationArrayString = "30";
        int maxRep = 1;
        HashMap<String, String> results = new HashMap<>();
        int[] nFactorsArray = Util.getIntArray(nFactorsArrayString);
        for (final int nFactors : nFactorsArray) {
            int[] nIterationsArray = Util.getIntArray(nIterationsArrayString);
            for (final int nIterations : nIterationsArray) {
                int[] foldInIterationArray = Util.getIntArray(foldInIterationArrayString);
                for (int foldInIteration : foldInIterationArray) {
                    for (int rep = 0; rep < maxRep; rep++) {
                        log.log(Level.INFO, "\n nFactors:" + nFactors + " / " + Arrays.toString(nFactorsArray)
                                + "\n nIterations:" + nIterations + " / " + Arrays.toString(nIterationsArray)
                                + "\n foldInIteration:" + foldInIteration + " / " + Arrays.toString(foldInIterationArray)
                                + "\n rep:" + rep + " / " + maxRep + "\n");
                        //setModelConfiguration(context, null, nFactors,
                        //        nIterations, null, foldInIteration, null, null);
                        RecommenderBuilder recommenderBuilder = new RecommenderBuilder() {
                            @Override
                            public Recommender buildRecommender(DataModel model) throws TasteException {
                                //   UserSimilarity similarity = new PearsonCorrelationSimilarity(model);
                                //   UserNeighborhood neighborhood =
                                //     new NearestNUserNeighborhood(2, similarity, model);
                                //   return new GenericUserBasedRecommender(model, neighborhood, similarity);
                                //return new SVDRecommender(model, new FunkSVDFactorizer(model, 10, 10) );
                                //return new SVDRecommender(model, new ParallelSGDFactorizer(model, 10, 0.02, 10));
                                return new OnlineSVDRecommender(model, new ParallelSGDFactorizer(model, nFactors, 0.02, nIterations), new AllUnknownItemsCandidateItemsStrategy());
                                //return new SVDRecommender(model, new TevfikSVDFactorizer(model, 20, 100) );
                                //return new SVDRecommender(model, new SVDPlusPlusFactorizer(model, 10, 10));
                            }
                        };


                        KorenIRStatsWithFoldInEvaluator kirse = new KorenIRStatsWithFoldInEvaluator(
                                0.7, 0.01, 300);
                        String recallResult = kirse.evaluateFoldInRecall(
                                recommenderBuilder, null,
                                model, null,
                                20, 4, 1);
                        //file.write("Recall:" + recallResult+"\n");

                        kirse = new KorenIRStatsWithFoldInEvaluator(
                                0.7, 0.01, 0);
                        String diversityResult = kirse
                                .evaluateFoldInAggregateDiversity(
                                recommenderBuilder, null,
                                model, null,
                                20, 1);

                        //file.write("Diversity:" + diversityResult+"\n");

                        RecommenderEvaluator evaluator = new AverageAbsoluteDifferenceRecommenderEvaluator();
                        double score = evaluator.evaluate(
                                recommenderBuilder, null,
                                model,
                                0.7, 1);
                        //file.write("MAE:" + score+"\n");
                        results.put(nFactors + ":" + nIterations + ":"
                                + foldInIteration + ":" + rep, recallResult
                                + ":" + diversityResult + ":" + score);
                    }
                }
            }
        }

        file.write("runGeneralExperiment results starts" + "\n");
        for (String key : results.keySet()) {
            String result = results.get(key);
            file.write(key + ":" + result + "\n");
        }
        file.write("runGeneralExperiment results ends" + "\n");
        file.close();
    }
}

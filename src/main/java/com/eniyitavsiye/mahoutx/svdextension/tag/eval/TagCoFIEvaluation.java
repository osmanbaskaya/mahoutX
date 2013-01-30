/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.eniyitavsiye.mahoutx.svdextension.tag.eval;

import com.eniyitavsiye.mahoutx.svdextension.tag.tagcofi.TagCoFiFactorizer;
import com.eniyitavsiye.mahoutx.tags.TaggingData;
import com.eniyitavsiye.mahoutx.tags.TaggingDataIO_ML;
import java.io.File;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.eval.RecommenderBuilder;
import org.apache.mahout.cf.taste.impl.eval.AverageAbsoluteDifferenceRecommenderEvaluator;
import org.apache.mahout.cf.taste.impl.model.file.FileDataModel;
import org.apache.mahout.cf.taste.impl.recommender.svd.SVDRecommender;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.recommender.Recommender;

/**
 *
 * @author ceyhun
 */
public class TagCoFIEvaluation {

	public static void main(String[] args) throws Exception {
		
		DataModel model;
		final TaggingData taggingData;
		TagCoFiFactorizer factorizer;
		File root = new java.io.File("/home/ceyhun/Dropbox/Projects/doctoral/dataset/MovieLens/10M100K");
		model = new FileDataModel(new File(root, "ratings.csv"));
		TaggingDataIO_ML instance = new TaggingDataIO_ML(new File(root, "tags.dat"));
		factorizer = new TagCoFiFactorizer(model, 
						TagCoFiFactorizer.SimilarityCalculator.COSINE, 10, 10, 0.1, 0.1, 0.1);
		taggingData = instance.readTaggingData(factorizer);

		double mae = new AverageAbsoluteDifferenceRecommenderEvaluator()
						.evaluate(new RecommenderBuilder() {

			@Override
			public Recommender buildRecommender(DataModel dataModel) 
							throws TasteException {
				TagCoFiFactorizer factorizer = new TagCoFiFactorizer(dataModel, 
						TagCoFiFactorizer.SimilarityCalculator.COSINE, 10, 10, 0.1, 0.1, 0.1);
				factorizer.setUserTagMatrix(taggingData.getUserTaggingCountMatrix());
				return new SVDRecommender(dataModel, factorizer);
			}
		}, null, model, 0.8, 0.1);

		System.out.printf("MAE = %6.4f\n", mae);
	}
	
}

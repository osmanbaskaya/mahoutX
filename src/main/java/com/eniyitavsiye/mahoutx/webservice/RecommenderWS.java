package com.eniyitavsiye.mahoutx.webservice;

import com.eniyitavsiye.mahoutx.common.FilterIDsRescorer;
import com.eniyitavsiye.mahoutx.common.LimitMySQLJDBCDataModel;
import com.eniyitavsiye.mahoutx.common.MutableGenericDataModel;
import com.eniyitavsiye.mahoutx.common.evaluation.KorenIRStatsEvaluator;
import com.eniyitavsiye.mahoutx.common.evaluation.KorenIRStatsWithFoldInEvaluator;
import com.eniyitavsiye.mahoutx.db.DBUtil;
import com.eniyitavsiye.mahoutx.svdextension.FactorizationCachingFactorizer;
import com.eniyitavsiye.mahoutx.svdextension.online.OnlineSVDRecommender;
import org.apache.mahout.cf.taste.common.NoSuchItemException;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.common.TopK;
import org.apache.mahout.cf.taste.eval.RecommenderBuilder;
import org.apache.mahout.cf.taste.eval.RecommenderEvaluator;
import org.apache.mahout.cf.taste.example.kddcup.track1.svd.ParallelArraysSGDFactorizer;
import org.apache.mahout.cf.taste.impl.common.FastIDSet;
import org.apache.mahout.cf.taste.impl.eval.AverageAbsoluteDifferenceRecommenderEvaluator;
import org.apache.mahout.cf.taste.impl.model.file.FileDataModel;
import org.apache.mahout.cf.taste.impl.model.jdbc.ConnectionPoolDataSource;
import org.apache.mahout.cf.taste.impl.neighborhood.NearestNUserNeighborhood;
import org.apache.mahout.cf.taste.impl.neighborhood.ThresholdUserNeighborhood;
import org.apache.mahout.cf.taste.impl.recommender.AllUnknownItemsCandidateItemsStrategy;
import org.apache.mahout.cf.taste.impl.recommender.GenericItemBasedRecommender;
import org.apache.mahout.cf.taste.impl.recommender.GenericUserBasedRecommender;
import org.apache.mahout.cf.taste.impl.recommender.svd.ALSWRFactorizer;
import org.apache.mahout.cf.taste.impl.recommender.svd.ExpectationMaximizationSVDFactorizer;
import org.apache.mahout.cf.taste.impl.recommender.svd.Factorization;
import org.apache.mahout.cf.taste.impl.recommender.svd.Factorizer;
import org.apache.mahout.cf.taste.impl.similarity.CachingItemSimilarity;
import org.apache.mahout.cf.taste.impl.similarity.CachingUserSimilarity;
import org.apache.mahout.cf.taste.impl.similarity.UncenteredCosineSimilarity;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.neighborhood.UserNeighborhood;
import org.apache.mahout.cf.taste.recommender.CandidateItemsStrategy;
import org.apache.mahout.cf.taste.recommender.MostSimilarItemsCandidateItemsStrategy;
import org.apache.mahout.cf.taste.recommender.RecommendedItem;
import org.apache.mahout.cf.taste.recommender.Recommender;
import org.apache.mahout.cf.taste.similarity.ItemSimilarity;
import org.apache.mahout.cf.taste.similarity.UserSimilarity;
import org.apache.mahout.common.distance.CosineDistanceMeasure;

import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebService;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebService(serviceName = "RecommenderWS")
public class RecommenderWS {

  private static HashMap<String, Recommender> predictor;
  private static HashMap<String, RecommenderBuilder> builders;
  private static HashMap<String, FactorizationCachingFactorizer> factorizationCaches;
  private static HashMap<String, DataModel> inMemoryDataModels;
  private static HashMap<String, DataModel> dbBackedDataModels;
  private static HashMap<String, ContextState> contextStates;
  private static HashMap<String, ArrayList<Float>> onlineMaeHistories;
  private static final Logger log = Logger.getLogger(RecommenderWS.class.getName());

  static {
    predictor = new HashMap<>();
    builders = new HashMap<>();
    factorizationCaches = new HashMap<>();
    inMemoryDataModels = new HashMap<>();
    dbBackedDataModels = new HashMap<>();
    contextStates = new HashMap<>();
    onlineMaeHistories = new HashMap<>();
  }

  @WebMethod(operationName = "isModelAlive")
  public boolean isModelAlive(@WebParam(name = "context") String context) {
    return contextStates.get(context) == ContextState.READY;
  }

  @WebMethod(operationName = "fetchData")
  public String fetchData(@WebParam(name = "context") String context,
                          @WebParam(name = "dataFraction") Double dataFraction) {
    if (dataFraction == null) {
      dataFraction = 1.0;
    }
    ContextState current = contextStates.get(context);
    try {
      log.log(Level.INFO, "Beginning to fetch data for context {0}.",
              context);
      contextStates.put(context, ContextState.FETCHING);
      DBUtil dbUtil = new DBUtil();
      LimitMySQLJDBCDataModel dbBackedModel = new LimitMySQLJDBCDataModel(
              new ConnectionPoolDataSource(dbUtil.getDataSource()),
              context + "_rating", "user_id", "item_id", "rating", null, dataFraction);
      DataModel inMemoryModel = new MutableGenericDataModel(dbBackedModel.exportWithPrefs());
      //ReplaceableDataModel replaceableModel = new ReplaceableDataModel(reloadModel);
      inMemoryDataModels.put(context, inMemoryModel);
      dbBackedDataModels.put(context, dbBackedModel);
      contextStates.put(context, ContextState.FETCHED);
      log.log(Level.INFO, "Data fetch for context {0} is completed.", context);
    } catch (Exception ex) {
      log.log(Level.SEVERE, null, ex);
      contextStates.put(context, current);
      return "error";
    }
    return "done";
  }

  @WebMethod(operationName = "evaluateRecommenderRecall")
  public String evaluateRecommenderRecall(
          @WebParam(name = "context") final String context,
          @WebParam(name = "nUnratedItems") final int nUnratedItems,
          @WebParam(name = "listSize") final int listSize,
          @WebParam(name = "trainingPercent") final double trainingPercent,
          @WebParam(name = "relevanceThreshold") final double relevanceThreshold,
          @WebParam(name = "evalPercent") final double evalPercent,
          @WebParam(name = "foldInUserPercentage") Double foldInUserPercentage)
          throws TasteException {
    //TODO maybe later check if no configuration exists, or no datamodel available, handle exceptions and so on...

    String result;
    if (foldInUserPercentage != null) {
      KorenIRStatsWithFoldInEvaluator kirse = new KorenIRStatsWithFoldInEvaluator(
              trainingPercent, foldInUserPercentage, nUnratedItems);
      result = kirse.evaluateFoldIn(builders.get(context), null, inMemoryDataModels.get(context), null,
              listSize, relevanceThreshold, evalPercent);
    } else {
      KorenIRStatsEvaluator kirse = new KorenIRStatsEvaluator(trainingPercent, nUnratedItems);
      result = kirse.evaluate(builders.get(context), null, inMemoryDataModels.get(context), null,
              listSize, relevanceThreshold, evalPercent).getRecall() + "";
    }
    log.log(Level.INFO, "Recall result: {0}", result);
    return result;
  }

  @WebMethod(operationName = "evaluateRecommenderMae")
  public String evaluateRecommenderMae(
          @WebParam(name = "context") final String context,
          @WebParam(name = "trainingPercent") final double trainingPercent,
          @WebParam(name = "evalPercent") final double evalPercent) {
    try {
      RecommenderBuilder builder = builders.get(context);
      RecommenderEvaluator evaluator = new AverageAbsoluteDifferenceRecommenderEvaluator();
      double score = evaluator.evaluate(builder, null, inMemoryDataModels.get(context), trainingPercent, evalPercent);
      log.log(Level.INFO, "MAE is {0}.", score);
      return "" + score;
    } catch (Exception ex) {
      log.log(Level.SEVERE, null, ex);
      return ex.getMessage();
    }
  }

  @WebMethod(operationName = "buildModel")
  public String buildModel(
          @WebParam(name = "context") String context) {
    /*
		 if (!(model.getDelegate() instanceof ReloadFromJDBCDataModel)) {
		 String message = String.format(
		 "Cannot build without in-memory data! " +
		 "Current data delegate model is : %s.", model.getDelegate().getClass());
		 log.log(Level.SEVERE, message);
		 return message;
		 }
		 */
    ContextState currentState = contextStates.get(context);
    if (!(currentState == ContextState.CONFIGURED
            || currentState == ContextState.READY)) {
      return "Illegal context state! " + currentState;
    }
    contextStates.put(context, ContextState.BUILDING);
    DataModel model = inMemoryDataModels.get(context);

    RecommenderBuilder builder = builders.get(context);

    try {
      Recommender osv = builder.buildRecommender(model);

      predictor.put(context, osv);
      if (osv instanceof OnlineSVDRecommender) {
        factorizationCaches.put(context,
                ((OnlineSVDRecommender) osv).getFactorizationCachingFactorizer());
      }
      contextStates.put(context, ContextState.READY);
      onlineMaeHistories.put(context, new ArrayList<Float>());
      return "done";
    } catch (TasteException e) {
      // return to old state, whatever it is.
      log.log(Level.WARNING, "Some exception occurred while building recommender.", e);
      contextStates.put(context, currentState);
      return "error";
    }
  }

  @WebMethod(operationName = "setNeighborhoodModelConfiguration")
  public String setNeighborhoodModelConfiguration(
          @WebParam(name = "context") String context,
          @WebParam(name = "isBasedOnItems") final boolean isBasedOnItems,
          @WebParam(name = "similarityType") final String similarityType,
          @WebParam(name = "shouldCacheSimilarities") final boolean shouldCacheSimilarities,
          @WebParam(name = "candidateItemStrategy") final String candidateItemStrategy,
          @WebParam(name = "similarityThreshold") final Double similarityThreshold,
          @WebParam(name = "nMostSimilarUsers") final Integer nMostSimilarUsers,
          @WebParam(name = "neighborhoodType") final String neighborhoodType) {

    RecommenderBuilder builder = new RecommenderBuilder() {
      @Override
      public Recommender buildRecommender(DataModel model) throws TasteException {
        Object similarity;
        try {
          similarity =
                  Class.forName("org.apache.mahout.cf.taste.impl.similarity." + similarityType)
                          .getConstructor(DataModel.class)
                          .newInstance(model);
        } catch (ClassNotFoundException | NoSuchMethodException
                | SecurityException | InstantiationException
                | IllegalAccessException | IllegalArgumentException
                | InvocationTargetException e) {
          log.log(Level.WARNING, "Could not find class or constructor for class: {0}\n"
                  + "Caused by: {1}.\n"
                  + "Using UncenteredCosineSimilarity.",
                  new Object[]{similarityType, e});
          similarity = new UncenteredCosineSimilarity(model);
        }
        if (isBasedOnItems) {
          ItemSimilarity sim =
                  shouldCacheSimilarities
                          ? new CachingItemSimilarity((ItemSimilarity) similarity, model)
                          : (ItemSimilarity) similarity;
          String cs = candidateItemStrategy;
          if (cs == null) {
            cs = "AllUnknownItemsCandidateItemsStrategy";
          }
          CandidateItemsStrategy strategy;
          try {
            strategy = (CandidateItemsStrategy)
                    Class.forName("org.apache.mahout.cf.taste.impl.recommender." + cs)
                            .newInstance();
          } catch (ClassNotFoundException | InstantiationException
                  | IllegalAccessException e) {
            log.log(Level.WARNING, "Could not instantiate strategy: {0}. "
                    + "Using default AllUnkownItemsCandidateItemsStrategy",
                    candidateItemStrategy);
            log.log(Level.WARNING, null, e);
            strategy = new AllUnknownItemsCandidateItemsStrategy();
          }
          return new GenericItemBasedRecommender(model, sim, strategy,
                  (MostSimilarItemsCandidateItemsStrategy) strategy);
        } else {
          UserNeighborhood neighborhood;
          UserSimilarity sim =
                  shouldCacheSimilarities
                          ? new CachingUserSimilarity((UserSimilarity) similarity, model)
                          : (UserSimilarity) similarity;
          switch (neighborhoodType) {
            case "NearestNUserNeighborhood":
              neighborhood = new NearestNUserNeighborhood(
                      nMostSimilarUsers, similarityThreshold, sim, model);
              break;
            default:
              log.log(Level.WARNING, "Not a known UserNeighborhood class name: {0}.\n"
                      + "Using ThresholdUserNeighborhood.", neighborhoodType);
            case "ThresholdUserNeighborhood":
              neighborhood = new ThresholdUserNeighborhood(similarityThreshold, sim, model);
          }
          return new GenericUserBasedRecommender(model, neighborhood, sim);
        }
      }
    };

    log.log(Level.INFO, "Neighborhood based configuration set.");

    builders.put(context, builder);
    contextStates.put(context, ContextState.CONFIGURED);
    return "done";
  }

  @WebMethod(operationName = "setModelConfiguration")
  public String setModelConfiguration(
          @WebParam(name = "context") String context,
          @WebParam(name = "factorizerName") final String factorizerName,
          @WebParam(name = "nFactors") final int nFactors,
          @WebParam(name = "nIterations") final int nIterations,
          @WebParam(name = "candidateItemStrategy") final String candidateItemStrategy) {


    RecommenderBuilder builder = new RecommenderBuilder() {
      @Override
      public Recommender buildRecommender(DataModel dataModel) throws TasteException {
        String fn = factorizerName == null ? "" : factorizerName;
        log.log(Level.INFO, "buildItemSimilarityMatrix starts with {0} factorizer.", fn);
        Factorizer factorizer;
        switch (fn) {
          case "ExpectationMaximizationSVDFactorizer":
            factorizer = new ExpectationMaximizationSVDFactorizer(dataModel, nFactors, nIterations);
            break;
          case "ALSWRFactorizer":
            factorizer = new ALSWRFactorizer(dataModel, nFactors, 0.005, nIterations);
            break;
          default:
            factorizer = new ParallelArraysSGDFactorizer(dataModel, nFactors, nIterations);
            break;
        }
        FactorizationCachingFactorizer cachingFactorizer =
                new FactorizationCachingFactorizer(factorizer);

        String cs = candidateItemStrategy;
        if (cs == null) {
          cs = "AllUnknownItemsCandidateItemsStrategy";
        }
        CandidateItemsStrategy strategy;
        try {
          strategy = (CandidateItemsStrategy)
                  Class.forName("org.apache.mahout.cf.taste.impl.recommender." + cs)
                          .newInstance();
        } catch (ClassNotFoundException | InstantiationException
                | IllegalAccessException e) {
          log.log(Level.WARNING, "Could not instantiate strategy: {0}. "
                  + "Using default AllUnkownItemsCandidateItemsStrategy",
                  candidateItemStrategy);
          log.log(Level.WARNING, null, e);
          strategy = new AllUnknownItemsCandidateItemsStrategy();
        }

        return new OnlineSVDRecommender(dataModel, cachingFactorizer, strategy);
      }
    };
    log.log(Level.INFO, "Configuration set.");


    builders.put(context, builder);
    contextStates.put(context, ContextState.CONFIGURED);
    return "done";
  }

  @WebMethod(operationName = "getRecommendationListPaginated")
  public String[] getRecommendationListPaginated(
          @WebParam(name = "context") String context,
          @WebParam(name = "userId") long userId,
          @WebParam(name = "tagstring") String tagstring,
          @WebParam(name = "offset") int offset,
          @WebParam(name = "length") int length) {
    if (tagstring == null) {
      tagstring = "";
    }
    tagstring = tagstring.trim();

    try {
      log.log(Level.INFO, "Entering getRecommendationList for user {0} in context {1}.",
              new Object[]{userId, context});
      List<RecommendedItem> recommendations;
      if (!tagstring.equals("")) {
        DBUtil dbUtil = new DBUtil();
        Collection<Long> specificItemIDsList = dbUtil.getItems(context, tagstring.split(","));
        FastIDSet specificItemIDs = new FastIDSet(specificItemIDsList.size());
        for (Long id : specificItemIDsList) {
          specificItemIDs.add(id);
        }
        FilterIDsRescorer filterIDsRescorer = new FilterIDsRescorer(specificItemIDs);
        recommendations = predictor.get(context).recommend(userId, offset + length, filterIDsRescorer);
      } else {
        recommendations = predictor.get(context).recommend(userId, offset + length);
      }
      long[] candidateItemIds =
              new AllUnknownItemsCandidateItemsStrategy()
                      .getCandidateItems(userId,
                              inMemoryDataModels.get(context).getPreferencesFromUser(userId),
                              inMemoryDataModels.get(context)
                      ).toArray();
      String[] result = new String[candidateItemIds.length];
      for (int i = 0; i < result.length; ++i) {
        long itemId = candidateItemIds[i];
        result[i] = String.format("%d: uid:%d, iid:%d, prediction:%f\n",
                (i + 1), userId, itemId,
                estimatePreference(context, userId, itemId));
      }

      //log.log(Level.FINE, "Predictions:\n{0}\n\n\n\n", Arrays.toString(result));

      //log.log(Level.FINE, "Recommendation list: {0}", recommendations);

      String[] list = new String[Math.min(length, recommendations.size())];
      for (int i = offset; i < Math.min(list.length + offset, recommendations.size()); i++) {
        RecommendedItem recommendedItem = recommendations.get(i);
        list[i - offset] = recommendedItem.getItemID() + ";" + recommendedItem.getValue();
      }
      return list;
    } catch (Exception ex) {
      log.log(Level.SEVERE, null, ex);
      throw new RuntimeException(ex);
    }
  }

  @WebMethod(operationName = "estimatePreference")
  public double estimatePreference(
          @WebParam(name = "context") String context,
          @WebParam(name = "userId") long userId,
          @WebParam(name = "itemId") long itemId) throws Exception {
    try {
      return predictor.get(context).estimatePreference(userId, itemId);
    } catch (Exception ex) {
      log.log(Level.SEVERE, null, ex);
      throw ex;
    }
  }

  @WebMethod(operationName = "estimatePreferences")
  public double[] estimatePreferences(
          @WebParam(name = "context") String context,
          @WebParam(name = "userId") long userId,
          @WebParam(name = "itemIds") long[] itemIds) throws Exception {
    try {
      double[] preferences = new double[itemIds.length];
      for (int i = 0, itemIdsLength = itemIds.length; i < itemIdsLength; i++) {
        long itemId = itemIds[i];
        preferences[i] = predictor.get(context).estimatePreference(userId, itemId);
      }
      return preferences;
    } catch (Exception ex) {
      log.log(Level.SEVERE, null, ex);
      throw ex;
    }
  }

  @WebMethod(operationName = "getOnlineMaeHistory")
  public List<Float> getOnlineMaeHistory(
          @WebParam(name = "context") String context,
          @WebParam(name = "numberOfRatings") int numberOfRatings) throws Exception {
    try {
      return numberOfRatings == 0 || numberOfRatings >= onlineMaeHistories.get(context).size()
              ? onlineMaeHistories.get(context)
              : onlineMaeHistories.get(context).
              subList(onlineMaeHistories.get(context).size() - numberOfRatings, numberOfRatings);
    } catch (Exception e) {
      log.log(Level.SEVERE, null, e);
      throw new RuntimeException(e);
    }
  }

  @WebMethod(operationName = "addPreference")
  public void addPreference(
          @WebParam(name = "context") String context,
          @WebParam(name = "userId") long userId,
          @WebParam(name = "itemId") long itemId,
          @WebParam(name = "rating") byte rating) throws Exception {
    try {

      OnlineSVDRecommender osr = (OnlineSVDRecommender) predictor.get(context);
      DataModel newModel = osr.userPreferenceChanged(userId, itemId, rating);

      inMemoryDataModels.put(context, newModel);

      //immediately calculates mae for measuring online mae.
      double predictedRating = estimatePreference(context, userId, itemId);
      ArrayList<Float> list = onlineMaeHistories.get(context);
      list.add((float) (rating - predictedRating));

    } catch (ClassCastException e) {
      log.log(Level.WARNING, "Recommender for context " + context
              + " is not instance of OnlineSVDRecommender", e);
    } catch (NoSuchItemException e) {
      log.log(Level.FINE, null, e);
    } catch (Exception e) {
      log.log(Level.SEVERE, "An unexpected exception occurred!", e);
    }
  }

  @WebMethod(operationName = "removePreference")
  public void removePreference(
          @WebParam(name = "context") String context,
          @WebParam(name = "userId") long userId,
          @WebParam(name = "itemId") long itemId,
          @WebParam(name = "rating") byte rating) throws Exception {
    addPreference(context, userId, itemId, rating);
  }

  @WebMethod(operationName = "changePreference")
  public void changePreference(
          @WebParam(name = "context") String context,
          @WebParam(name = "userId") long userId,
          @WebParam(name = "itemId") long itemId,
          @WebParam(name = "rating") byte rating) throws Exception {
    addPreference(context, userId, itemId, rating);
  }

  @WebMethod(operationName = "getRecommendationList")
  public String[] getRecommendationList(
          @WebParam(name = "context") String context,
          @WebParam(name = "userId") long userId) {

    try {
      log.log(Level.INFO, "Entering getRecommendationList for user {0} in context {1}.",
              new Object[]{userId, context});
      List<RecommendedItem> recommendations = predictor.get(context).recommend(userId, 20);
      String[] list = new String[recommendations.size()];
      for (int i = 0; i < recommendations.size(); i++) {
        RecommendedItem recommendedItem = recommendations.get(i);
        list[i] = recommendedItem.getItemID() + ";" + recommendedItem.getValue();
      }
      return list;
    } catch (Exception ex) {
      log.log(Level.SEVERE, null, ex);
      throw new RuntimeException(ex);
    }
  }

  @WebMethod(operationName = "getUserFeatures")
  public double[] getUserFeatures(
          @WebParam(name = "context") String context,
          @WebParam(name = "userId") final long userId) throws Exception {
    try {
      FactorizationCachingFactorizer cachingFactorizer = factorizationCaches.get(context);
      final Factorization factorization = cachingFactorizer.getCachedFactorization();
      return factorization.getUserFeatures(userId);
    } catch (Exception ex) {
      log.log(Level.SEVERE, null, ex);
      throw ex;
    }

  }

  @WebMethod(operationName = "getUserNearestNeighborList")
  public String[] getUserNearestNeighborList(
          @WebParam(name = "context") String context,
          @WebParam(name = "userId") final long userId) {

    return new String[0];
		/*


		 FactorizationCachingFactorizer cachingFactorizer = factorizationCaches.get(context);
		 final Factorization factorization = cachingFactorizer.getCachedFactorization();

		 Collection<Long> userIds = new DBUtil().getUsersNotFollowing(context, userId);

		 class UserComparison implements Comparator<Long> {

		 private double similarity(long i, long j) {
		 try {
		 return CosineDistanceMeasure.distance(factorization.getUserFeatures(i), factorization.getUserFeatures(j));
		 } catch (NoSuchUserException ex) {
		 log.log(Level.SEVERE, null, ex);
		 throw new RuntimeException(ex);
		 }
		 }

		 @Override
		 public int compare(Long o1, Long o2) {
		 double sim1 = similarity(userId, o1);
		 double sim2 = similarity(userId, o2);

		 if (sim1 < sim2) {
		 return -1;
		 } else if (sim1 > sim2) {
		 return 1;
		 } else {
		 return 0;
		 }

		 }
		 }
		 UserComparison comparison = new UserComparison();
		 TopK<Long> topk = new TopK<>(20, comparison);
		 for (Long id : userIds) {
		 //try {
		 //if (predictor.get(context).getDataModel().getPreferencesFromUser(id).length() > 0) {
		 topk.offer(id);
		 //}
		 //} catch (TasteException ex) {
		 //Logger.getLogger(RecommenderWS.class.getName()).log(Level.SEVERE, null, ex);
		 //throw new RuntimeException(ex);
		 //}
		 }

		 List<Long> top = topk.retrieve();
		 String[] topWithSim = new String[top.size()];
		 for (int i = 0; i < topWithSim.length; ++i) {
		 long id = top.get(i);
		 topWithSim[i] = id + ";" + comparison.similarity(id, userId);
		 }
		 return topWithSim;
		 *
		 */
  }

  @WebMethod(operationName = "hasUserRatings")
  public boolean hasUserRatings(
          @WebParam(name = "context") String context,
          @WebParam(name = "userId") final long userId) {
    try {
      return predictor.get(context).getDataModel().getPreferencesFromUser(userId).length() != 0;
    } catch (Exception ex) {
      Logger.getLogger(RecommenderWS.class.getName()).log(Level.SEVERE, null, ex);
      return false;
    }
  }

  @WebMethod(operationName = "getItemNearestNeighborList")
  public Long[] getItemNearestNeighborList(
          @WebParam(name = "context") String context,
          @WebParam(name = "itemId") final long itemId) {

    FactorizationCachingFactorizer cachingFactorizer = factorizationCaches.get(context);
    final Factorization factorization = cachingFactorizer.getCachedFactorization();
    Iterable<Entry<Long, Integer>> userIDMappings = factorization.getItemIDMappings();

    TopK<Long> topk = new TopK<>(20, new Comparator<Long>() {
      private double similarity(long i, long j) {
        try {
          return CosineDistanceMeasure.distance(factorization.getItemFeatures(i), factorization.getItemFeatures(j));
        } catch (NoSuchItemException ex) {
          log.log(Level.WARNING, "Unavailable item!", ex);
          return Double.NEGATIVE_INFINITY;
        }
      }

      @Override
      public int compare(Long o1, Long o2) {
        double sim1 = similarity(itemId, o1);
        double sim2 = similarity(itemId, o2);

        if (sim1 < sim2) {
          return -1;
        } else if (sim1 > sim2) {
          return 1;
        } else {
          return 0;
        }
      }
    });
    for (Entry<Long, Integer> entry : userIDMappings) {
      topk.offer(entry.getKey());
    }

    return (Long[]) topk.retrieve().toArray();
  }

  @WebMethod(operationName = "isBuildingInProgress")
  public boolean isBuildingInProgress(@WebParam(name = "context") String context) {
    ContextState ongoing = getContextState(context);
    return ongoing == ContextState.BUILDING;
  }

  @WebMethod(operationName = "getContextState")
  public ContextState getContextState(@WebParam(name = "context") String context) {
    ContextState state = contextStates.get(context);
    return state == null ? ContextState.UNKNOWN : state;
  }

  @WebMethod(operationName = "getPreferrenceForItem")
  public String getPreferrenceForItem(
          @WebParam(name = "context") String context,
          @WebParam(name = "itemId") long itemId) throws TasteException {
    return String.valueOf(inMemoryDataModels.get(context).getPreferencesForItem(itemId));
  }

  @WebMethod(operationName = "getPreferrenceFromUser")
  public String getPreferrenceFromUser(
          @WebParam(name = "context") String context,
          @WebParam(name = "userId") long userId) throws TasteException {
    return String.valueOf(inMemoryDataModels.get(context).getPreferencesFromUser(userId));
  }

  @WebMethod(operationName = "getSimilarityBetweenItems")
  public double getSimilarityBetweenItems(
          @WebParam(name = "context") String context,
          @WebParam(name = "itemId1") long itemId1,
          @WebParam(name = "itemId2") long itemId2) throws TasteException {
    ItemSimilarity sim = ((GenericItemBasedRecommender) predictor.get(context)).getSimilarity();
    return sim.itemSimilarity(itemId1, itemId2);
  }

  public static void main(String[] args) throws Exception {
    DataModel model = new FileDataModel(new File("/home/ceyhun/Dropbox/Projects/doctoral/dataset/MovieLens/100k/ratings.dat"));
    final String context = "movie";
    inMemoryDataModels.put(context, model);
    contextStates.put(context, ContextState.FETCHED);


    RecommenderWS w = new RecommenderWS();


    while (true) {
      System.out.println("REPL!");
    }

		/*

		 DataModel reloadModel =
		 FactorizationCachingFactorizer cachingFactorizer = new FactorizationCachingFactorizer(new ParallelArraysSGDFactorizer(reloadModel, 25, 25));
		 Recommender recommender = new SVDRecommender(reloadModel, cachingFactorizer);
		 log.log(Level.INFO, "Data loading and training done.");

		 predictor.put(context, recommender);
		 factorizationCaches.put(context, cachingFactorizer);
		 String context = "test";
		 RecommenderWS recommenderWS = new RecommenderWS();
		 recommenderWS.buildModel(context);
		 recommenderWS.getRecommendationListPaginated(context, 1, "", 1, 100);
		 */
    //recommenderWS.estimatePreference(context, 2, 1);
  }
}

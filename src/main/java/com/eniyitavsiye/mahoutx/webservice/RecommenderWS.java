package com.eniyitavsiye.mahoutx.webservice;

import com.eniyitavsiye.mahoutx.common.FilterIDsRescorer;
import com.eniyitavsiye.mahoutx.common.LimitMySQLJDBCDataModel;
import com.eniyitavsiye.mahoutx.db.DBUtil;
import com.eniyitavsiye.mahoutx.svdextension.FactorizationCachingFactorizer;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebService;
import org.apache.mahout.cf.taste.common.NoSuchItemException;
import org.apache.mahout.cf.taste.common.NoSuchUserException;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.common.TopK;
import org.apache.mahout.cf.taste.eval.RecommenderEvaluator;
import org.apache.mahout.cf.taste.impl.common.FastIDSet;
import org.apache.mahout.cf.taste.impl.eval.AverageAbsoluteDifferenceRecommenderEvaluator;
import org.apache.mahout.cf.taste.impl.model.jdbc.ConnectionPoolDataSource;
import org.apache.mahout.cf.taste.impl.model.jdbc.ReloadFromJDBCDataModel;
import org.apache.mahout.cf.taste.impl.recommender.svd.ALSWRFactorizer;
import org.apache.mahout.cf.taste.impl.recommender.svd.Factorization;
import org.apache.mahout.cf.taste.impl.recommender.svd.SVDRecommender;
import org.apache.mahout.cf.taste.recommender.RecommendedItem;
import org.apache.mahout.cf.taste.recommender.Recommender;
import org.apache.mahout.common.distance.CosineDistanceMeasure;

@WebService(serviceName = "RecommenderWS")
public class RecommenderWS {

    private static HashMap<String, Recommender> predictor;
    private static HashMap<String, FactorizationCachingFactorizer> factorizationCaches;
    private static HashMap<String, Boolean> ongoingTrainingStates;
    private static final Logger log = Logger.getLogger(RecommenderWS.class.getName());

    static {
        predictor = new HashMap<>();
        factorizationCaches = new HashMap<>();
        ongoingTrainingStates = new HashMap<>();
    }

    @WebMethod(operationName = "isModelAlive")
    public boolean isModelAlive(@WebParam(name = "context") String context) {
        return predictor.containsKey(context);
    }

    @WebMethod(operationName = "buildModel")
    public String buildModel(@WebParam(name = "context") String context) {
        try {
            ongoingTrainingStates.put(context, Boolean.TRUE);
            log.log(Level.INFO, "buildItemSimilarityMatrix starts.");
            DBUtil dbUtil = new DBUtil();
            LimitMySQLJDBCDataModel model = new LimitMySQLJDBCDataModel(new ConnectionPoolDataSource(dbUtil.getDataSource()), context + "_rating", "user_id", "item_id", "rating", null);
            ReloadFromJDBCDataModel reloadModel = new ReloadFromJDBCDataModel(model);
            FactorizationCachingFactorizer cachingFactorizer = new FactorizationCachingFactorizer(new ALSWRFactorizer(reloadModel, 15, 0.001, 15));
            Recommender recommender = new SVDRecommender(reloadModel, cachingFactorizer);
            log.log(Level.INFO, "Data loading and training done.");

            predictor.put(context, recommender);
            factorizationCaches.put(context, cachingFactorizer);

            return "done";
        } catch (TasteException ex) {
            log.log(Level.SEVERE, null, ex);
            return "error";
        } finally {
            ongoingTrainingStates.put(context, Boolean.FALSE);
        }
    }

    @WebMethod(operationName = "getRecommendationListPaginated")
    public String[] getRecommendationListPaginated(
            @WebParam(name = "context") String context,
            @WebParam(name = "userId") long userId,
            @WebParam(name = "tagstring") String tagstring,
            @WebParam(name = "offset") int offset,
            @WebParam(name = "length") int length) {

        try {
            log.log(Level.INFO, "Entering getRecommendationList for user {0} in context {1}.",
                    new Object[]{userId, context});
            List<RecommendedItem> recommendations = null;
            if (!tagstring.equals("")) {
                DBUtil dbUtil = new DBUtil();
                List<Long> specificItemIDsList = dbUtil.getItems(context, tagstring.split(","));
                FastIDSet specificItemIDs = new FastIDSet(specificItemIDsList.size());
                for (Long id : specificItemIDsList) {
                    specificItemIDs.add(id);
                }
                FilterIDsRescorer filterIDsRescorer = new FilterIDsRescorer(specificItemIDs);
                recommendations = predictor.get(context).recommend(userId, offset + length, filterIDsRescorer);
            } else {
                recommendations = predictor.get(context).recommend(userId, offset + length);
            }

            String[] list = new String[Math.min(length, recommendations.size())];
            for (int i = offset; i < Math.min(list.length + offset, recommendations.size()); i++) {
                RecommendedItem recommendedItem = recommendations.get(i);
                list[i - offset] = recommendedItem.getItemID() + ";" + recommendedItem.getValue();
            }
            return list;
        } catch (TasteException ex) {
            log.log(Level.SEVERE, null, ex);
            return new String[0];
        }
    }

    @WebMethod(operationName = "getRecommendationList")
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
        } catch (TasteException ex) {
            log.log(Level.SEVERE, null, ex);
            return new String[0];
        }
    }

    @WebMethod(operationName = "getUserNearestNeighborList")
    public Long[] getUserNearestNeighborList(
            @WebParam(name = "context") String context,
            @WebParam(name = "userId") final long userId) {

        FactorizationCachingFactorizer cachingFactorizer = factorizationCaches.get(context);
        final Factorization factorization = cachingFactorizer.getCachedFactorization();
        Iterable<Entry<Long, Integer>> userIDMappings = factorization.getUserIDMappings();

        TopK<Long> topk = new TopK<>(20, new Comparator<Long>() {
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
        });
        for (Entry<Long, Integer> entry : userIDMappings) {
            topk.offer(entry.getKey());
        }

        return (Long[]) topk.retrieve().toArray();
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
                    log.log(Level.SEVERE, null, ex);
                    throw new RuntimeException(ex);
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
        Boolean ongoing = ongoingTrainingStates.get(context);
        return ongoing != null && ongoing;
    }
}

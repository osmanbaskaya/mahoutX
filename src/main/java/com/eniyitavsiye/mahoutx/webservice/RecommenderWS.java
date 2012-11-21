package com.eniyitavsiye.mahoutx.webservice;

import com.eniyitavsiye.mahoutx.svdextension.FactorizationCachingFactorizer;
import com.mysql.jdbc.jdbc2.optional.MysqlDataSource;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebService;
import org.apache.mahout.cf.taste.common.NoSuchUserException;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.common.TopK;
import org.apache.mahout.cf.taste.impl.model.jdbc.ConnectionPoolDataSource;
import org.apache.mahout.cf.taste.impl.model.jdbc.MySQLJDBCDataModel;
import org.apache.mahout.cf.taste.impl.model.jdbc.ReloadFromJDBCDataModel;
import org.apache.mahout.cf.taste.impl.recommender.svd.ALSWRFactorizer;
import org.apache.mahout.cf.taste.impl.recommender.svd.Factorization;
import org.apache.mahout.cf.taste.impl.recommender.svd.SVDRecommender;
import org.apache.mahout.cf.taste.recommender.RecommendedItem;
import org.apache.mahout.cf.taste.recommender.Recommender;
import org.apache.mahout.common.distance.CosineDistanceMeasure;

@WebService(serviceName="RecommenderWS")
public class RecommenderWS {

	private static HashMap<String, Recommender> predictor;
	private static HashMap<String, FactorizationCachingFactorizer> factorizationCaches;

	private static final String host = "54.246.114.38";
	private static final int port = 8080;

	private static final String user = "root";
	private static final String password = "rast0gele1";
	private static final String database = "eniyitavsiye";

	private static final Logger log = Logger.getLogger(RecommenderWS.class.getName());

	static {
		predictor = new HashMap<>();
		factorizationCaches = new HashMap<>();
	}

	@WebMethod(operationName = "isModelAlive")
	public boolean isModelAlive(@WebParam(name = "context") String context) {
		return predictor.containsKey(context);
	}

	@WebMethod(operationName = "buildModel")
	public String buildModel(@WebParam(name = "context") String context) {
		try {
			log.log(Level.INFO, "buildItemSimilarityMatrix starts.");
			MysqlDataSource dataSource = new MysqlDataSource();
			dataSource.setServerName(host);
			dataSource.setUser(user);
			dataSource.setPassword(password);
			dataSource.setDatabaseName(database);
			dataSource.setPort(port);
			dataSource.setAutoReconnect(true);
			dataSource.setAutoReconnectForPools(true);
			dataSource.setCachePreparedStatements(true);
			dataSource.setCachePrepStmts(true);
			dataSource.setCacheResultSetMetadata(true);
			dataSource.setAlwaysSendSetIsolation(false);
			dataSource.setElideSetAutoCommits(true);
			MySQLJDBCDataModel model = new MySQLJDBCDataModel(new ConnectionPoolDataSource(dataSource), context, "user_id", "item_id", "rating", null);
			ReloadFromJDBCDataModel reloadModel = new ReloadFromJDBCDataModel(model);
			FactorizationCachingFactorizer cachingFactorizer = new FactorizationCachingFactorizer(new ALSWRFactorizer(reloadModel, 20, 0.001, 40));
			Recommender recommender = new SVDRecommender(reloadModel, cachingFactorizer);
			log.log(Level.INFO, "Data loading and training done.");

			predictor.put(context, recommender);
			factorizationCaches.put(context, cachingFactorizer);

			return "done";
		} catch (TasteException ex) {
			log.log(Level.SEVERE, null, ex);
			return "error";
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

	@WebMethod(operationName = "getNearestNeighborList")
	public Long[] getNearestNeighborList(
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
}

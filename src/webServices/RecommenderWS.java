package webServices;
 
  

import java.text.DateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import org.apache.mahout.cf.taste.impl.model.mongodb.MongoDBDataModel;
import org.apache.mahout.cf.taste.impl.recommender.svd.ALSWRFactorizer;
import org.apache.mahout.cf.taste.impl.recommender.svd.SVDRecommender;
import org.apache.mahout.cf.taste.recommender.RecommendedItem;
import org.apache.mahout.cf.taste.recommender.Recommender;

public class RecommenderWS {

	public static HashMap<String, Recommender > predictor;
	public static HashMap<String, MongoDBDataModel > dataModel;
	
	static {
		predictor = new HashMap<String, Recommender>();
		
	}
	static {
		dataModel = new HashMap<String, MongoDBDataModel>();
		
	}
	

	public static String test() throws Exception
	{
		try {
			System.out.println(new Date());
			return "ok";
			
		} catch (Exception e) {
			throw e;
		}
	}
	 
	public static boolean isModelAlive(String context) throws Exception
	{
		try {
			return predictor.containsKey(context);
		} catch (Exception e) {
			throw e;
		}
	}
	public static String buildModel(String context,boolean buildSimilarities) throws Exception
	{
		
	  try {
 		  System.out.println("buildItemSimilarityMatrix starts att: "+new Date());
		  MongoDBDataModel model= new MongoDBDataModel("54.247.188.246",27017,"eniyitavsiye",context + "_ratings",
				  					true,true,DateFormat.getDateTimeInstance());
// 		 DataModel model = new FileDataModel (new File("C:/tez/data/movielensDataset/ratingsComma.dat"));
		  dataModel.put(context, model);
		  Recommender recommender = new SVDRecommender(model, new ALSWRFactorizer(model, 10, 0.05, 10));
		  predictor.put(context, recommender);
	  
	      return "done";
	    		  
	      
		} catch (Exception e) {
			throw e;
		}	
      

	}
	public static String[]  getRecommendationList(String userId,final String context,long lastRate) throws Exception
	{
		try {
			long userIdLong= userIdStringToLong(userId, context);
			List<RecommendedItem> recommendations =predictor.get(context).recommend(userIdLong, 20);
			String[] list= new String[recommendations.size()] ;
			for (int i=0;i<recommendations.size();i++) {
				RecommendedItem recommendedItem = recommendations.get(i);
				list[i]=idLongToString(context, recommendedItem.getItemID())+";"+recommendedItem.getValue();
			}
			return list;
		} catch (Exception e) {	
			throw e;
		}
	//		return "ok";
	}

	private static String idLongToString(final String context,
			long recommendedItem) {
		return dataModel.get(context).fromLongToId(recommendedItem);
	}

	private static long userIdStringToLong(String userId, final String context) {
		return Long.parseLong(dataModel.get(context).fromIdToLong(userId, true));
	}
}

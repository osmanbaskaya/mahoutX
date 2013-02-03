/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.eniyitavsiye.mahoutx.tags;

import com.eniyitavsiye.mahoutx.common.UserItemIDIndexMapFunction;
import org.apache.commons.math3.linear.*;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 *
 * @author ceyhun
 */
public class TaggingData {

	private Tags tags;
	private RealMatrix userTags;
	private RealMatrix itemTags;
	private UserItemIDIndexMapFunction indexMap;

	public TaggingData(Tags tags, RealMatrix userTags, RealMatrix itemTags, UserItemIDIndexMapFunction indexMap) {
		this.tags = tags;
		this.userTags = userTags;
		this.itemTags = itemTags;
		this.indexMap = indexMap;
	}

	public Tags getTags() {
		return tags;
	}

	public RealMatrix getUserTaggingCountMatrix() {
		return userTags;
	}

	public RealMatrix getItemTaggingCountMatrix() {
		return itemTags;
	}

	public int getItemNumTagged(long itemID, String tag) {
		return (int) itemTags.getEntry(indexMap.itemIndex(itemID), tags.getTagIndex(tag));
	}

	public int getUserNumTagged(long userID, String tag) {
		return (int) userTags.getEntry(indexMap.userIndex(userID), tags.getTagIndex(tag));
	}

	public int getItemNumTaggedTotal(long itemID) {
		return (int) itemTags.getRowVector(indexMap.itemIndex(itemID)).dotProduct(new ArrayRealVector(itemTags.getRowDimension(), 1));
	}

	public int getUserNumTaggedTotal(long userID) {
        return (int) userTags.getRowVector(indexMap.userIndex(userID)).dotProduct(new ArrayRealVector(userTags.getRowDimension(), 1));
	}

	public int getTagUsedTotal(String tag) {
        return (int) userTags.getColumnVector(tags.getTagIndex(tag)).dotProduct(new ArrayRealVector(userTags.getColumnDimension(), 1));
	}

	protected int getTagUsedTotalByItems(String tag) {
        return (int) itemTags.getColumnVector(tags.getTagIndex(tag)).dotProduct(new ArrayRealVector(itemTags.getColumnDimension(), 1));
	}

	public static class Builder {

		private Tags.Builder tagBuilder = new Tags.Builder();
		private UserItemIDIndexMapFunction indexMap;
		private List<RealVector> userTagging;
		private List<RealVector> itemTagging;
		
		public Builder(UserItemIDIndexMapFunction indexMap) {
			this.indexMap = indexMap;
			userTagging = new ArrayList<>();
			itemTagging = new ArrayList<>();
		}

		public void addTagging(long userId, long itemId, String tag) {
			if (userTagging == null) {
				throw new IllegalStateException("This builder is already done building! Cannot add tagging.");
			}
			int tagIndex = tagBuilder.addTagGetIndex(tag);
			
			int userIndex = indexMap.userIndex(userId);
			int itemIndex = indexMap.itemIndex(itemId);

			//WARN : 1689 users out of 69878 does not have a rating data but have 
			//tagging data!!!

			incMat(userTagging, userIndex, tagIndex, indexMap.getUserCount());
			incMat(itemTagging, itemIndex, tagIndex, indexMap.getItemCount());
		}

		public TaggingData done() {
			//TODO maybe prune tags with zero tagging count.
			List<RealVector> it = this.itemTagging;
			List<RealVector> ut = this.userTagging;
			userTagging = null;
			itemTagging = null;
			Tags tags = tagBuilder.done();

			int T = tags.getTagCount();
			RealMatrix userMat = new OpenMapRealMatrix(
							indexMap.getUserCount(), T);
            for (int i = 0; i < ut.size(); ++i) {
                userMat.setColumnVector(i, ut.get(i));
            }


			RealMatrix itemMat = new OpenMapRealMatrix(
							indexMap.getItemCount(), T);
            for (int i = 0; i < it.size(); ++i) {
                itemMat.setColumnVector(i, it.get(i));
            }
			return new TaggingData(tags, userMat, itemMat, indexMap);
		}

		/**
		 * Increments ijth index at given matrix with <code>tagging</code>, returns 
		 * <code>true</code> if could be incremented therefore there exists a cell, 
		 * <code>false</code> otherwise.
		 * 
		 * @param tagging
		 * @param i
		 * @param j
		 * @param c
		 * @return 
		 */
		private boolean incMat(List<RealVector> tagging, int i, int j, int c) {
			RealVector col;

			if (j >= tagging.size()) { // if a new tag has come
				//create a sparse vector 
				col = new OpenMapRealVector(c);
				//add that column as jth index
				tagging.add(col);

			} else { //otherwise
				//get column for jth tag
				col = tagging.get(j);
			}
			if (i >= c) {
				Logger.getLogger(getClass().getName()).log(Level.WARNING, 
								"Index discarded for not being in rating data {0} >= {1}", 
								new Object[] {i, c});
				return false;
			} else {
				//increment ith user's/item's usage by 1
				col.setEntry(i, col.getEntry(i) + 1);
				return true;
			}
		}

	}
	
}

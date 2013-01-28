/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.eniyitavsiye.mahoutx.tags;

import com.eniyitavsiye.mahoutx.common.UserItemIDIndexMapFunction;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.mahout.math.Matrix;
import org.apache.mahout.math.RandomAccessSparseVector;
import org.apache.mahout.math.SparseColumnMatrix;

/**
 *
 * @author ceyhun
 */
public class TaggingData {

	private Tags tags;
	private Matrix userTags;
	private Matrix itemTags;
	private UserItemIDIndexMapFunction indexMap;

	public TaggingData(Tags tags, Matrix userTags, Matrix itemTags, UserItemIDIndexMapFunction indexMap) {
		this.tags = tags;
		this.userTags = userTags;
		this.itemTags = itemTags;
		this.indexMap = indexMap;
	}

	public Tags getTags() {
		return tags;
	}

	public Matrix getUserTaggingCountMatrix() {
		return userTags;
	}

	public Matrix getItemTaggingCountMatrix() {
		return itemTags;
	}

	public int getItemNumTagged(long itemID, String tag) {
		return (int) itemTags.get(indexMap.itemIndex(itemID), tags.getTagIndex(tag));
	}

	public int getUserNumTagged(long userID, String tag) {
		return (int) userTags.get(indexMap.userIndex(userID), tags.getTagIndex(tag));
	}

	public int getItemNumTaggedTotal(long itemID, String tag) {
		return (int) itemTags.viewRow(indexMap.itemIndex(itemID)).zSum();
	}

	public int getUserNumTaggedTotal(long userID, String tag) {
		return (int) userTags.viewRow(indexMap.userIndex(userID)).zSum();
	}

	public int getTagUsedTotal(String tag) {
		return (int) userTags.viewColumn(tags.getTagIndex(tag)).zSum();
	}

	public int getUserTotalTagged(long userID) {
		return (int) userTags.viewRow(indexMap.userIndex(userID)).zSum();
	}

	public int getItemTotalTagged(long itemID) {
		return (int) itemTags.viewRow(indexMap.itemIndex(itemID)).zSum();
	}

	protected int getTagUsedTotalByItems(String tag) {
		return (int) itemTags.viewColumn(tags.getTagIndex(tag)).zSum();
	}

	public static class Builder {

		private Tags.Builder tagBuilder = new Tags.Builder();
		private UserItemIDIndexMapFunction indexMap;
		private List<RandomAccessSparseVector> userTagging;
		private List<RandomAccessSparseVector> itemTagging;
		
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
			List<RandomAccessSparseVector> it = this.itemTagging;
			List<RandomAccessSparseVector> ut = this.userTagging;
			userTagging = null;
			itemTagging = null;
			Tags tags = tagBuilder.done();

			int T = tags.getTagCount();
			Matrix userMat = new SparseColumnMatrix(
							indexMap.getUserCount(), T,
							ut.toArray(new RandomAccessSparseVector[T]));

			Matrix itemMat = new SparseColumnMatrix(
							indexMap.getItemCount(), T, 
							it.toArray(new RandomAccessSparseVector[T]));
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
		private boolean incMat(List<RandomAccessSparseVector> tagging, int i, int j, int c) {
			RandomAccessSparseVector col;

			if (j >= tagging.size()) { // if a new tag has come
				//create a sparse vector 
				col = new RandomAccessSparseVector(c);
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
				col.set(i, col.get(i) + 1);
				return true;
			}
		}

	}
	
}

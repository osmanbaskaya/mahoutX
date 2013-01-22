/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.eniyitavsiye.mahoutx.tags;

import com.eniyitavsiye.mahoutx.common.UserItemIDIndexMapFunction;
import java.util.ArrayList;
import java.util.List;
import org.apache.mahout.math.Matrix;
import org.apache.mahout.math.RandomAccessSparseVector;
import org.apache.mahout.math.SparseColumnMatrix;
import org.apache.mahout.math.SparseRowMatrix;
import org.apache.mahout.math.Vector;

/**
 *
 * @author ceyhun
 */
public class TaggingData {

	private Tags tags;
	private Matrix userTags;
	private Matrix itemTags;

	private TaggingData(int N, int M, int T) {
		userTags = new SparseRowMatrix(N, T);
		itemTags = new SparseRowMatrix(M, T);
	}

	public TaggingData(Tags tags, Matrix userTags, Matrix itemTags) {
		this.tags = tags;
		this.userTags = userTags;
		this.itemTags = itemTags;
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

	public static class Builder {

		private Tags.Builder tagBuilder = new Tags.Builder();
		private UserItemIDIndexMapFunction indexMap;
		private TaggingData data;
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

			incMat(userTagging, userIndex, tagIndex, indexMap.getUserCount());
			incMat(itemTagging, itemIndex, tagIndex, indexMap.getItemCount());
		}

		public TaggingData done() {
			List<RandomAccessSparseVector> it = this.itemTagging;
			List<RandomAccessSparseVector> ut = this.userTagging;
			userTagging = null;
			itemTagging = null;
			Tags tags = tagBuilder.done();
			return new TaggingData(
							tags, 
							new SparseColumnMatrix(
									indexMap.getUserCount(), 
									tags.getTagCount(),
									it.toArray(new RandomAccessSparseVector[0])),
							new SparseColumnMatrix(
									indexMap.getUserCount(), 
									tags.getTagCount(),
									ut.toArray(new RandomAccessSparseVector[0])));
		}

		private void incMat(List<RandomAccessSparseVector> tagging, int i, int j, int c) {
			if (j >= tagging.size()) { // if a new tag has come
				//create a sparse vector 
				RandomAccessSparseVector col = new RandomAccessSparseVector(c);
				//set 1 to user row
				col.set(i, 1);
				//add that column
				tagging.add(col);
			} else { //otherwise
				//get column for jth tag
				Vector col = tagging.get(j);
				//increment ith user's/item's usage by 1
				col.set(i, col.get(i) + 1);
			}
		}

	}
	
}

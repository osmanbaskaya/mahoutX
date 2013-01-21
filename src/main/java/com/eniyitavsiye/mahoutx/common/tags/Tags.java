/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.eniyitavsiye.mahoutx.common.tags;

import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author ceyhun
 */
public class Tags {

	private int lastIndex = 0;
	private Map<String, Integer> tagIndices;

	private Tags() {
		tagIndices = new HashMap<>();
	}

	public Tags(Map<String, Integer> tagIndices) {
		this.tagIndices = new HashMap<>(tagIndices);
	}

	public int getTagIndex(String tag) {
		return tagIndices.get(tag);
	}

	public int getTagCount() {
		return tagIndices.size();
	}

	public Tags pruneThoseHold(TagPredicate pred) {
		Builder builder = new Builder();
		for (String tag : tagIndices.keySet()) {
			if (!pred.holds(tag)) {
				builder.addTagGetIndex(tag);
			}
		}
		return builder.done();
	}

	public interface TagPredicate {
		public boolean holds(String tag);
	}

	public static class Builder {

		private Tags tags = new Tags();

		public int addTagGetIndex(String tag) {
			if (tags == null) {
				throw new IllegalStateException("This builder is already done building! Cannot add tags.");
			}
			if (!tags.tagIndices.containsKey(tag)) {
				int index = tags.lastIndex;
				tags.tagIndices.put(tag, tags.lastIndex++);
				return index;
			} 
			return tags.getTagIndex(tag);
		}

		public Tags done() {
			Tags retVal = tags;
			tags = null;
			return retVal;
		}

	}
	
}

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.eniyitavsiye.mahoutx.tags;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 *
 * @author ceyhun
 */
public class Tags {

	private final Map<String, Integer> tagIndices;
	private final String[] tags;

	public Tags(Map<String, Integer> tagIndices) {
		this.tagIndices = new HashMap<>(tagIndices);
		this.tags = new String[tagIndices.size()];
		for (Map.Entry<String, Integer> entry : tagIndices.entrySet()) {
			tags[entry.getValue()] = entry.getKey();
		}
	}

	public int getTagIndex(String tag) {
		return tagIndices.get(tag);
	}

	public String getTagAtIndex(int index) {
		return tags[index];
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

		public abstract class TagPredicateWithData implements TagPredicate {

			protected TaggingData data;

			public TagPredicateWithData(TaggingData data) {
				this.data = data;
			}
			
		}

		public class TaggingLessThan extends TagPredicateWithData {

			private int threshold;

			public TaggingLessThan(TaggingData data, int threshold) {
				super(data);
				this.threshold = threshold;
			}

			@Override
			public boolean holds(String tag) {
				return data.getTagUsedTotal(tag) < threshold;
			}
			
		}

		public class NameLengthMoreThan implements TagPredicate {

			private int threshold;

			@Override
			public boolean holds(String tag) {
				return tag.length() > threshold;
			}

		}

		public class NotWord implements TagPredicate {
			private static Pattern numeric = Pattern.compile("\\W+");

			@Override
			public boolean holds(String tag) {
				return !numeric.matcher(tag).find();
			}

		}

		public class AnyHoldsPredicate implements TagPredicate {

			private TagPredicate[] predicates;

			public AnyHoldsPredicate(TagPredicate... predicates) {
				this.predicates = predicates;
			}

			@Override
			public boolean holds(String tag) {
				for (TagPredicate pred : predicates) {
					if (pred.holds(tag)) {
						return true;
					}
				}
				return false;
			}

		}

	}

	public static class Builder {

		private int lastIndex = 0;
		private Map<String, Integer> tagIndices = new HashMap<>();

		public int addTagGetIndex(String tag) {
			if (tagIndices == null) {
				throw new IllegalStateException("This builder is already done building! Cannot add tags.");
			}
			if (!tagIndices.containsKey(tag)) {
				int index = lastIndex;
				tagIndices.put(tag, lastIndex++);
				return index;
			} 
			return tagIndices.get(tag);
		}

		public Tags done() {
			Map<String, Integer> tagIndices_ = tagIndices;
			tagIndices = null;
			return new Tags(tagIndices_);
		}

	}
	
}

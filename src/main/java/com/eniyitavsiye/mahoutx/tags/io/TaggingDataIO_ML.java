/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.eniyitavsiye.mahoutx.tags.io;

import com.eniyitavsiye.mahoutx.common.UserItemIDIndexMapFunction;
import com.eniyitavsiye.mahoutx.tags.TaggingData;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

/**
 *
 * @author ceyhun
 */
public class TaggingDataIO_ML {

	private String filename;
	private static final String COL_DELIMITER = "::";

	public TaggingDataIO_ML(String filename) {
		this.filename = filename;
	}

	public TaggingData readTaggingData(UserItemIDIndexMapFunction indexMap) throws IOException {
		TaggingData.Builder builder = new TaggingData.Builder(indexMap);
		try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
			String line;
			while ((line = reader.readLine()) != null) {
				String[] pieces = line.split(COL_DELIMITER);
				long userID = Long.parseLong(pieces[0]);
				long itemID = Long.parseLong(pieces[1]);
				String tag = pieces[2];
				builder.addTagging(userID, itemID, tag);
			}
		}
		return builder.done();
	}
	
}

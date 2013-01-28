/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.eniyitavsiye.mahoutx.tags;

import com.eniyitavsiye.mahoutx.common.UserItemIDIndexMapFunction;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author ceyhun
 */
public class TaggingDataIO_ML {

	private static final Logger log = Logger.getLogger(TaggingDataIO_ML.class.getName());

	private File filename;
	private static final String COL_DELIMITER = "::";
	private static final String CSV_DELIMITER = ",";

	private Set<Long> nonRatedUsers;
	private Set<Long> nonRatedItems;

	public Set<Long> getNonRatedItems() {
		return nonRatedItems;
	}

	public Set<Long> getNonRatedUsers() {
		return nonRatedUsers;
	}

	public TaggingDataIO_ML(String filename) {
		this(new File(filename));
	}

	public TaggingDataIO_ML(File file) {
		this.filename = file;
		this.nonRatedUsers = new HashSet<>();
		this.nonRatedItems = new HashSet<>();
	}

	private File getCSVFilename() {
		String csvName = filename.getName();
		csvName = csvName.substring(0, csvName.lastIndexOf(".dat")) + ".csv";
		return new File(filename.getParentFile(), csvName);
	}

	private void stripTimeConvertCSV() {
		try (PrintWriter writer = new PrintWriter(new FileWriter(getCSVFilename()), true);
				 BufferedReader reader = new BufferedReader(new FileReader(filename))) {
			String line;
			while ((line = reader.readLine()) != null) {
				String[] pieces = line.split(COL_DELIMITER);
				String userID = pieces[0];
				String itemID = pieces[1];
				String tag = pieces[2];
				writer.println(userID + "," + itemID + "," + tag);
			}
		} catch (FileNotFoundException e) {
			log.log(Level.SEVERE, "This should not happen: {0}", e);
		} catch (IOException e) {
			log.log(Level.SEVERE, "Original tag file not found: {0}", e);
		}

	}

	public TaggingData readTaggingData(UserItemIDIndexMapFunction indexMap) throws IOException {
		if (!getCSVFilename().exists()) {
			stripTimeConvertCSV();
		}
		TaggingData.Builder builder = new TaggingData.Builder(indexMap);
		try (BufferedReader reader = new BufferedReader(new FileReader(getCSVFilename()))) {
			String line;
			while ((line = reader.readLine()) != null) {
				String[] pieces = line.split(CSV_DELIMITER);
				long userID = Long.parseLong(pieces[0]);
				long itemID = Long.parseLong(pieces[1]);
				String tag = pieces[2];

				boolean shouldAdd = true;

				boolean userExists = indexMap.userIndex(userID) < indexMap.getUserCount();
				if (!userExists) {
					nonRatedUsers.add(userID);
				}
				shouldAdd &= userExists;

				boolean itemExists = indexMap.itemIndex(itemID) < indexMap.getItemCount();
				if (!itemExists) {
					nonRatedItems.add(itemID);
				}
				shouldAdd &= itemExists;

				if (shouldAdd) {
					builder.addTagging(userID, itemID, tag);
				} else {
					log.log(Level.FINE, 
									"User {0} or Item {1} does not exist but tagged as {2}.",
									new Object[] { userID, itemID, tag });
				}
			}
		}
		return builder.done();
	}
	
}

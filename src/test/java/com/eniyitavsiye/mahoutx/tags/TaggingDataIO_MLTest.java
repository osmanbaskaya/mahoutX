/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.eniyitavsiye.mahoutx.tags;

import com.eniyitavsiye.mahoutx.common.UserItemIDIndexMapFunction;
import com.eniyitavsiye.mahoutx.svdextension.tag.tagcofi.TagCoFiFactorizer;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.impl.model.file.FileDataModel;
import org.apache.mahout.cf.taste.model.DataModel;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;
/**
 *
 * @author ceyhun
 */
public class TaggingDataIO_MLTest {

	private DataModel dataModel;
	private UserItemIDIndexMapFunction indexMap;
	private File root = new File("/home/ceyhun/Dropbox/Projects/doctoral/dataset/MovieLens/10M100K");
	
	
	public TaggingDataIO_MLTest() {
	}
	
	@BeforeClass
	public static void setUpClass() {
	}
	
	@AfterClass
	public static void tearDownClass() {
	}
	
	@Before
	public void setUp() {
		try {
			dataModel = new FileDataModel(new File(root, "ratings.csv"));
			indexMap = new TagCoFiFactorizer(dataModel, 
								TagCoFiFactorizer.SimilarityCalculator.COSINE, 1, 1, 1, 1, 1);
		} catch (IOException | TasteException ex) {
			Logger.getLogger(TaggingDataIO_MLTest.class.getName()).log(Level.SEVERE, null, ex);
		}
	}
	
	@After
	public void tearDown() {
		dataModel = null;
	}

	/**
	 * Test of readTaggingData method, of class TaggingDataIO_ML.
	 */
	@Test
	public void testReadTaggingData() throws Exception {
		System.out.println("readTaggingData");
		TaggingDataIO_ML instance = new TaggingDataIO_ML(new File(root, "tags.dat"));
		TaggingData data = instance.readTaggingData(indexMap);
		
		assertEquals("item 4973 is tagged \"French\" 4 times.", 4,
						data.getItemNumTagged(4973L, "French"));
		assertEquals("user 1754 is tagged \"French\" 8 times.", 8,
						data.getUserNumTagged(1751L, "French"));
		assertEquals("tag counts should be same by both user and item matrices.", 
						data.getTagUsedTotal("French"), 
						data.getTagUsedTotalByItems("French"));
		assertEquals("tag \"French\" is used 31 times.", 31, 
						data.getTagUsedTotal("French"));
	}

}

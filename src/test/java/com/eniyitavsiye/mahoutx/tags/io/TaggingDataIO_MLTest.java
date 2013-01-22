/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.eniyitavsiye.mahoutx.tags.io;

import com.eniyitavsiye.mahoutx.common.UserItemIDIndexMapFunction;
import com.eniyitavsiye.mahoutx.svdextension.tag.tagcofi.TagCoFiFactorizer;
import com.eniyitavsiye.mahoutx.tags.TaggingData;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.example.grouplens.GroupLensDataModel;
import org.apache.mahout.cf.taste.model.DataModel;
import org.junit.After;
import org.junit.AfterClass;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

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
			dataModel = new GroupLensDataModel(new File(root, "ratings.dat"));
			indexMap = new TagCoFiFactorizer(dataModel, TagCoFiFactorizer.SimilarityCalculator.COSINE, 1, 1, 1, 1, 1);
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
		
		assertEquals(data.getItemNumTagged(4973L, "French"), 4);
		assertEquals(data.getUserNumTagged(1751L, "French"), 8);
		assertEquals(data.getTagUsedTotal("French"), 31);
		//fail("The test case is a prototype.");
	}

}

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.eniyitavsiye.mahoutx.svdextension.tag.tagcofi;

import com.eniyitavsiye.mahoutx.tags.TaggingData;
import com.eniyitavsiye.mahoutx.tags.TaggingDataIO_ML;
import java.io.File;
import org.apache.mahout.cf.taste.impl.model.file.FileDataModel;
import org.apache.mahout.cf.taste.impl.recommender.svd.Factorization;
import org.apache.mahout.cf.taste.model.DataModel;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 *
 * @author ceyhun
 */
public class TagCoFiFactorizerTest {

	private DataModel model;
	private TaggingData taggingData;
	private TagCoFiFactorizer factorizer;
	private File root = new File("/home/ceyhun/Dropbox/Projects/doctoral/dataset/MovieLens/10M100K");
	
	public TagCoFiFactorizerTest() {
	}
	
	@BeforeClass
	public static void setUpClass() {
	}
	
	@AfterClass
	public static void tearDownClass() {
	}
	
	@Before
	public void setUp() throws Exception {
		model = new FileDataModel(new File(root, "ratings.csv"));
		TaggingDataIO_ML instance = new TaggingDataIO_ML(new File(root, "tags.dat"));
		factorizer = new TagCoFiFactorizer(model, 
						TagCoFiFactorizer.SimilarityCalculator.COSINE, 10, 10, 0.1, 0.1, 0.1);
		taggingData = instance.readTaggingData(factorizer);
		factorizer.setUserTagMatrix(taggingData.getUserTaggingCountMatrix());
	}
	
	@After
	public void tearDown() {
	}

	/**
	 * Test of setUserTagMatrix method, of class TagCoFiFactorizer.
	@Test
	public void testSetUserTagMatrix() {
		System.out.println("setUserTagMatrix");
		Matrix userTagMatrix = null;
		TagCoFiFactorizer instance = null;
		instance.setUserTagMatrix(userTagMatrix);
		// TODO review the generated test code and remove the default call to fail.
		fail("The test case is a prototype.");
	}
	 */

	/**
	 * Test of factorize method, of class TagCoFiFactorizer.
	 */
	@Test
	public void testFactorize() throws Exception {
		System.out.println("factorize");
		TagCoFiFactorizer instance = factorizer;
		//Factorization expResult = null;
		Factorization result = instance.factorize();
		//assertEquals(expResult, result);
		// TODO review the generated test code and remove the default call to fail.
		//fail("The test case is a prototype.");
	}

	/**
	 * Test of userIndex method, of class TagCoFiFactorizer.
	@Test
	public void testUserIndex() {
		System.out.println("userIndex");
		long id = 0L;
		TagCoFiFactorizer instance = null;
		Integer expResult = null;
		Integer result = instance.userIndex(id);
		assertEquals(expResult, result);
		// TODO review the generated test code and remove the default call to fail.
		fail("The test case is a prototype.");
	}
	 */

	/**
	 * Test of itemIndex method, of class TagCoFiFactorizer.
	@Test
	public void testItemIndex() {
		System.out.println("itemIndex");
		long id = 0L;
		TagCoFiFactorizer instance = null;
		Integer expResult = null;
		Integer result = instance.itemIndex(id);
		assertEquals(expResult, result);
		// TODO review the generated test code and remove the default call to fail.
		fail("The test case is a prototype.");
	}
	 */

	/**
	 * Test of getUserCount method, of class TagCoFiFactorizer.
	@Test
	public void testGetUserCount() {
		System.out.println("getUserCount");
		TagCoFiFactorizer instance = null;
		int expResult = 0;
		int result = instance.getUserCount();
		assertEquals(expResult, result);
		// TODO review the generated test code and remove the default call to fail.
		fail("The test case is a prototype.");
	}
	 */

	/**
	 * Test of getItemCount method, of class TagCoFiFactorizer.
	@Test
	public void testGetItemCount() {
		System.out.println("getItemCount");
		TagCoFiFactorizer instance = null;
		int expResult = 0;
		int result = instance.getItemCount();
		assertEquals(expResult, result);
		// TODO review the generated test code and remove the default call to fail.
		fail("The test case is a prototype.");
	}
	 */


}

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.eniyitavsiye.mahoutx.svd;

import com.google.common.base.Preconditions;
import org.apache.mahout.cf.taste.common.NoSuchItemException;
import org.apache.mahout.cf.taste.common.NoSuchUserException;
import org.apache.mahout.cf.taste.impl.common.FastByIDMap;
import org.apache.mahout.cf.taste.impl.recommender.svd.Factorization;

/**
 *
 * @author tevfik
 */
public class NewFactorization {

    double avgRating;
    double[] userDeviations;
    double[] itemDeviations;
    /**
     * used to find the rows in the user features matrix by userID
     */
    private final FastByIDMap<Integer> userIDMapping;
    /**
     * used to find the rows in the item features matrix by itemID
     */
    private final FastByIDMap<Integer> itemIDMapping;
    /**
     * user features matrix
     */
    private final double[][] userFeatures;
    /**
     * item features matrix
     */
    private final double[][] itemFeatures;

    public NewFactorization(FastByIDMap<Integer> userIDMapping, FastByIDMap<Integer> itemIDMapping, double[][] userFeatures,
            double[][] itemFeatures, double avgRating, double[] userDeviations, double[] itemDeviations) {
        this.userIDMapping = Preconditions.checkNotNull(userIDMapping);
        this.itemIDMapping = Preconditions.checkNotNull(itemIDMapping);
        this.userFeatures = userFeatures;
        this.itemFeatures = itemFeatures;
        this.avgRating = avgRating;
        this.userDeviations = userDeviations;
        this.itemDeviations = itemDeviations;
    }

    public double getUserDeviation(long userID) throws NoSuchUserException {
        Integer index = userIDMapping.get(userID);
        if (index == null) {
            throw new NoSuchUserException(userID);
        }
        return userDeviations[index];
    }

    public double[] allUserDeviations() {
        return userDeviations;
    }

    public double getItemDeviation(long itemID) throws NoSuchItemException {
        Integer index = itemIDMapping.get(itemID);
        if (index == null) {
            throw new NoSuchItemException(itemID);
        }
        return itemDeviations[index];
    }

    public double[] allItemDeviations() {
        return itemDeviations;
    }

    public double[] getUserFeatures(long userID) throws NoSuchUserException {
        Integer index = userIDMapping.get(userID);
        if (index == null) {
            throw new NoSuchUserException(userID);
        }
        return userFeatures[index];
    }

    public double[][] allUserFeatures() {
        return userFeatures;
    }

    public double[][] allItemFeatures() {
        return itemFeatures;
    }

    public double[] getItemFeatures(long itemID) throws NoSuchItemException {
        Integer index = itemIDMapping.get(itemID);
        if (index == null) {
            throw new NoSuchItemException(itemID);
        }
        return itemFeatures[index];
    }
}

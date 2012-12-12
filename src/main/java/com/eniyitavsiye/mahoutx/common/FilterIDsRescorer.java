/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.eniyitavsiye.mahoutx.common;

import org.apache.mahout.cf.taste.impl.common.FastIDSet;
import org.apache.mahout.cf.taste.recommender.IDRescorer;

/**
 *
 * @author p.bell
 */
public class FilterIDsRescorer implements IDRescorer {
     FastIDSet allowedIDs;

    public FilterIDsRescorer(FastIDSet allowedIDs) {
        this.allowedIDs = allowedIDs;
    }

    @Override
    public double rescore(long id, double originalScore) {
        return originalScore;
    }

    @Override
    public boolean isFiltered(long id) {
        return !this.allowedIDs.contains(id);
    }
}

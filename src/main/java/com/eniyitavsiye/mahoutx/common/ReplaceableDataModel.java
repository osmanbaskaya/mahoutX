/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.eniyitavsiye.mahoutx.common;

import java.util.Collection;
import org.apache.mahout.cf.taste.common.Refreshable;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.impl.common.FastIDSet;
import org.apache.mahout.cf.taste.impl.common.LongPrimitiveIterator;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.model.PreferenceArray;

/**
 *
 * @author ceyhun
 */
public class ReplaceableDataModel implements DataModel {

	private DataModel delegate;

	public void setDelegate(DataModel delegate) {
		this.delegate = delegate;
	}

	public ReplaceableDataModel(DataModel delegate) {
		this.delegate = delegate;
	}

	@Override
	public LongPrimitiveIterator getUserIDs() throws TasteException {
		return delegate.getUserIDs();
	}

	@Override
	public PreferenceArray getPreferencesFromUser(long userID) throws TasteException {
		return delegate.getPreferencesFromUser(userID);
	}

	@Override
	public FastIDSet getItemIDsFromUser(long userID) throws TasteException {
		return delegate.getItemIDsFromUser(userID);
	}

	@Override
	public LongPrimitiveIterator getItemIDs() throws TasteException {
		return delegate.getItemIDs();
	}

	@Override
	public PreferenceArray getPreferencesForItem(long itemID) throws TasteException {
		return delegate.getPreferencesForItem(itemID);
	}

	@Override
	public Float getPreferenceValue(long userID, long itemID) throws TasteException {
		return delegate.getPreferenceValue(userID, itemID);
	}

	@Override
	public Long getPreferenceTime(long userID, long itemID) throws TasteException {
		return delegate.getPreferenceTime(userID, itemID);
	}

	@Override
	public int getNumItems() throws TasteException {
		return delegate.getNumItems();
	}

	@Override
	public int getNumUsers() throws TasteException {
		return delegate.getNumUsers();
	}

	@Override
	public int getNumUsersWithPreferenceFor(long itemID) throws TasteException {
		return delegate.getNumUsersWithPreferenceFor(itemID);
	}

	@Override
	public int getNumUsersWithPreferenceFor(long itemID1, long itemID2) throws TasteException {
		return delegate.getNumUsersWithPreferenceFor(itemID1, itemID2);
	}

	@Override
	public void setPreference(long userID, long itemID, float value) throws TasteException {
		delegate.setPreference(userID, itemID, value);
	}

	@Override
	public void removePreference(long userID, long itemID) throws TasteException {
		delegate.removePreference(userID, itemID);
	}

	@Override
	public boolean hasPreferenceValues() {
		return delegate.hasPreferenceValues();
	}

	@Override
	public float getMaxPreference() {
		return delegate.getMaxPreference();
	}

	@Override
	public float getMinPreference() {
		return delegate.getMinPreference();
	}

	@Override
	public void refresh(Collection<Refreshable> alreadyRefreshed) {
		delegate.refresh(alreadyRefreshed);
	}
	
}

package com.eniyitavsiye.mahoutx.common;

import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.impl.common.FastByIDMap;
import org.apache.mahout.cf.taste.impl.common.LongPrimitiveIterator;
import org.apache.mahout.cf.taste.impl.model.GenericDataModel;
import org.apache.mahout.cf.taste.impl.model.GenericUserPreferenceArray;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.model.Preference;
import org.apache.mahout.cf.taste.model.PreferenceArray;

import java.util.Arrays;

public class DataModelUtilities {

    public static FastByIDMap<PreferenceArray> extractAllPreferences(DataModel model) throws TasteException {

        FastByIDMap<PreferenceArray> preferences = new FastByIDMap<>(model.getNumUsers());
        LongPrimitiveIterator userIDs = model.getUserIDs();
        while (userIDs.hasNext()) {
            long id = userIDs.nextLong();
            preferences.put(id, model.getPreferencesFromUser(id));
        }
        return preferences;
    }


    public static DataModel addPreferece(ReplaceableDataModel model, Preference preference) throws TasteException {
        GenericDataModel newModel = new GenericDataModel(addRatingToPreferences(extractAllPreferences(model), preference));
        model.setDelegate(newModel);
        return newModel;
    }

    private static FastByIDMap<PreferenceArray> addRatingToPreferences(FastByIDMap<PreferenceArray> preferences, Preference preference) {
        long userId = preference.getUserID();
        long itemId = preference.getItemID();

        if (preferences.containsKey(userId)) {
            PreferenceArray oldArray = preferences.get(userId);

            if (oldArray.hasPrefWithItemID(itemId)) {
                oldArray.sortByItem();
                int i = Arrays.binarySearch(oldArray.getIDs(), itemId);
                oldArray.setValue(i, preference.getValue());
            } else {
                PreferenceArray newArray = new GenericUserPreferenceArray(oldArray.length() + 1);
                newArray.setUserID(0, userId);
                for (int i = 0; i < oldArray.length(); ++i) {
                    newArray.set(i, oldArray.get(i));
                }
                newArray.set(oldArray.length(), preference);
                preferences.put(userId, newArray);
            }

        } else {
            preferences.put(userId, new GenericUserPreferenceArray(Arrays.asList(preference)));
        }

        return preferences;
    }

    public static long getTotalPreferenceCount(DataModel model) throws TasteException {
        long totalPreferenceCount = 0L;
        LongPrimitiveIterator itemIDs = model.getItemIDs();
        while (itemIDs.hasNext()) {
          totalPreferenceCount += model.getPreferencesForItem(itemIDs.nextLong()).length();
        }
        return totalPreferenceCount;
    }

}

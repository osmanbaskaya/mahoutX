/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.eniyitavsiye.mahoutx.common;


import com.google.common.collect.Lists;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.impl.common.FastByIDMap;
import org.apache.mahout.cf.taste.impl.model.GenericUserPreferenceArray;
import org.apache.mahout.cf.taste.impl.model.jdbc.MySQLJDBCDataModel;
import org.apache.mahout.cf.taste.model.Preference;
import org.apache.mahout.cf.taste.model.PreferenceArray;
import org.apache.mahout.common.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

public class LimitMySQLJDBCDataModel extends MySQLJDBCDataModel {

	private static final long serialVersionUID = -2895240379301248474L;

	private static final Logger log = LoggerFactory.getLogger(LimitMySQLJDBCDataModel.class);
	private final DataSource dataSource;
	private String userIDColumn;
	private String itemIDColumn;
	private String preferenceColumn;
	private String preferenceTable;

	public LimitMySQLJDBCDataModel(DataSource dataSource,
					String preferenceTable, String userIDColumn, String itemIDColumn,
					String preferenceColumn, String timestampColumn) {
		super(dataSource, preferenceTable, userIDColumn, itemIDColumn,
						preferenceColumn, timestampColumn);
		this.dataSource = dataSource;
		this.userIDColumn = userIDColumn;
		this.itemIDColumn = itemIDColumn;
		this.preferenceColumn = preferenceColumn;
		this.preferenceTable = preferenceTable;
		// TODO Auto-generated constructor stub
	}

	@Override
	public FastByIDMap<PreferenceArray> exportWithPrefs() throws TasteException {
		// TODO Auto-generated method stub
		log.info("Exporting all data");

		Connection conn = null;
		Statement stmt = null;
		ResultSet rs = null;


		try {
			conn = dataSource.getConnection();
			stmt = conn.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
			stmt.setFetchDirection(ResultSet.FETCH_FORWARD);
			stmt.setFetchSize(getFetchSize());
			int offset = 0, limit = 400000, counter = 0;

			String query;

            log.info("before 500000 allocation: {}.", printFreeMemory());
			FastByIDMap<List<Preference>> currentPrefs = new FastByIDMap<>(500000);
			do {
				counter = 0;
				query = "SELECT " + userIDColumn + ", " + itemIDColumn + ", " + preferenceColumn + " FROM " + preferenceTable
								+ " WHERE id > " + offset + " AND id <= " + (offset + limit) + " ORDER BY " + userIDColumn + ", " + itemIDColumn;

				log.info("Executing SQL query (offset = {}) : {}", offset, query);
				rs = stmt.executeQuery(query);
				log.info("query executed. Current state of memory: {}", printFreeMemory());
				while (rs.next()) {
					counter++;
					long nextUserID = getLongColumn(rs, 1);
					List<Preference> prefs;
					if (currentPrefs.containsKey(nextUserID)) {
 						prefs = currentPrefs.get(nextUserID);
					} else {
						prefs = Lists.newArrayList();
						currentPrefs.put(nextUserID, prefs);
					}
					prefs.add(buildPreference(rs));
					//log.info("counter: {}, nextUserID: {}, nItems: {}.", new Object[] { counter, nextUserID, prefs.size() });
				}
				offset += limit;
			} while (counter != 0);

            FastByIDMap<PreferenceArray> result = new FastByIDMap<>(500000);

            for (int count = currentPrefs.size(); count >= 0; --count) {
                long id = currentPrefs.keySetIterator().next();
                List<Preference> preferences = currentPrefs.get(id);
                currentPrefs.remove(id);
                result.put(id, new GenericUserPreferenceArray(preferences));
            }

			return result;

		} catch (SQLException sqle) {
			log.info("Exception while exporting all data", sqle);
			throw new TasteException(sqle);
		} finally {
			IOUtils.quietClose(rs, stmt, conn);
		}
	}

    private String printFreeMemory() {
        String whole = "\nFree heap: " + Runtime.getRuntime().freeMemory() + "\n";
        try {
            Process p = Runtime.getRuntime().exec("free -m");

            BufferedReader reader =
                    new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                whole += line + "\n";
            }
        } catch (Exception e) {
            whole += "Some exception occurred.\n";
        }

        return whole;
    }

}

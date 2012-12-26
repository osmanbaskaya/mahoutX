/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.eniyitavsiye.mahoutx.common;


import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import javax.sql.DataSource;

import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.impl.common.FastByIDMap;
import org.apache.mahout.cf.taste.impl.model.GenericUserPreferenceArray;
import org.apache.mahout.cf.taste.impl.model.jdbc.MySQLJDBCDataModel;
import org.apache.mahout.cf.taste.model.Preference;
import org.apache.mahout.cf.taste.model.PreferenceArray;
import org.apache.mahout.common.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;


public class LimitMySQLJDBCDataModel extends MySQLJDBCDataModel {
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

	/**
	 * 
	 */
	private static final long serialVersionUID = -2895240379301248474L;
	
	@Override
	public FastByIDMap<PreferenceArray> exportWithPrefs() throws TasteException {
		// TODO Auto-generated method stub
		log.info("Exporting all data");

	    Connection conn = null;
	    Statement stmt = null;
	    ResultSet rs = null;

	    FastByIDMap<PreferenceArray> result = new FastByIDMap<PreferenceArray>();

	    try {
    	  conn = dataSource.getConnection();
	      stmt = conn.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
	      stmt.setFetchDirection(ResultSet.FETCH_FORWARD);
	      stmt.setFetchSize(getFetchSize());
	      int offset=0,limit=400000,counter=0;
	      
	      String query = "SELECT " + userIDColumn + ", " + itemIDColumn + ", " + preferenceColumn + " FROM " + preferenceTable
	              + " ORDER BY " + userIDColumn + ", " + itemIDColumn;

	      Long currentUserID = null;
	      List<Preference> currentPrefs = Lists.newArrayList();
	      do{
	    	  counter=0;
	    	  query = "SELECT " + userIDColumn + ", " + itemIDColumn + ", " + preferenceColumn + " FROM " + preferenceTable
		              + " ORDER BY " + userIDColumn + ", " + itemIDColumn + " LIMIT "+offset+","+limit;
		      log.info("Executing SQL query: {}", query);
			  rs = stmt.executeQuery(query);
		      log.info("query executed");
		      while (rs.next()) {
		    	counter++;
		        long nextUserID = getLongColumn(rs, 1);
		        if (currentUserID != null && !currentUserID.equals(nextUserID) && !currentPrefs.isEmpty()) {
		          result.put(currentUserID, new GenericUserPreferenceArray(currentPrefs));
		          currentPrefs.clear();
		        }
		        currentPrefs.add(buildPreference(rs));
		        currentUserID = nextUserID;
		      }
		      if (!currentPrefs.isEmpty()) {
		        result.put(currentUserID, new GenericUserPreferenceArray(currentPrefs));
		      }
		      offset += limit;
	      }while(counter==limit);

	      return result;

	    } catch (SQLException sqle) {
	      log.info("Exception while exporting all data", sqle);
	      throw new TasteException(sqle);
	    } finally {
	      IOUtils.quietClose(rs, stmt, conn);
	    }
	}

}

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.eniyitavsiye.mahoutx.common;

import com.google.common.collect.Lists;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.impl.common.FastByIDMap;
import org.apache.mahout.cf.taste.impl.common.FullRunningAverage;
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
    private double dataFraction;

    public LimitMySQLJDBCDataModel(DataSource dataSource,
                                   String preferenceTable, String userIDColumn, String itemIDColumn,
                                   String preferenceColumn, String timestampColumn,
                                   double dataFraction) {
        super(dataSource, preferenceTable, userIDColumn, itemIDColumn,
                preferenceColumn, timestampColumn);
        this.dataSource = dataSource;
        this.userIDColumn = userIDColumn;
        this.itemIDColumn = itemIDColumn;
        this.preferenceColumn = preferenceColumn;
        this.preferenceTable = preferenceTable;
        this.dataFraction = dataFraction;
        // TODO Auto-generated constructor stub
    }

    @Override
    public FastByIDMap<PreferenceArray> exportWithPrefs() throws TasteException {
        log.info("Exporting all data");

        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;


        try {
            conn = dataSource.getConnection();
            stmt = conn.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            stmt.setFetchDirection(ResultSet.FETCH_FORWARD);
            stmt.setFetchSize(getFetchSize());
            int offset = 0, counter = 1;

            String query;

            String rangeColumn = "user_id";
			int maxUserID = getMaxUserID(conn);
            int limit = maxUserID / 250;

            log.info("before 500000 allocation: {}.", printFreeMemory());
            FastByIDMap<PreferenceArray> result = new FastByIDMap<>(500_000);
            List<Preference> prefs = Lists.newArrayList();
            long currentUserID = -1;

            int userCount = 0;
            FullRunningAverage avg = new FullRunningAverage();
            boolean firstTime = true;
            boolean skipped;

            ResultSet userSet = conn
                    .createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)
                    .executeQuery("SELECT id as user_id FROM auth_user");
            int totalRowCount = 0;
            do {
                skipped = Math.random() > dataFraction;
                if (!skipped || firstTime) {
                    if (firstTime) {
                        if (userSet.next()) {
                            query = "SELECT " + userIDColumn + ", " + itemIDColumn + ", " + preferenceColumn
                                    + " FROM " + preferenceTable
                                    + " WHERE user_id = " + userSet.getLong(1);
                        } else {
                            firstTime = false;
                            counter = 1;
                            continue;
                        }
                    } else {
                        query = "SELECT " + userIDColumn + ", " + itemIDColumn + ", " + preferenceColumn
                                + " FROM " + preferenceTable
                                + " WHERE " + rangeColumn + " > " + offset + " AND " + rangeColumn + "  <= " + (offset + limit)
                                + " ORDER BY " + userIDColumn;
                    }

                    log.info("Executing SQL query (offset = {}) : \n{}", offset, query.substring(query.indexOf("WHERE")));
                    rs = stmt.executeQuery(query);
                    //log.info("Query executed. Current state of memory: {}", printFreeMemory());
                    int blockUserCount = 0;
                    counter = 0;

                    long blockStart = System.nanoTime();
                    FullRunningAverage avgPerLine = new FullRunningAverage();
                    long lineStart = blockStart;

                    while (rs.next()) {
                        counter++;
                        long nextUserID = getLongColumn(rs, 1);
                        if (nextUserID != currentUserID) {
                            ++blockUserCount;
                            if (currentUserID != -1) {
                                result.put(currentUserID, new GenericUserPreferenceArray(prefs));
                                prefs.clear();
                            }
                            currentUserID = nextUserID;
                        }
                        prefs.add(buildPreference(rs));
                        //log.info("counter: {}, nextUserID: {}, nItems: {}.", new Object[] { counter, nextUserID, prefs.size() });
                        long temp = System.nanoTime();
                        avgPerLine.addDatum((temp - lineStart) / Math.pow(10, 6));
                        lineStart = temp;
                    }
                    double timePassedBlock = (System.nanoTime() - blockStart) / Math.pow(10, 9);
                    avg.addDatum(timePassedBlock);
                    userCount += blockUserCount;
                    totalRowCount += counter;

                    log.info("\n#users so far = {}, #block users = {},#total rows = {}, #block row = {}.",
                            new Object[]{ userCount, blockUserCount, totalRowCount, counter });

                    log.info("\nBlock Time = {} (avg={}) secs with avg {} ms per row.",
                            new Object[]{ timePassedBlock, avg.getAverage(), avgPerLine.getAverage() });
                }

                offset += limit;
            } while (counter != 0 || skipped || firstTime);

            return result;

        } catch (SQLException sqle) {
            log.info("Exception while exporting all data", sqle);
            throw new TasteException(sqle);
        } finally {
            IOUtils.quietClose(rs, stmt, conn);
        }
    }

    private int getMaxUserID(Connection conn) throws SQLException {
        Statement stmt = conn.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
        stmt.execute("SELECT MAX(" + userIDColumn + ") FROM " + preferenceTable);
        ResultSet rs = stmt.getResultSet();
        rs.next();
        return rs.getInt(1);
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


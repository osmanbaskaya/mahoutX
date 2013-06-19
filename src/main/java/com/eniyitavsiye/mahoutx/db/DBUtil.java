/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.eniyitavsiye.mahoutx.db;


import com.mysql.jdbc.jdbc2.optional.MysqlDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author p.bell
 */
public class DBUtil {

    //TODO use property file to load information below
    private static final String host = "54.246.114.38";
    private static final int port = 8080;
    private static final String user = "root";
    private static final String password = "rast0gele1";
    private static final String database = "eniyitavsiye";
    private static final Logger log = Logger.getLogger(DBUtil.class.getName());

    public Collection<Long> getAuthUsers() {

        try {
            MysqlDataSource datasource = getDataSource();
            Connection con = datasource.getConnection();
            String sql = "select id from auth_user";

            PreparedStatement prest = con.prepareStatement(sql);
            ResultSet rs = prest.executeQuery();
            ArrayList<Long> items=new ArrayList<>();
            while (rs.next()) {
                items.add(new Long(rs.getString(1)));
            }
            prest.close();
            con.close();
            return items;
        } catch (SQLException | NumberFormatException ex) {
            log.log(Level.SEVERE, null, ex);
            //FIXME throw exception instead of returning empty list.
            throw new RuntimeException(ex);
        }
    }

    public Collection<Long> getUsersNotFollowing(String context, long id) {
        try {
            MysqlDataSource datasource = getDataSource();
            Connection con = datasource.getConnection();
            String sql = new StringBuilder()
                    .append("select id from auth_user where id not in ")
                    .append("(select followee_id from ") .append(context) .append("_follow ") .append("where ")
                    .append(id).append(" = follower_id) and id in ").append("(select distinct user_id from ")
                    .append(context).append("_rating) ").append("and id <> ").append(id).toString();

            PreparedStatement prest = con.prepareStatement(sql);
            ResultSet rs = prest.executeQuery();
            ArrayList<Long> users = new ArrayList<>();
            while (rs.next()) {
                users.add(new Long(rs.getString(1)));
            }
            prest.close();
            con.close();
            return users;
        } catch (SQLException | NumberFormatException ex) {
            log.log(Level.SEVERE, null, ex);
            throw new RuntimeException(ex);
        }
    }

    public Collection<Long> getItems(String context, String[] tags)  {

        try{
            MysqlDataSource datasource = getDataSource();
            Connection con = datasource.getConnection();

            StringBuilder sql = new StringBuilder()
                    .append("select t0.item_id from (SELECT item_id FROM ")
                    .append(context).append("_tag WHERE tag='").append(tags[0]).append("')t0 ");
            for (int i=1;i<tags.length;i++) {
                sql.append(" inner join ").append("(SELECT item_id FROM ").append(context).append("_tag WHERE tag='")
                        .append(tags[i]).append("')t").append(i).append(" on (t").append(i - 1).append(".item_id=t")
                        .append(i).append(".item_id) ").toString();
            }
            PreparedStatement prest = con.prepareStatement(sql.toString());
            ResultSet rs = prest.executeQuery();
            ArrayList<Long> items=new ArrayList<>();
            while (rs.next()) {
                items.add(new Long(rs.getString(1)));
            }
            prest.close();
            con.close();
            return items;
        } catch (SQLException | NumberFormatException ex) {
            log.log(Level.SEVERE, null, ex);
            //FIXME throw exception instead of returning empty list.
            return new ArrayList<>();
        }
    }

    public MysqlDataSource getDataSource() {
        MysqlDataSource dataSource = new MysqlDataSource();
        dataSource.setServerName(host);
        dataSource.setUser(user);
        dataSource.setPassword(password);
        dataSource.setDatabaseName(database);
        dataSource.setPort(port);
        dataSource.setAutoReconnect(true);
        dataSource.setAutoReconnectForPools(true);
        dataSource.setCachePreparedStatements(true);
        dataSource.setCachePrepStmts(true);
        dataSource.setCacheResultSetMetadata(true);
        dataSource.setAlwaysSendSetIsolation(false);
        dataSource.setElideSetAutoCommits(true);
        return dataSource;
    }

    public static void testUsersNotFollowing() {
        DBUtil util = new DBUtil();
        final Collection<Long> usersNotFollowing = util.getUsersNotFollowing("movie", 6044);
        System.out.println(usersNotFollowing);
        System.out.println(usersNotFollowing.contains(6044L));
        System.out.println(usersNotFollowing.contains(6043L));
    }

    public long getNumPreferences(String context) {
      StringBuilder sql = new StringBuilder()
                .append("select count(*) from ")
                .append(context).append("_rating");
      return executeCountQuery(sql);
    }

    public long getNumUsers(String context) {
      StringBuilder sql = new StringBuilder()
              .append("select count(distinct user_id) from ")
              .append(context).append("_rating");
      return executeCountQuery(sql);
    }

    public long getNumItems(String context) {
      StringBuilder sql = new StringBuilder()
              .append("select count(distinct item_id) from ")
              .append(context).append("_rating");
      return executeCountQuery(sql);
    }


  private long executeCountQuery(CharSequence sql) {
    try{
      MysqlDataSource datasource = getDataSource();
      Connection con = datasource.getConnection();

      PreparedStatement prest = con.prepareStatement(sql.toString());
      ResultSet rs = prest.executeQuery();
      rs.next();
      long prefCount = rs.getLong(1);
      prest.close();
      con.close();
      return prefCount;
    } catch (SQLException | NumberFormatException ex) {
      log.log(Level.SEVERE, null, ex);
      return -1;
    }
  }

}

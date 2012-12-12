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
import java.util.List;
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

    public List<Long> getItems(String context, String[] tags)  {
        
        try{
        MysqlDataSource datasource = getDataSource();
        Connection con = datasource.getConnection();
         
        String sql = "select t0.item_id from (SELECT item_id FROM "+context+
                    "_tag WHERE tag='"+tags[0] +"')t0 ";
        for (int i=1;i<tags.length;i++) {
            sql+= " inner join " +"(SELECT item_id FROM "+context+
                    "_tag WHERE tag='"+tags[i] +"')t"+i+ " on (t"+(i-1)+".item_id=t"+i+".item_id) " ;
        } 
        PreparedStatement prest = con.prepareStatement(sql);
        ResultSet rs = prest.executeQuery();
        ArrayList<Long> items=new ArrayList<Long>();
        while (rs.next()) {
            items.add(new Long(rs.getString(1)));
        }
        prest.close();
        con.close();
        return items;
        } catch (Exception ex) {
            log.log(Level.SEVERE, null, ex);
            return new ArrayList<Long>();
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
}

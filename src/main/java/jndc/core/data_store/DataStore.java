package jndc.core.data_store;

import jndc.core.UniqueBeanManage;
import jndc.core.config.UnifiedConfiguration;
import jndc.utils.ThreadQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DataStore {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final String SQL_LITE_DB = "jnc_db.db";

    private final String PROTOCOL = "jdbc:sqlite:";

    private volatile boolean initialized = false;


    private Connection connection;

    private static final Map<String, SQLiteTableSupport> liteTableSupports = new HashMap();

    static {
        liteTableSupports.put("channel_context_record", new SQLiteTableSupport("channel_context_record", "CREATE TABLE \"channel_context_record\"( \"id\" text(32) NOT NULL, \"ip\" text(16), \"port\" integer(8), \"timeStamp\" integer(64), PRIMARY KEY (\"id\"))"));
        liteTableSupports.put("server_port_bind", new SQLiteTableSupport("server_port_bind", "CREATE TABLE \"server_port_bind\"( \"id\" text(32) NOT NULL, \"name\" text(50), \"port\" integer(10), \"portEnable\" integer(2), \"routeTo\" text(16), PRIMARY KEY (\"id\"));"));
        liteTableSupports.put("server_ip_filter_rule", new SQLiteTableSupport("server_ip_filter_rule", "CREATE TABLE \"server_ip_filter_rule\"( \"id\" text(32) NOT NULL, \"ip\" text(32), \"type\" integer(1), PRIMARY KEY (\"id\"))"));
        liteTableSupports.put("ip_filter_record", new SQLiteTableSupport("ip_filter_record", "CREATE TABLE \"ip_filter_record\" ( \"id\" text(32) NOT NULL, \"ip\" TEXT(16), \"vCount\" integer(32), \"timeStamp\" integer(64), \"recordType\" integer(2), PRIMARY KEY (\"id\") )"));
    }

    public DataStore() {

    }

    private void lazyInit() {
        DBWrapper<SQLiteTableSupport> dbWrapper = DBWrapper.getDBWrapper(SQLiteTableSupport.class);
        List<SQLiteTableSupport> sqLiteTableSupports = dbWrapper.customQuery("select * from sqlite_master where type='table' ", null);
        sqLiteTableSupports.forEach(x -> {
            liteTableSupports.remove(x.getTbl_name());
        });

        liteTableSupports.forEach((k, v) -> {
            dbWrapper.customExecute(v.getSql(), null);
            logger.info("auto create table:" + v.getSql());
        });


    }

    private void init() {
        if (!initialized) {
            synchronized (this) {
                if (!initialized) {
                    UnifiedConfiguration bean = UniqueBeanManage.getBean(UnifiedConfiguration.class);
                    String runtimeDir = bean.getRuntimeDir();


                    if (!runtimeDir.endsWith(File.separator)) {
                        runtimeDir += File.separator;
                    }

                    String s = PROTOCOL + runtimeDir + SQL_LITE_DB;

                    try {
                        connection = DriverManager.getConnection(s);
                        initialized = true;
                        lazyInit();
                    } catch (SQLException sqlException) {
                        logger.error(sqlException.toString());
                        throw new RuntimeException(sqlException);
                    }
                }
            }
        }

        try {
            if (connection.isClosed()) {
                initialized = false;
                init();
            }
        } catch (SQLException sqlException) {
            initialized = false;
            logger.error(sqlException + "");
        }
    }

    public void execute(String sql, Object[] objects) {
        init();
        try {
            PreparedStatement preparedStatement = connection.prepareStatement(sql);
            if (objects == null) {
                objects = new Object[0];
            }
            for (int i = 0; i < objects.length; i++) {
                preparedStatement.setObject(i + 1, objects[i]);
            }
            preparedStatement.execute();

        } catch (SQLException sqlException) {
            logger.error(sqlException.toString());
            throw new RuntimeException("execute error: " + sqlException);
        }
    }

    public List<Map> executeQuery(String sql, Object[] objects) {
        init();
        ResultSet resultSet = null;
        try {
            PreparedStatement preparedStatement = connection.prepareStatement(sql);
            if (objects == null) {
                objects = new Object[0];
            }
            for (int i = 0; i < objects.length; i++) {
                preparedStatement.setObject(i + 1, objects[i]);
            }
            resultSet = preparedStatement.executeQuery();
            List<Map> maps = parseResult(resultSet);
            return maps;
        } catch (SQLException sqlException) {
            logger.error(sqlException.toString());
            throw new RuntimeException("execute error: " + sqlException);
        } finally {
            try {
                resultSet.close();
            } catch (SQLException sqlException) {
                throw new RuntimeException("result close fail:" + sqlException);
            }
        }
    }

    private List<Map> parseResult(ResultSet rs) throws SQLException {
        List<Map> list = new ArrayList();
        ResultSetMetaData md = rs.getMetaData();//获取键名

        int columnCount = md.getColumnCount();//获取行的数量
        while (rs.next()) {
            Map rowData = new HashMap();//声明Map
            for (int i = 1; i <= columnCount; i++) {
                rowData.put(md.getColumnName(i), rs.getObject(i));//获取键名及值
            }
            list.add(rowData);
        }
        return list;
    }


}

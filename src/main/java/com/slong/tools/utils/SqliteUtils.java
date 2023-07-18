package com.slong.tools.utils;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class SqliteUtils {
    private static String driverClass = "org.sqlite.JDBC";

    public SqliteUtils() {
    }

    public static Connection getConnection(String url) throws SQLException {
        Connection conn = null;
        try {
            Class.forName(driverClass);
            System.out.println("数据库驱动加载成功");
            conn = DriverManager.getConnection(url);
            System.out.println("数据库连接成功");
            System.out.print('\n');
        } catch (ClassNotFoundException var3) {
            var3.printStackTrace();
        } catch (SQLException var4) {
            var4.printStackTrace();
            throw var4;
        }
        return conn;
    }

    public static List populate(ResultSet rs, Class clazz) throws SQLException, InstantiationException, IllegalAccessException {
        ResultSetMetaData rsmd = rs.getMetaData();
        int colCount = rsmd.getColumnCount();
        List list = new ArrayList();
        Field[] fields = clazz.getDeclaredFields();
        while (rs.next()) {
            Object obj = clazz.newInstance();
            for (int i = 1; i <= colCount; ++i) {
                Object value = rs.getObject(i);
                for (int j = 0; j < fields.length; ++j) {
                    Field f = fields[j];
                    if (f.getName().equalsIgnoreCase(toCamelCase(rsmd.getColumnName(i)))) {
                        boolean flag = f.isAccessible();
                        f.setAccessible(true);
                        f.set(obj, value);
                        f.setAccessible(flag);
                    }
                }
            }
            list.add(obj);
        }
        return list;
    }

    public static int executeUpdate(Connection conn, String sql) throws SQLException {
        Statement statement = null;
        try {
            statement = conn.createStatement();
            return statement.executeUpdate(sql);
        } catch (SQLException var4) {
            var4.printStackTrace();
            throw var4;
        }
    }

    public static int executeUpdate(String url, String sql) throws SQLException {
        Statement statement = null;
        try {
            Connection conn = getConnection(url);
            statement = conn.createStatement();
            return statement.executeUpdate(sql);
        } catch (SQLException var4) {
            var4.printStackTrace();
            throw var4;
        }
    }

    public static ResultSet executeQuery(Connection conn, String sql) throws SQLException {
        Statement statement = null;
        try {
            statement = conn.createStatement();
            ResultSet res = statement.executeQuery(sql);
            return res;
        } catch (SQLException var4) {
            throw var4;
        }
    }

    public static ResultSet executeQuery(String url, String sql) throws SQLException {
        Statement statement = null;
        try {
            Connection   conn = getConnection(url);
            statement = conn.createStatement();
            ResultSet res = statement.executeQuery(sql);
            return res;
        } catch (SQLException var5) {
            var5.printStackTrace();
            throw var5;
        }
    }

    public static List executeQuery(String url, String sql, Class clazz) throws SQLException, InstantiationException, IllegalAccessException {
        try {
            ResultSet res = executeQuery(url, sql);
            return populate(res, clazz);
        } catch (SQLException var4) {
            var4.printStackTrace();
            throw var4;
        }
    }

    public static List executeQuery(Connection conn, String sql, Class clazz) throws SQLException, InstantiationException, IllegalAccessException {
        try {
            ResultSet res = executeQuery(conn, sql);
            return populate(res, clazz);
        } catch (SQLException var4) {
            var4.printStackTrace();
            throw var4;
        }
    }

    public static <T> boolean save(Connection conn, String tableName, T data) throws IllegalAccessException, SQLException {
        Class clazz = data.getClass();
        Field[] fields = clazz.getDeclaredFields();
        String sql = "INSERT INTO %s (%s)  VALUES (%s);";
        String columns = "";
        String values = "";
        for (int i = 0; i < fields.length; ++i) {
            Field field = fields[i];
            field.setAccessible(true);
            Object value = field.get(data);
            if (null != value && !"".equals(value)) {
                Type type = field.getGenericType();
                String var12 = type.toString();
                byte var13 = -1;
                switch (var12.hashCode()) {
                    case -1561781994:
                        if (var12.equals("class java.util.Date")) {
                            var13 = 1;
                        }
                        break;
                    case 673016845:
                        if (var12.equals("class java.lang.String")) {
                            var13 = 0;
                        }
                }
                switch (var13) {
                    case 0:
                        values = values + "'" + value + "',";
                        break;
                    case 1:
                        values = values + new Date(((Date) value).getTime()) + ",";
                        break;
                    default:
                        values = values + value + ",";
                }
                columns = columns + toUnderScoreCase(field.getName()) + ",";
            }
        }
        columns = columns.substring(0, columns.length() - 1);
        values = values.substring(0, values.length() - 1);
        sql = String.format(sql, tableName, columns, values);
        System.out.println("sql is :" + sql);
        executeUpdate(conn, sql);
        return true;
    }

    public static String toCamelCase(String s) {
        if (s == null) {
            return null;
        } else {
            StringBuilder sb = new StringBuilder(s.length());
            boolean upperCase = false;
            for (int i = 0; i < s.length(); ++i) {
                char c = s.charAt(i);
                if (c == '_') {
                    upperCase = true;
                } else if (upperCase) {
                    sb.append(Character.toUpperCase(c));
                    upperCase = false;
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }
    }

    public static String toUnderScoreCase(String s) {
        if (s == null) {
            return null;
        } else {
            StringBuilder sb = new StringBuilder();
            boolean upperCase = false;
            for (int i = 0; i < s.length(); ++i) {
                char c = s.charAt(i);
                boolean nextUpperCase = true;
                if (i < s.length() - 1) {
                    nextUpperCase = Character.isUpperCase(s.charAt(i + 1));
                }
                if (i > 0 && Character.isUpperCase(c)) {
                    if (!upperCase || !nextUpperCase) {
                        sb.append('_');
                    }
                    upperCase = true;
                } else {
                    upperCase = false;
                }
                sb.append(Character.toLowerCase(c));
            }
            return sb.toString();
        }
    }
}
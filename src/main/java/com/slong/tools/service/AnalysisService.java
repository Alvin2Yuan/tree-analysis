package com.slong.tools.service;

import cn.hutool.poi.excel.ExcelReader;
import cn.hutool.poi.excel.ExcelUtil;
import cn.hutool.poi.excel.ExcelWriter;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.slong.tools.utils.SqliteUtils;
import org.apache.commons.io.FileUtils;
import org.apache.poi.ss.usermodel.CellStyle;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class AnalysisService {

    private static String jdbcUrl = "jdbc:sqlite:tree.db";
    private Map<String, String> rgMap = new LinkedHashMap<String, String>();
    private Map<String, String> trMap = new LinkedHashMap<String, String>();


    public void process2(String excelPath) throws SQLException, IOException {
        File excel = new File(excelPath);
        if (excel.isDirectory()) {
            for (File file : excel.listFiles()) {
                ExcelReader reader = ExcelUtil.getReader(file);
                List<Map<String, Object>> dataList = reader.readAll();
                List<Map<String, Object>> newDataList = new ArrayList<Map<String, Object>>();
                for (Map<String, Object> dataRow : dataList) {
                    String szzc = dataRow.get("前树种组成").toString();
                    String origin = dataRow.get("起源").toString();
                    int fqll = Integer.valueOf(dataRow.get("伐前林龄").toString());
                    String lz = dataRow.get("龄组").toString();
                    //过滤
                    szzc = filterSzzc(szzc);
                    String[] array = null;
                    if (szzc.length() > 3) {
                        array = szzc.split("(?=\\d{1,100}[\\u4e00-\\u9fa5])");
                    } else {
                        array = new String[]{szzc};
                    }
//                   System.out.println(szzc);
                    if (szzc.equals("3云3水2落2白")) {
                        System.out.println(szzc);
                    }
                    //将拆分后的树种
                    validate3(origin, fqll, lz, array, dataRow, newDataList);

                }
                File wf=new File("D:\\zbj\\tree\\测试.xlsx");
                FileUtils.copyFile(file,wf);
                ExcelReader excelReader=ExcelUtil.getReader(wf);

                ExcelWriter writer = excelReader.getWriter();
                int colCount= writer.getColumnCount();
                writer.writeCellValue(colCount+1,0,"执行结果");

                writer.setColumnWidth(colCount+1,30);
                for (int i=0;i<newDataList.size();i++){
                    Map<String,Object> rowData= newDataList.get(i);
                    writer.writeCellValue(colCount+1,i+1,rowData.get("执行结果"));
                }

//                writer.passCurrentRow();
//                writer.write(newDataList, false);
                writer.flush();
                writer.close();
            }
        }
    }


    private String getGroup(String originName,String tree){
        if("人工".equals(originName)){
            return rgMap.get(tree);
        }else{
            return trMap.get(tree);
        }
    }
    private void validate3(String origin, int fqll, String lz, String[] array, Map<String, Object> dataMap, List<Map<String, Object>> newDataList) throws SQLException {
        String newszzc = "";
        boolean flag = true;
        StringBuffer removeTree=new StringBuffer();
        Map<String,JSONObject> groupMap=new LinkedHashMap<String, JSONObject>();
        for (String t : array) {

            String tree = t.replaceAll("\\d", "");
            String treeNum = t.replaceAll("[\\u4e00-\\u9fa5]", "");
            //1.按单个树种查询，使用所属起源直接查询
            String group = getGroup(origin,tree);
            boolean result = check(tree, group, origin, fqll, lz);
            String realOrigin ="";
            //2.如果树种不符合林龄要求的，去掉
            if (result) {
                realOrigin = origin.substring(0,1);
            }else{
                removeTree.append(t);
                continue;
            }
            //3.符合林龄要求的，树种按树种组相加 是否大于等于5
            JSONObject data=new JSONObject();
            if(groupMap.containsKey(group)){
                data=groupMap.get(group);
            }
            Integer total= data.getIntValue("total");
            total+=Integer.valueOf(treeNum);
            data.put("total",total);
            data.put("group",group);
            data.put("tree",tree);
            data.put("realOrigin",realOrigin);
            groupMap.put(group,data);
        }
        //4.拿到按树种组分后的map后开始比对
        Integer preTotal=0;
        Integer preOrderBy=10;
        String originName="";
        for(Map.Entry<String,JSONObject> entry:groupMap.entrySet()){
            //先看数字是否大于等于5
            String group=entry.getKey();
            JSONObject data= entry.getValue();
            Integer total= data.getInteger("total");
            String realOrigin= data.getString("realOrigin");
            //如果大于等于5 还需要看优先级
            if(total>=preTotal){
                int orderBy=0;
                if("慢针".equals(group)){
                    orderBy=1;
                }else if("中针".equals(group)){
                    orderBy=2;
                }else if("慢阔".equals(group)){
                    orderBy=3;
                }else if("中阔".equals(group)){
                    orderBy=4;
                }else if("速阔".equals(group)){
                    orderBy=5;
                }
                //判断优先级
                if(preOrderBy>orderBy){
                    preOrderBy=orderBy;
//                    preTotal=total;
                    originName=realOrigin;
                }
            }
        }

        //如果没有树种组大于等于5的 报错
        if (!"".equals(originName)) {
            for (String t : array) {
                if (!removeTree.toString().contains(t)) {
                    String tree = t.replaceAll("\\d", "");
                    String treeNum = t.replaceAll("[\\u4e00-\\u9fa5]", "");
                    newszzc += treeNum + originName + tree;
                }
            }
        }else{
            newszzc = "错误";
        }
        System.out.println("执行结果:" + newszzc);
        Map<String, Object> map = new LinkedHashMap<String, Object>(dataMap);
        map.put("执行结果", newszzc);
        newDataList.add(map);
    }



    public void process(String excelPath) throws SQLException {
        File excel = new File(excelPath);
        if (excel.isDirectory()) {
            for (File file : excel.listFiles()) {
                ExcelReader reader = ExcelUtil.getReader(file);
                List<Map<String, Object>> dataList = reader.readAll();
                List<Map<String, Object>> newDataList = new ArrayList<Map<String, Object>>();
                for (Map<String, Object> dataRow : dataList) {
                    String szzc = dataRow.get("前树种组成").toString();
                    String origin = dataRow.get("起源").toString();
                    int fqll = Integer.valueOf(dataRow.get("伐前林龄").toString());
                    String lz = dataRow.get("龄组").toString();
                    //过滤
                    szzc = filterSzzc(szzc);
                    String[] array = null;
                    if (szzc.length() > 3) {
                        array = szzc.split("(?=\\d{1,100}[\\u4e00-\\u9fa5])");
                    } else {
                        array = new String[]{szzc};
                    }
//                   System.out.println(szzc);
                    if (szzc.equals("3杨3落2水1白1榆")) {
                        System.out.println(szzc);
                    }


                    //1.先判断单一树种是否有大于等于5
                    String maxTreeName = null;   //大于等于5树种名称
                    for (String t : array) {
                        String tree = t.replaceAll("\\d", "");
                        String treeNum = t.replaceAll("[\\u4e00-\\u9fa5]", "");
                        Integer num = Integer.valueOf(treeNum);
                        if (num >= 5) {
                            maxTreeName = tree;
                            break;
                        }
                    }
                    //1.1 如果单一树种大于等于5，则直接查询对照表
                    if (null != maxTreeName) {
                        String treeGroupName = getTreeGroupName(origin, maxTreeName);
                        JSONObject treeObj = new JSONObject();
                        treeObj.put("树种", maxTreeName);     //使用大于等于5的树种信息
                        treeObj.put("树种组", treeGroupName);
                        treeObj.put("起源", origin);
                        treeObj.put("林龄", fqll);
                        treeObj.put("龄组", lz);
                        int count = countTreeGroup(treeObj);
                        String newszzc = "";
                        if (count == 1) {
                            newszzc = getNewszzc(origin, array);
                            System.out.println("正确:" + newszzc);

                        } else {
                            newszzc = "错误";
                            System.out.println("错误:" + JSONObject.toJSONString(dataRow));
                        }
                        Map<String, Object> map = new LinkedHashMap<String, Object>(dataRow);

                        map.put("执行结果", newszzc);
                        newDataList.add(map);
                    } else {
                        //2.没有单一树种大于等于5的 需要先根据起源 按针和阔分组相加然后比对
//                       int total1=0;  //针计数
//                       int total2=0;  //阔计数
//                       for(String t:array){
//
//                           String tree=t.replaceAll("\\d","");
//                           String treeNum=t.replaceAll("[\\u4e00-\\u9fa5]","");
//
//                           if("人工".equals(origin)){
//                               if(rgMap.containsKey(tree)){
//                                   String group= rgMap.get(tree);
//                                   if(group.endsWith("针")){
//                                       total1+= Integer.valueOf(treeNum);
//                                   }else{
//                                       total2+= Integer.valueOf(treeNum);
//                                   }
//                               }
//                           }else if("天然".equals(origin)){
//                               if(trMap.containsKey(tree)){
//                                   String group= trMap.get(tree);
//                                   if(group.endsWith("针")){
//                                       total1+= Integer.valueOf(treeNum);
//                                   }else{
//                                       total2+= Integer.valueOf(treeNum);
//                                   }
//                               }
//                           }
//                       }
//                       //比对针和阔计数 决定按哪个树种组去查询
//                       String group="阔";
//                       if(total1>=total2){
//                           group="针";
//                       }
//
//                       JSONObject treeObj=new JSONObject();
////                       treeObj.put("树种",maxTreeName);
//                       treeObj.put("树种组",group);
//                       treeObj.put("起源",origin);
//                       treeObj.put("林龄",fqll);
//                       treeObj.put("龄组",lz);
//                       int count=countTreeGroup(treeObj);
//                       if(count==1){
//                           //设置人/云
//                           String newszzc = getNewszzc(origin, array);
//                           System.out.println("==========正确:"+newszzc);
//                       }else{
//                           System.out.println("错误:"+JSONObject.toJSONString(dataRow));
//                       }
                        validate2(origin, fqll, lz, array, dataRow, newDataList);
                    }

                }
                ExcelWriter writer = ExcelUtil.getWriter("d:\\test\\tree\\excel\\测试.xlsx");
                writer.write(newDataList, true);
                writer.flush();
                writer.close();

            }
        }

    }

    private void validate2(String origin, int fqll, String lz, String[] array, Map<String, Object> dataMap, List<Map<String, Object>> newDataList) throws SQLException {
        String newszzc = "";
        boolean flag = true;
        for (String t : array) {

            String tree = t.replaceAll("\\d", "");
            String treeNum = t.replaceAll("[\\u4e00-\\u9fa5]", "");
            //1.按单个树种查询，使用所属起源直接查询

            String originName = "人工";
            String group = rgMap.get(tree);
            String realOrigin = "";
            if (null != group) {
                boolean result = check(tree, group, originName, fqll, lz);
                //如果所属起源和对照表都匹配则算所属起源
                if (result) {
                    realOrigin = "人";
                }
            }
            originName = "天然";
            group = trMap.get(tree);

            if (null != group) {
                boolean result = check(tree, group, originName, fqll, lz);
                //如果所属起源和对照表都匹配则算所属起源
                if (result) {
                    if ("人".equals(realOrigin)) {
                        System.out.println("警告，树种既符合人工又符合天然：" + JSONObject.toJSONString(dataMap));
                    }
                    if (originName.equals(origin) || "".equals(realOrigin)) {
                        realOrigin = "天";
                    }


                }
            }
            //没有出错再继续
            if ("".equals(realOrigin)) {
                flag = false;
                System.out.println("错误，树种两种起源都不符合：" + JSONObject.toJSONString(dataMap));
            } else {
                newszzc += treeNum + realOrigin + tree;
            }
        }
        if (flag) {
            System.out.println("正确:" + newszzc);
        } else {
            newszzc = "错误";
        }
        Map<String, Object> map = new LinkedHashMap<String, Object>(dataMap);
        map.put("执行结果", newszzc);
        newDataList.add(map);
    }

    private boolean check(String treeName, String group, String origin, int fqll, String lz) throws SQLException {
        JSONObject treeObj = new JSONObject();
        treeObj.put("树种", treeName);
        treeObj.put("树种组", group);
        treeObj.put("起源", origin);
        treeObj.put("林龄", fqll);
        treeObj.put("龄组", lz);
        int count = countTreeGroup(treeObj);
        return count == 1;
    }

    private String getNewszzc(String origin, String[] array) {
        String newszzc = "";
        for (String t : array) {

            String tree = t.replaceAll("\\d", "");
            String treeNum = t.replaceAll("[\\u4e00-\\u9fa5]", "");

            if ("人工".equals(origin)) {
                if (rgMap.containsKey(tree)) {
                    newszzc = newszzc + treeNum + "人" + tree;
                }
            } else {
                if (trMap.containsKey(tree)) {
                    newszzc = newszzc + treeNum + "天" + tree;
                }
            }
        }
        return newszzc;
    }

    private String getTreeGroupName(String origin, String maxTreeName) {
        String treeGroup = null;
        if ("人工".equals(origin)) {
            treeGroup = rgMap.get(maxTreeName);
        } else if ("天然".equals(origin)) {
            treeGroup = trMap.get(maxTreeName);
        }
        return treeGroup;
    }

    private void getTreeMap() throws SQLException {
        String sql = "select * from origin";

        ResultSet resultSet = SqliteUtils.executeQuery(jdbcUrl, sql);

        while (resultSet.next()) {
            String tree = resultSet.getString("树种");
            String treeGroup = resultSet.getString("树种组");
            String origin = resultSet.getString("起源");
            if ("人工".equals(origin)) {
                rgMap.put(tree, treeGroup);
            } else if ("天然".equals(origin)) {
                trMap.put(tree, treeGroup);
            }
        }
    }

    private int countTreeGroup(JSONObject tree) throws SQLException {
        String sql = "SELECT count(*) from tree_group where " +
                "   树种组 like '%" + tree.getString("树种组") + "' and 起源='" + tree.getString("起源") + "'" +
                "  and 起始林龄<=" + tree.getInteger("林龄") + " and 截止林龄>=" + tree.getInteger("林龄") +
                "  and 龄组='" + tree.getString("龄组") + "'";
        if (null != tree.getString("树种")) {
            sql += " and 树种 like '" + tree.getString("树种") + "%' ";
        }


        System.out.println(sql);
        ResultSet resultSet = SqliteUtils.executeQuery(jdbcUrl, sql);
        int count = resultSet.getInt(1);
        return count;
    }

    /**
     * 过滤树种组成 去除+ 和-的树
     *
     * @param szzc 树种组成
     * @return
     */
    private String filterSzzc(String szzc) {
        if (szzc.contains("+")) {
            szzc = szzc.substring(0, szzc.indexOf("+"));
        }
        if (szzc.contains("-")) {
            szzc = szzc.substring(0, szzc.indexOf("-"));
        }
        return szzc;
    }

    public static void main(String[] args) throws Exception {
        String dbFile = AnalysisService.class.getClassLoader().getResource("tree.db").getFile();
        jdbcUrl = "jdbc:sqlite:src/main/resources/tree.db";
//        String excelPath = "D:\\test\\tree\\excel";
        String excelPath = "D:\\zbj\\tree\\excel";
        AnalysisService service = new AnalysisService();
        service.getTreeMap();
        service.process2(excelPath);
    }
}

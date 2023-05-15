package com.slong.tools.service;

import cn.hutool.poi.excel.ExcelReader;
import cn.hutool.poi.excel.ExcelUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.slong.tools.utils.SqliteUtils;

import java.io.File;
import java.lang.reflect.Array;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class AnalysisService {

    private static String jdbcUrl="jdbc:sqlite:tree.db";
    private Map<String,String> rgMap=new LinkedHashMap<String, String>();
    private Map<String,String> trMap=new LinkedHashMap<String, String>();
    public void process(String excelPath) throws SQLException {
        File excel=new File(excelPath);
        if(excel.isDirectory()){
            for(File file:excel.listFiles()){
               ExcelReader reader= ExcelUtil.getReader(file);
               List<Map<String,Object>> dataList= reader.readAll();
               for(Map<String,Object> dataRow:dataList){
                  String szzc= dataRow.get("前树种组成").toString();
                   String origin= dataRow.get("起源").toString();
                  int fqll=Integer.valueOf(dataRow.get("伐前林龄").toString());
                  String lz=dataRow.get("龄组").toString();
                   //过滤
                   szzc= filterSzzc(szzc);
                   String[] array=null;
                   if(szzc.length()>3){
                         array= szzc.split("(?=\\d{1,100}[\\u4e00-\\u9fa5])");

                   }else {
                       array=new String[]{szzc};

                   }
//                   System.out.println(szzc);
//                   if(szzc.equals("3柞3椴2枫1阔1榆")){
//                       System.out.println(szzc);
//                   }


                 //1.先判断单一树种是否有大于等于5
                   String maxTreeName=null;   //大于等于5树种名称
                   for(String t:array){
                       String tree=t.replaceAll("\\d","");
                       String treeNum=t.replaceAll("[\\u4e00-\\u9fa5]","");
                       Integer num=Integer.valueOf(treeNum);
                       if(num>=5){
                           maxTreeName=tree;
                           break;
                       }
                   }

                   //1.1 如果单一树种大于等于5，则直接查询对照表
                   if(null!=maxTreeName){

                       String treeGroupName= getTreeGroupName(origin, maxTreeName);

                       JSONObject treeObj=new JSONObject();
                       treeObj.put("树种",maxTreeName);     //使用大于等于5的树种信息
                       treeObj.put("树种组",treeGroupName);
                       treeObj.put("起源",origin);
                       treeObj.put("林龄",fqll);
                       treeObj.put("龄组",lz);
                       int count=countTreeGroup(treeObj);
                       if(count==1){
                           String newszzc = getNewszzc(origin, array);
                           System.out.println("正确:"+newszzc);
                       }else{
                           System.out.println("错误:"+JSONObject.toJSONString(dataRow));
                       }
                   }else {
                       //2.没有单一树种大于等于5的 需要先根据起源 按针和阔分组相加然后比对
                       int total1=0;  //针计数
                       int total2=0;  //阔计数
                       for(String t:array){

                           String tree=t.replaceAll("\\d","");
                           String treeNum=t.replaceAll("[\\u4e00-\\u9fa5]","");

                           if("人工".equals(origin)){
                               if(rgMap.containsKey(tree)){
                                   String group= rgMap.get(tree);
                                   if(group.endsWith("针")){
                                       total1+= Integer.valueOf(treeNum);
                                   }else{
                                       total2+= Integer.valueOf(treeNum);
                                   }
                               }
                           }else if("天然".equals(origin)){
                               if(trMap.containsKey(tree)){
                                   String group= trMap.get(tree);
                                   if(group.endsWith("针")){
                                       total1+= Integer.valueOf(treeNum);
                                   }else{
                                       total2+= Integer.valueOf(treeNum);
                                   }
                               }
                           }
                       }
                       //比对针和阔计数 决定按哪个树种组去查询
                       String group="阔";
                       if(total1>=total2){
                           group="针";
                       }

                       JSONObject treeObj=new JSONObject();
//                       treeObj.put("树种",maxTreeName);
                       treeObj.put("树种组",group);
                       treeObj.put("起源",origin);
                       treeObj.put("林龄",fqll);
                       treeObj.put("龄组",lz);
                       int count=countTreeGroup(treeObj);
                       if(count==1){

                           //设置人/云
                           String newszzc = getNewszzc(origin, array);
                           System.out.println("==========正确:"+newszzc);

                       }else{
                           System.out.println("错误:"+JSONObject.toJSONString(dataRow));
                       }

                   }
//                    for(int i=0;i<array.length;){
//                        System.out.println(array[i]+array[i+1]);
//                        i=i+2;
//                    }

               }


            }
        }

    }

    private String getNewszzc(String origin, String[] array) {
        String newszzc="";
        for(String t: array) {

            String tree = t.replaceAll("\\d", "");
            String treeNum = t.replaceAll("[\\u4e00-\\u9fa5]", "");

            if("人工".equals(origin)) {
                if (rgMap.containsKey(tree)) {
                      newszzc=newszzc+treeNum+"人"+tree;
                }
            }else{
                if (trMap.containsKey(tree)) {
                    newszzc=newszzc+treeNum+"天"+tree;
                }
            }
        }
        return newszzc;
    }

    private String getTreeGroupName(String origin, String maxTreeName) {
        String treeGroup=null;
        if("人工".equals(origin)){
            treeGroup=rgMap.get(maxTreeName);
        }else if("天然".equals(origin)){
            treeGroup=trMap.get(maxTreeName);
        }
        return treeGroup;
    }

    private void getTreeMap() throws SQLException {
        String sql="select * from origin";

        ResultSet resultSet= SqliteUtils.executeQuery(jdbcUrl,sql);

        while (resultSet.next()){
            String tree= resultSet.getString("树种");
            String treeGroup= resultSet.getString("树种组");
            String origin=resultSet.getString("起源");
            if("人工".equals(origin)){
                rgMap.put(tree,treeGroup);
            } else if ("天然".equals(origin)) {
                trMap.put(tree,treeGroup);
            }
        }
    }

    private int countTreeGroup(JSONObject tree) throws SQLException {
        String sql="SELECT count(*) from tree_group where "+
                "   树种组 like '%"+tree.getString("树种组")+"' and 起源='"+tree.getString("起源")+"'"+
                "  and 起始林龄<="+tree.getInteger("林龄")+" and 截止林龄>="+tree.getInteger("林龄")+
                "  and 龄组='"+tree.getString("龄组")+"'";
        if(null!=tree.getString("树种")){
            sql+=" and 树种 like '"+tree.getString("树种")+"%' ";
        }


        System.out.println(sql);
        ResultSet resultSet= SqliteUtils.executeQuery(jdbcUrl,sql);
        int count= resultSet.getInt(1);
        return count;
    }

    /**
     * 过滤树种组成 去除+ 和-的树
     * @param szzc 树种组成
     * @return
     */
    private String filterSzzc(String szzc){
        if (szzc.contains("+")) {
            szzc = szzc.substring(0, szzc.indexOf("+"));
        }
        if(szzc.contains("-")){
            szzc=szzc.substring(0,szzc.indexOf("-"));
        }
        return szzc;
    }
    public static void main( String[] args ) throws Exception {
        String dbFile=AnalysisService.class.getClassLoader().getResource("tree.db").getFile();
        jdbcUrl="jdbc:sqlite:src/main/resources/tree.db";
        String excelPath="D:\\zbj\\tree\\excel";
        AnalysisService service=new AnalysisService();
        service.getTreeMap();
        service.process(excelPath);
    }
}

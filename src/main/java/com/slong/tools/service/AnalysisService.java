package com.slong.tools.service;

import cn.hutool.poi.excel.ExcelReader;
import cn.hutool.poi.excel.ExcelUtil;
import cn.hutool.poi.excel.ExcelWriter;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.slong.tools.utils.SqliteUtils;
import org.apache.commons.io.FileUtils;
import org.apache.poi.hssf.util.HSSFColor;
import org.apache.poi.ss.usermodel.*;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class AnalysisService {

    public static String jdbcUrl = "jdbc:sqlite:src/main/resources/tree.db";
    private Map<String, String> rgMap = new LinkedHashMap<String, String>();
    private Map<String, String> trMap = new LinkedHashMap<String, String>();

    private static Map<String, Integer> treeGroupOrderByMap = new LinkedHashMap<String, Integer>();

    private static Map<String, String> treeDensityGroupMap = new LinkedHashMap<String, String>();

    public Connection connection=null;

    public void process2(String excelPath,String outPath) throws SQLException, IOException {
        File excel = new File(excelPath);
        if (excel.isDirectory()) {
            for (File file : excel.listFiles()) {
                ExcelReader reader = ExcelUtil.getReader(file);
                List<Map<String, Object>> dataList = reader.readAll();
                List<Map<String, Object>> newDataList = new ArrayList<Map<String, Object>>();
                Map<String, List<Map<String, Object>>> colorDataMap = new HashMap<String, List<Map<String, Object>>>();

//                List<Map<String, Object>> yellowDataList = new ArrayList<Map<String, Object>>();
                int rowNum = 0;
                for (Map<String, Object> dataRow : dataList) {
                    rowNum++;
                    String szzc = dataRow.get("前树种组成") != null ? dataRow.get("前树种组成").toString() : null;
                    if (null == szzc || "".equals(szzc)) {
                        continue;
                    }
                    String origin = dataRow.get("起源").toString();
                    String sllb = dataRow.get("森林类别").toString();
                    String cflx = dataRow.get("采伐类型").toString();
                    String cffs = dataRow.get("采伐方式").toString();
                    int xjqd = Integer.valueOf(dataRow.get("蓄积强度").toString());
                    double fqybd = Double.valueOf(dataRow.get("伐前郁闭度").toString());
                    double fhybd = Double.valueOf(dataRow.get("伐后郁闭度").toString());

                    int fqll = Integer.valueOf(dataRow.get("伐前林龄").toString());
                    int xj = Integer.valueOf(dataRow.get("伐前胸径").toString());
                    int sg = Integer.valueOf(dataRow.get("伐前树高").toString());
                    String lz = dataRow.get("龄组").toString();

                    //判断树高和胸径是否合格
                    boolean isValid = validateSgAndXj(sg, xj);
                    Map<String, Object> sgxjMap = new LinkedHashMap<String, Object>(dataRow);
                    if (!isValid) {
                        sgxjMap.put("树高和胸径比对结果", "错误");

                    } else {
                        sgxjMap.put("树高和胸径比对结果", "正确");
                    }
                    List<Map<String, Object>> sgxjListMap = new ArrayList<Map<String, Object>>();
                    if (colorDataMap.containsKey("树高和胸径比对结果")) {
                        sgxjListMap = colorDataMap.get("树高和胸径比对结果");
                    }
                    String color = "";
                    sgxjMap.put("rowNum", rowNum);
                    sgxjMap.put("color", color);
                    sgxjListMap.add(sgxjMap);
                    colorDataMap.put("树高和胸径比对结果", sgxjListMap);
//                    newDataList.add(sgxjMap);

                    //蓄积强度校验
                    if (cflx.equals("抚育采伐")) {
                        isValid = validateXjqd(sllb, cflx, lz, xjqd, origin);

                        Map<String, Object> xjqdMap = new LinkedHashMap<String, Object>(dataRow);
                        if (!isValid) {
                            xjqdMap.put("蓄积强度结果", "错误");

                        } else {
                            xjqdMap.put("蓄积强度结果", "正确");
                        }
                        List<Map<String, Object>> listMap = new ArrayList<Map<String, Object>>();
                        if (colorDataMap.containsKey("蓄积强度结果")) {
                            listMap = colorDataMap.get("蓄积强度结果");
                        }
                        color = "";
                        if (lz.equals("近熟林")) {
                            color = "yellow";
                        }
                        xjqdMap.put("rowNum", rowNum);
                        xjqdMap.put("color", color);
                        listMap.add(xjqdMap);
                        colorDataMap.put("蓄积强度结果", listMap);
                    }
                    //判断郁闭度
                    if (cflx.equals("抚育采伐")) {
                        sgxjMap = new LinkedHashMap<String, Object>();
                        isValid = validateYbd(sllb, cflx, cffs, lz, fqybd, fhybd);
                        if (!isValid) {
                            sgxjMap.put("郁闭度验证结果", "错误");

                        } else {
                            sgxjMap.put("郁闭度验证结果", "正确");
                        }
                        sgxjListMap = new ArrayList<Map<String, Object>>();
                        if (colorDataMap.containsKey("郁闭度验证结果")) {
                            sgxjListMap = colorDataMap.get("郁闭度验证结果");
                        }
                        color = "";
                        sgxjMap.put("rowNum", rowNum);
                        sgxjMap.put("color", color);
                        sgxjListMap.add(sgxjMap);
                        colorDataMap.put("郁闭度验证结果", sgxjListMap);
                    }


                    //过滤
                    szzc = filterSzzc(szzc);
                    String[] array = null;
                    if (szzc.length() > 3) {
                        array = szzc.split("(?=\\d{1,100}[\\u4e00-\\u9fa5])");
                    } else {
                        array = new String[]{szzc};
                    }
                    System.out.println("处理树种组成:" + szzc);
                    if (szzc.equals("3云3水2落2白")) {
                        System.out.println(szzc);
                    }
                    origin = origin.replaceAll("林", "");
                    //将拆分后的树种
                    if ("天然".equals(origin)) {
                        List<String> list = getTrArrays(origin, array);
                        String[] newArray = new String[list.size()];
                        list.toArray(newArray);
                        validateTr(origin, fqll, lz, array, newArray, dataRow, newDataList);

                    } else {
                        //1.是否有单一树种5层及5层以上的，没有则报错
                        List<String> tree = getRgArrays(origin, array);
                        if (tree.isEmpty()) {
                            Map<String, Object> map2 = new LinkedHashMap<String, Object>(dataRow);
                            map2.put("执行结果", "错误");
                            map2.put("处理树种组成", "");
                            newDataList.add(map2);
                        } else {
                            String[] newArray = new String[tree.size()];
                            tree.toArray(newArray);
                            validateRg(origin, fqll, lz, array, newArray, dataRow, newDataList);
                        }

                    }


                }
                File wf = new File(outPath);
                String destPath =wf.getAbsolutePath() ;
                if(wf.isDirectory()){
                    destPath+="/"+file.getName();
                }
                File destFile = new File(destPath);
                FileUtils.copyFile(file, destFile);
                ExcelReader excelReader = ExcelUtil.getReader(destFile);

                ExcelWriter writer = excelReader.getWriter();
                int colCount = writer.getColumnCount();
                writer.writeCellValue(colCount + 1, 0, "经营密度");
                writer.writeCellValue(colCount + 2, 0, "执行结果");
                writer.writeCellValue(colCount + 3, 0, "处理树种组成");
//                writer.writeCellValue(colCount+3,0,"树高和胸径比对结果");
//                writer.writeCellValue(colCount+3,0,"蓄积强度结果");
                writer.setColumnWidth(colCount + 1, 10);
                writer.setColumnWidth(colCount + 2, 10);
                writer.setColumnWidth(colCount + 3, 30);
//                writer.setColumnWidth(colCount+3,30);
                List<String> checkList = new ArrayList<String>();
                for (int i = 0; i < newDataList.size(); i++) {
                    Map<String, Object> rowData = newDataList.get(i);
                    String result = rowData.get("执行结果").toString();
                    String result2 = rowData.get("处理树种组成").toString();
                    String origin = rowData.get("起源").toString();
                    origin = origin.replaceAll("林", "");
                    int fhxj = Integer.parseInt(rowData.get("伐后胸径").toString());
                    if ("错误".equals(result) || "错误".equals(result2)) {
                        checkList.add("错误");
                    } else {
                        checkList.add("正确");
                    }
                    //最低经营密度
                    Integer minValue = getZdmd(origin,result2, fhxj);
                    writer.writeCellValue(colCount + 1, i + 1, minValue);
                    writer.writeCellValue(colCount + 2, i + 1, result);
                    writer.writeCellValue(colCount + 3, i + 1, result2);
//                    writer.writeCellValue(colCount+3,i+1,rowData.get("树高和胸径比对结果"));
                }
                for (Map.Entry<String, List<Map<String, Object>>> entry : colorDataMap.entrySet()) {
                    String colName = entry.getKey();
                    List<Map<String, Object>> colDataList = entry.getValue();
                    int lastCellIndex = writer.getColumnCount();
                    int newCellIndex = lastCellIndex;
                    writer.writeCellValue(newCellIndex, 0, colName);
                    writer.setColumnWidth(newCellIndex, 15);
                    for (int i = 0; i < colDataList.size(); i++) {
                        Map<String, Object> rowData = colDataList.get(i);
                        int rowIndex = Integer.parseInt(rowData.get("rowNum").toString());
                        String color = rowData.get("color").toString();
                        String colData = rowData.get(colName).toString();
                        if ("错误".equals(colData)) {
                            checkList.set(rowIndex - 1, "错误");
                        }
                        writer.writeCellValue(newCellIndex, rowIndex, colData);
                        if (!"".equals(color)) {
                            CellStyle cellStyle = writer.createCellStyle();
                            cellStyle.setAlignment(HorizontalAlignment.CENTER);
                            if ("yellow".equals(color)) {
                                cellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                                cellStyle.setFillForegroundColor(IndexedColors.YELLOW.index);
//                                cellStyle.setFillForegroundColor(HSSFColor.HSSFColorPredefined.YELLOW.getIndex());
                            }
                            writer.setStyle(cellStyle, newCellIndex, rowIndex);

                        }

                    }
                }
                int lastCellIndex = writer.getColumnCount();
                writer.writeCellValue(lastCellIndex, 0, "全部验证结果");
                for (int i = 0; i < checkList.size(); i++) {
                    writer.writeCellValue(lastCellIndex, i + 1, checkList.get(i));
                }


//                writer.passCurrentRow();
//                writer.write(newDataList, false);
                writer.flush();
                writer.close();
            }
        }
    }

    private Integer getZdmd(String origin,String szzc, int fhxj) throws SQLException {
        if (null == szzc || "".equals(szzc)) {
            return null;
        }
        szzc = szzc.replaceAll("人", "");
        szzc = szzc.replaceAll("天", "");
        String[] szzcArray = null;
        if (szzc.length() > 3) {
            szzcArray = szzc.split("(?=\\d{1,100}[\\u4e00-\\u9fa5])");
        } else {
            szzcArray = new String[]{szzc};
        }
        Map<String, Integer> map = new HashMap<String, Integer>();
        System.out.println("经营密度:"+szzc);
        for (String tree : szzcArray) {
            String treeName = tree.replaceAll("\\d", "");
            String treeNum = tree.replaceAll("[\\u4e00-\\u9fa5]", "");
//            map.put(treeName, Integer.valueOf(treeNum));
            String treeDensityName = treeDensityGroupMap.get(treeName);
            if(null==treeDensityName){
                continue;
            }
            Integer total=Integer.parseInt(treeNum);
            if(map.containsKey(treeDensityName)){
                total+=map.get(treeDensityName);
            }
            map.put(treeDensityName,total);
        }
        if(map.isEmpty()){
            return null;
        }
        List<Map.Entry<String, Integer>> list = new ArrayList<Map.Entry<String, Integer>>(map.entrySet());
        Collections.sort(list, new Comparator<Map.Entry<String, Integer>>() {
            public int compare(Map.Entry<String, Integer> o1, Map.Entry<String, Integer> o2) {
                return o2.getValue().compareTo(o1.getValue());
            }
        });
//        System.out.println(list);
//        System.out.println(list.get(0).getKey());
        String treeName = null;
        if (list.size() > 1) {
            Integer val1 = list.get(0).getValue();
            Integer val2 = list.get(1).getValue();
            String groupName = null;
            if (val1.equals(val2)) {
                return null;
            } else {
                treeName = list.get(0).getKey();
            }
        } else {
            treeName = list.get(0).getKey();
        }


        JSONObject data = getJymd(origin, treeName, fhxj);
        return data != null ? data.getInteger("min") : null;
    }

    private String getOrderByGroup(String origin, List<Map.Entry<String, Integer>> list) {
        Map<String, Integer> treeGroupMap = new HashMap<String, Integer>();
        //先分针和阔比对占比 当出现相同时 按针叶优先
        for (Map.Entry<String, Integer> entry : list) {
            System.out.println("起源:"+origin+"树种:"+entry.getKey());
            if("天然".equals(origin)&&"樟".equals(entry.getKey())){
                System.out.println("跳过 樟子松，起源:"+origin+"树种:"+entry.getKey());
                continue;
            }
            String treeGroup = getGroup(origin, entry.getKey());
            Integer treeNum = entry.getValue();
            String keyName="针";
            if(!treeGroup.contains("针")){
                keyName="阔";
            }
            if (treeGroupMap.containsKey(keyName)) {
                treeNum += treeGroupMap.get(keyName);
            }
            treeGroupMap.put(keyName, treeNum);
        }
        if(treeGroupMap.isEmpty()){

            return  getGroup(origin, list.get(0).getKey());
        }
        Integer totalZ= treeGroupMap.get("针")!=null? treeGroupMap.get("针"):0;
        Integer totalK= treeGroupMap.get("阔")!=null?treeGroupMap.get("阔"):0;
        String key="阔";
        if(totalZ>=totalK){
            key="针";
        }
        treeGroupMap=new LinkedHashMap<String, Integer>();
        //先对树种组 分组计算占比
        for (Map.Entry<String, Integer> entry : list) {
            if("天然".equals(origin)&&"樟".equals(entry.getKey())){
                continue;
            }
            String treeGroup = getGroup(origin, entry.getKey());
            if(!treeGroup.contains(key)){
                continue;
            }
            Integer treeNum = entry.getValue();
            if (treeGroupMap.containsKey(treeGroup)) {
                treeNum += treeGroupMap.get(treeGroup);
            }
            treeGroupMap.put(treeGroup, treeNum);
        }
        List<Map.Entry<String, Integer>> list2 = new ArrayList<Map.Entry<String, Integer>>(treeGroupMap.entrySet());



        Collections.sort(list2, new Comparator<Map.Entry<String, Integer>>() {
            public int compare(Map.Entry<String, Integer> o1, Map.Entry<String, Integer> o2) {
                return o2.getValue().compareTo(o1.getValue());
            }
        });
        //再取占比大的树种组 如果存在相同大小树种组
        if (list2.size() > 1) {
            Integer val1 = list2.get(0).getValue();
            Integer val2 = list2.get(1).getValue();
            // 还需要进一步 判断 树种组的排序顺序 再获取到最终的树种组
            if (val1.equals(val2)) {
                treeGroupMap = new HashMap<String, Integer>();
                for (Map.Entry<String, Integer> entry : list2) {
                    String groupName = entry.getKey();
                    Integer orderBy = treeGroupOrderByMap.get(groupName);
                    treeGroupMap.put(groupName, orderBy);
                }
                List<Map.Entry<String, Integer>> list3 = new ArrayList<Map.Entry<String, Integer>>(treeGroupMap.entrySet());
               System.out.println(list3);
                Collections.sort(list3, new Comparator<Map.Entry<String, Integer>>() {
                    public int compare(Map.Entry<String, Integer> o1, Map.Entry<String, Integer> o2) {
                        return o1.getValue()-o2.getValue();
                    }
                });
                return list3.get(0).getKey();
            }
        }
        return list2.get(0).getKey();
    }

    private boolean validateSgAndXj(int sg, int xj) {
        if (sg > 20) {
            return 20 < xj && xj < 24;
        } else {
            int diff = sg - xj;
            return -2 <= diff && diff <= 2;
        }
    }

    private boolean validateXjqd(String sllb, String cflx, String lz, int xjqd, String origin) {
        if (cflx.equals("抚育采伐")) {
            if ("商品林".equals(sllb)) {
                if (lz.equals("幼龄林")) {
                    if (origin.startsWith("人工")) {
                        return xjqd <= 30;
                    } else {
                        return xjqd <= 20;
                    }
                } else if (lz.equals("中龄林")) {
                    if (origin.startsWith("人工")) {
                        return xjqd <= 20;
                    } else {
                        return xjqd <= 30;
                    }
                } else if (lz.equals("近熟林")) {
                    if (origin.startsWith("人工")) {
                        return xjqd <= 20;
                    } else {
                        return xjqd <= 30;
                    }
                }
            } else if ("国家级公益林".equals(sllb)) {
                return xjqd <= 15;
            } else if ("县局级公益林".equals(sllb)) {
                return xjqd <= 20;
            }
        }
        return false;
    }

    private boolean validateYbd(String sllb, String cflx, String cffs, String lz, double fqybd, double fhybd) {
        if (fhybd < 0.6) {
            return false;
        }
        double diff = fqybd - fhybd;
        String diffStr = String.format("%.2f", diff);
        diff = Double.parseDouble(diffStr);
        if (diff != 0.1) {
            return false;
        }
        if ("透光伐".equals(cffs) && "幼龄林".equals(lz)) {
            return true;
        } else if ("生长伐".equals(cffs) && "商品林".equals(sllb)) {
            if ("中龄林".equals(lz) || "近熟林".equals(lz)) {
                return true;
            }
        } else if ("生态疏伐".equals(cffs)) {
            if ("国家级公益林".equals(sllb) || "县局级公益林".equals(sllb)) {
                if ("中龄林".equals(lz) || "近熟林".equals(lz)) {
                    return true;
                }
            }
        }
        return false;
    }

    private JSONObject getJymd(String origin, String treeName, int fhxj) throws SQLException {
        String sql = "select * from tree_density where 树种 like '%" + treeName + "%' and 径阶=" + fhxj;
        if ("人工".equals(origin) && "杨".equals(treeName)) {
            sql += " and 树种 like '%人%'";
        }
        ResultSet resultSet = SqliteUtils.executeQuery(connection, sql);
        List<JSONObject> list = new ArrayList<JSONObject>();
        while (resultSet.next()) {
            String tree = resultSet.getString("树种");
            String diameterClass = resultSet.getString("径阶");
            String min = resultSet.getString("最低密度");
            String suitable = resultSet.getString("适宜密度");
            String max = resultSet.getString("最高密度");
            JSONObject data = new JSONObject();
            data.put("treeName", tree);
            data.put("diameterClass", diameterClass);
            data.put("min", min);
            data.put("suitable", suitable);
            data.put("max", max);
            list.add(data);
        }
        resultSet.close();

        return list.size() > 0 ? list.get(0) : null;
    }

    private List<String> getTrArrays(String origin, String[] array) {
        List<String> groupz = new ArrayList<String>();
        List<String> groupk = new ArrayList<String>();
        int countz = 0;
        int countk = 0;
        for (String tree : array) {
            String treeName = tree.replaceAll("\\d", "");
            String treeNum = tree.replaceAll("[\\u4e00-\\u9fa5]", "");
            String group = getGroup(origin, treeName);
            if (group != null && group.contains("针")) {
                countz += Integer.valueOf(treeNum);
                groupz.add(tree);
            } else {
                countk += Integer.valueOf(treeNum);
                groupk.add(tree);
            }
        }
        //判断占比
        if (countz < countk) {
            return groupk;
        }
        return groupz;
    }

    private List<String> getRgArrays(String origin, String[] array) {
        List<String> treeList = new ArrayList<String>();
        for (String tree : array) {
            String treeName = tree.replaceAll("\\d", "");
            String treeNum = tree.replaceAll("[\\u4e00-\\u9fa5]", "");
            if (Integer.valueOf(treeNum) >= 5) {
                treeList.add(treeName);
            }
        }

        return treeList;
    }


    private String getGroup(String originName, String tree) {
        if ("人工".equals(originName)) {
            return rgMap.get(tree);
        } else {
            return trMap.get(tree);
        }
    }

    private void validateTr(String origin, int fqll, String lz, String[] originArray, String[] array, Map<String, Object> dataMap, List<Map<String, Object>> newDataList) throws SQLException {
        String newszzc = "";

        boolean flag = true;
        StringBuffer removeTree = new StringBuffer();
        Map<String, JSONObject> groupMap = new LinkedHashMap<String, JSONObject>();
        //1.根据针阔分组，获取需要做比对的树种组
        Map<String, Integer> map = new HashMap<String, Integer>();
        Map<String, List<String>> groupNameMap = new HashMap<String, List<String>>();
        for (String tree : array) {
            String treeName = tree.replaceAll("\\d", "");
            String treeNum = tree.replaceAll("[\\u4e00-\\u9fa5]", "");
            map.put(treeName, Integer.valueOf(treeNum));
            String groupName = getGroup(origin, treeName);
//            if("樟".equals(treeName)){
//                continue;
//            }
            List<String> treeList = new ArrayList<String>();
            if(groupNameMap.containsKey(groupName)){
                treeList=groupNameMap.get(groupName);
            }
            treeList.add(treeName);
            groupNameMap.put(groupName,treeList);
        }
        List<Map.Entry<String, Integer>> list = new ArrayList<Map.Entry<String, Integer>>(map.entrySet());
        String groupName = getOrderByGroup(origin,list);
        //2.检查获取的树种组中，包含的林龄是否正确
        List<String> treeList =  groupNameMap.get(groupName);
        for(String treeName : treeList){
            boolean result = check(treeName, groupName, origin, fqll, lz);
            if(!result){
                flag=false;
                break;
            }
        }
       String originName="天";
        String checkResult = "正确";
        if (flag) {
            for (String t : originArray) {
                String tree = t.replaceAll("\\d", "");
                String treeNum = t.replaceAll("[\\u4e00-\\u9fa5]", "");
                if("樟".equals(tree)){
                    newszzc += treeNum + "人" + tree;
                    continue;
                }
                newszzc += treeNum + originName + tree;
            }
        } else {
            checkResult = "错误";
        }

        System.out.println("执行结果:" + newszzc);
        Map<String, Object> resultMap = new LinkedHashMap<String, Object>(dataMap);
        resultMap.put("执行结果", checkResult);
        resultMap.put("处理树种组成", newszzc);
        newDataList.add(resultMap);
    }

    private static boolean isGroupOrderByValidate(Map<String, JSONObject> groupMap) {
        boolean tag = true;
        if(null==groupMap||groupMap.isEmpty()){
            tag=false;
        }
        for (Map.Entry<String, JSONObject> entry : groupMap.entrySet()) {

            String group = entry.getKey();
            JSONObject data = entry.getValue();
            Integer total = data.getInteger("total");
            String realOrigin = data.getString("realOrigin");

            int orderBy = 0;
            if ("慢针".equals(group)) {
                for (Map.Entry<String, JSONObject> entry2 : groupMap.entrySet()) {
                    String group2 = entry.getKey();
                    if (("慢针".equals(group2))) {
                        continue;
                    }
                    JSONObject data2 = groupMap.get(group2);
                    Integer total2 = data2.getInteger("total");
                    if (total < total2) {
                        tag = false;
                        break;
                    }
                }
            } else if ("中针".equals(group)) {
                //慢针≥中针≥慢阔≥中阔≥速阔
                for (Map.Entry<String, JSONObject> entry2 : groupMap.entrySet()) {
                    String group2 = entry.getKey();
                    if (("中针".equals(group2))) {
                        continue;
                    }
                    JSONObject data2 = groupMap.get(group2);
                    Integer total2 = data2.getInteger("total");
                    //慢针≥中针
                    if (("慢针".equals(group2))) {
                        if (total > total2) {
                            tag = false;
                            break;
                        }
                    } else {
                        if (total < total2) {
                            tag = false;
                            break;
                        }
                    }
                }
            } else if ("慢阔".equals(group)) {
                for (Map.Entry<String, JSONObject> entry2 : groupMap.entrySet()) {
                    String group2 = entry.getKey();
                    if (("慢阔".equals(group2))) {
                        continue;
                    }
                    JSONObject data2 = groupMap.get(group2);
                    Integer total2 = data2.getInteger("total");
                    //慢阔>=慢针 或者 慢阔>=中针
                    if (("慢针".equals(group2)) || ("中针".equals(group2))) {
                        if (total > total2) {
                            tag = false;
                            break;
                        }
                    } else {
                        if (total < total2) {
                            tag = false;
                            break;
                        }
                    }
                }
            } else if ("中阔".equals(group)) {
                for (Map.Entry<String, JSONObject> entry2 : groupMap.entrySet()) {
                    String group2 = entry.getKey();
                    if (("中阔".equals(group2))) {
                        continue;
                    }
                    JSONObject data2 = groupMap.get(group2);
                    Integer total2 = data2.getInteger("total");
                    //中阔>=慢针 或者 中阔>=中针或者 中阔>=慢阔
                    if (("慢针".equals(group2)) || ("中针".equals(group2)) || ("慢阔".equals(group2))) {
                        if (total > total2) {
                            tag = false;
                            break;
                        }
                    } else {
                        if (total < total2) {
                            tag = false;
                            break;
                        }
                    }
                }
            } else if ("速阔".equals(group)) {
                for (Map.Entry<String, JSONObject> entry2 : groupMap.entrySet()) {
                    String group2 = entry.getKey();
                    if (("速阔".equals(group2))) {
                        continue;
                    }
                    JSONObject data2 = groupMap.get(group2);
                    Integer total2 = data2.getInteger("total");
                    //速阔应该是最小的
                    if (total > total2) {
                        tag = false;
                        break;
                    }
                }
            }
        }
        return tag;
    }

    private static String getSortGroup(List<String> groupNames) {
        Map<String,Integer> treeGroupMap = new HashMap<String, Integer>();
        for (String groupName : groupNames) {
            Integer orderBy = treeGroupOrderByMap.get(groupName);
            treeGroupMap.put(groupName, orderBy);
        }
        List<Map.Entry<String, Integer>> list = new ArrayList<Map.Entry<String, Integer>>(treeGroupMap.entrySet());
        System.out.println(list);
        Collections.sort(list, new Comparator<Map.Entry<String, Integer>>() {
            public int compare(Map.Entry<String, Integer> o1, Map.Entry<String, Integer> o2) {
                return o1.getValue()-o2.getValue();
            }

        });
        return list.get(0).getKey();
    }


    private void validateRg(String origin, int fqll, String lz, String[] originArray, String[] array, Map<String, Object> dataMap, List<Map<String, Object>> newDataList) throws SQLException {
        String newszzc = "";
        boolean flag = true;
        StringBuffer removeTree = new StringBuffer();
        Map<String, JSONObject> groupMap = new LinkedHashMap<String, JSONObject>();
        //2.查看单一树种树龄是否符号
        Map<String,List<String>> groupNameMap=new LinkedHashMap<String, List<String>>();
        for (String t : array) {
            String tree = t.replaceAll("\\d", "");
            String treeNum = t.replaceAll("[\\u4e00-\\u9fa5]", "");
            //1.按单个树种查询，使用所属起源直接查询
            String group = getGroup(origin, tree);
            List<String> treeList = new ArrayList<String>();
            if(groupNameMap.containsKey(group)){
                treeList=  groupNameMap.get(group);
            }
            treeList.add(tree);
            groupNameMap.put(group,treeList);
//            boolean result = check(tree, group, origin, fqll, lz);
//            String realOrigin = "";
//            //2.如果树种不符合林龄要求的，去掉
//            if (!result) {
//                flag = false;
//                break;
//            }
        }
        String getSortGroupName=getSortGroup(new ArrayList<String>(groupNameMap.keySet()));
        //获取到要比对的 树种组，然后校验树种组中所含树种的林龄是否正确
        List<String> treeList= groupNameMap.get(getSortGroupName);
        for(String tree:treeList){
            boolean result = check(tree, getSortGroupName, origin, fqll, lz);
            if (!result) {
                flag = false;
                break;
            }
        }
        //4.拿到按树种组分后的map后开始比对
        String originName = "人";
//        boolean tag = isGroupOrderByValidate(groupMap);
        String checkResult = "正确";
        if (flag) {
            for (String t : originArray) {
                String tree = t.replaceAll("\\d", "");
                String treeNum = t.replaceAll("[\\u4e00-\\u9fa5]", "");
                newszzc += treeNum + originName + tree;
            }
        } else {
            checkResult = "错误";
        }
        System.out.println("执行结果:" + newszzc);
        Map<String, Object> map = new LinkedHashMap<String, Object>(dataMap);
        map.put("执行结果", checkResult);
        map.put("处理树种组成", newszzc);
        newDataList.add(map);
    }


    private void validate3(String origin, int fqll, String lz, String[] array, Map<String, Object> dataMap, List<Map<String, Object>> newDataList) throws SQLException {
        String newszzc = "";
        boolean flag = true;
        StringBuffer removeTree = new StringBuffer();
        Map<String, JSONObject> groupMap = new LinkedHashMap<String, JSONObject>();
        for (String t : array) {

            String tree = t.replaceAll("\\d", "");
            String treeNum = t.replaceAll("[\\u4e00-\\u9fa5]", "");
            //1.按单个树种查询，使用所属起源直接查询
            String group = getGroup(origin, tree);
            //如果是天然需要过滤占比小的
            if ("天然".equals(origin)) {

            }
            boolean result = check(tree, group, origin, fqll, lz);
            String realOrigin = "";
            //2.如果树种不符合林龄要求的，去掉
            if (result) {
                realOrigin = origin.substring(0, 1);
            } else {
                removeTree.append(t);
                continue;
            }
            //3.符合林龄要求的，树种按树种组相加 是否大于等于5
            JSONObject data = new JSONObject();
            if (groupMap.containsKey(group)) {
                data = groupMap.get(group);
            }
            Integer total = data.getIntValue("total");
            total += Integer.valueOf(treeNum);
            data.put("total", total);
            data.put("group", group);
            data.put("tree", tree);
            data.put("realOrigin", realOrigin);
            groupMap.put(group, data);
        }
        //4.拿到按树种组分后的map后开始比对
        Integer preTotal = 0;
        Integer preOrderBy = 10;
        String originName = "";
        for (Map.Entry<String, JSONObject> entry : groupMap.entrySet()) {
            //先看数字是否大于等于5
            String group = entry.getKey();
            JSONObject data = entry.getValue();
            Integer total = data.getInteger("total");
            String realOrigin = data.getString("realOrigin");
            //如果大于等于5 还需要看优先级
            if (total >= preTotal) {
                int orderBy = 0;
                if ("慢针".equals(group)) {
                    orderBy = 1;
                } else if ("中针".equals(group)) {
                    orderBy = 2;
                } else if ("慢阔".equals(group)) {
                    orderBy = 3;
                } else if ("中阔".equals(group)) {
                    orderBy = 4;
                } else if ("速阔".equals(group)) {
                    orderBy = 5;
                }
                //判断优先级
                if (preOrderBy > orderBy) {
                    preOrderBy = orderBy;
//                    preTotal=total;
                    originName = realOrigin;
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
        } else {
            newszzc = "错误";
        }
        System.out.println("执行结果:" + newszzc);
        Map<String, Object> map = new LinkedHashMap<String, Object>(dataMap);
        map.put("执行结果", newszzc);
        newDataList.add(map);
    }

    /**
     * 校验天然树种
     */


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
                ExcelWriter writer = ExcelUtil.getWriter("d:\\test\\tree\\output\\测试.xlsx");
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

    public void getTreeMap() throws SQLException {
        String sql = "select * from origin";

        ResultSet resultSet = SqliteUtils.executeQuery(connection, sql);

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
        resultSet.close();
    }

    private int countTreeGroup(JSONObject tree) throws SQLException {
        String sql = "SELECT * from tree_group where " +
                "   树种组 like '%" + tree.getString("树种组") + "' and 起源='" + tree.getString("起源") + "'" +
//                "  and 起始林龄>=" + tree.getInteger("林龄")+
                "  and 龄组='" + tree.getString("龄组") + "'";
//                if(!"过熟林".equals(tree.getString("龄组"))){
//                    sql+= " and 截止林龄<=" + tree.getInteger("林龄") ;
//                }


        if (null != tree.getString("树种")) {
            sql += " and 树种 like '" + tree.getString("树种") + "%' ";
        }


        System.out.println(sql);
        ResultSet resultSet = SqliteUtils.executeQuery(connection, sql);
//        int count = resultSet.getInt(1);
        int start= resultSet.getInt("起始林龄");
        int end=resultSet.getInt("截止林龄");
        resultSet.close();
        int ll= tree.getInteger("林龄");

        if(start<=ll&&ll<=end){
            return 1;
        }
        return 0;
    }

    public void queryTreeGroup() throws SQLException {
        String sql="SELECT 树种组,order_by from tree_group  GROUP BY 树种组 ORDER BY order_by";
        ResultSet resultSet = SqliteUtils.executeQuery(connection, sql);
        while (resultSet.next()) {
            String group = resultSet.getString("树种组");
            Integer orderBy = resultSet.getInt("order_by");
            treeGroupOrderByMap.put(group, orderBy);
        }
        resultSet.close();
    }

    public void queryTreeDensityGroup() throws SQLException {
        String sql="SELECT 树种,分组名 from tree_density_group ";
        ResultSet resultSet = SqliteUtils.executeQuery(connection, sql);
        while (resultSet.next()) {
            String treeName = resultSet.getString("树种");
            String groupName = resultSet.getString("分组名");
            treeDensityGroupMap.put(treeName, groupName);
        }
        resultSet.close();
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
//        jdbcUrl = "jdbc:sqlite:src/main/resources/tree.db";
        String excelPath = "D:\\test\\tree\\excel";
        String outPath="D:\\zbj\\tree\\测试.xlsx";
//        String excelPath = "D:\\zbj\\tree\\excel";
        AnalysisService service = new AnalysisService();
        service.getTreeMap();
        service.queryTreeGroup();
        service.queryTreeDensityGroup();
        service.process2(excelPath,outPath);
    }
}

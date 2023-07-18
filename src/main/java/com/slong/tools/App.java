package com.slong.tools;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.date.DateUnit;
import cn.hutool.core.date.DateUtil;
import cn.hutool.crypto.SmUtil;
import cn.hutool.crypto.symmetric.SymmetricCrypto;
import com.slong.auth.service.AuthService;
import com.slong.tools.service.AnalysisService;
import com.slong.tools.utils.SqliteUtils;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.Date;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void main(String[] args) throws Exception {
        String dbFile = AnalysisService.class.getClassLoader().getResource("tree.db").getFile();

        String excelPath = "D:\\zbj\\tree\\excel";
        String outPath="D:\\zbj\\tree\\测试.xlsx";

        if(args.length>1){
            excelPath=args[0];
            outPath=args[1];
            AnalysisService.jdbcUrl ="jdbc:sqlite:"+args[2];
        }
        if(!AuthService.validateAppKey()){
            throw new RuntimeException("程序已过期，请联系管理员！");
        }
        String sql="select total from auth ";
        ResultSet resultSet= SqliteUtils.executeQuery(AnalysisService.jdbcUrl,sql);
       int total=  resultSet.getInt("total");
        resultSet.close();
       if(total!=-1){
           if(total == 0){
               throw new RuntimeException("程序已超过使用次数，请联系管理员！");
           }
       }


//        String excelPath = "D:\\zbj\\tree\\excel";
        AnalysisService service = new AnalysisService();
        service.connection=SqliteUtils.getConnection(AnalysisService.jdbcUrl);
        service.getTreeMap();
        service.queryTreeGroup();
        service.queryTreeDensityGroup();
        service.process2(excelPath,outPath);
        total=total-1;
        if(total!=0){
            sql="update auth set total="+total;
        }

        SqliteUtils.executeUpdate(service.connection,sql);

    }


}

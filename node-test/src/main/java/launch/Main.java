/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package launch;
import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;
import org.cdlib.mrt.cloud.main.RunRestore;
import org.cdlib.mrt.s3.tools.CloudNodeList;
import org.cdlib.mrt.utility.LoggerInf;
import org.cdlib.mrt.utility.StringUtil;
import org.cdlib.mrt.utility.TException;
import org.cdlib.mrt.utility.TFileLogger;
import org.cdlib.mrt.utility.TFrame;
public class Main {
    public static void main(String args[])
        throws TException
    {
        LoggerInf logger = new TFileLogger("tcloud", 0, 50);
        String testDirS = ".";
        if (args.length == 0) {
            throw new TException.INVALID_OR_MISSING_PARM("name test not provided");
        }
        String testName = args[0];
        
        CloudNodeList cloudNodeList = CloudNodeList.getCloudNodeList(
            testDirS, 
            testName,
            logger);
        try {
            if (true) cloudNodeList.run();

        } catch(Exception e) {
                e.printStackTrace();
                System.out.println(
                    "Main: Encountered exception:" + e);
                System.out.println(
                        StringUtil.stackTrace(e));
        }
    
    }
}
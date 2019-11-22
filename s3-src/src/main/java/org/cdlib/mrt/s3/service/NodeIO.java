/*
Copyright (c) 2005-2010, Regents of the University of California
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:
 *
- Redistributions of source code must retain the above copyright notice,
  this list of conditions and the following disclaimer.
- Redistributions in binary form must reproduce the above copyright
  notice, this list of conditions and the following disclaimer in the
  documentation and/or other materials provided with the distribution.
- Neither the name of the University of California nor the names of its
  contributors may be used to endorse or promote products derived from
  this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
OF THE POSSIBILITY OF SUCH DAMAGE.
*********************************************************************/

package org.cdlib.mrt.s3.service;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Collection;
import java.util.Map;
import java.util.HashMap;
import java.util.Properties;
import org.cdlib.mrt.s3.aws.AWSS3Cloud;
import org.cdlib.mrt.s3.cloudhost.CloudhostAPI;
import org.cdlib.mrt.s3.openstack.OpenstackCloud;
import org.cdlib.mrt.s3.pairtree.PairtreeCloud;
import org.cdlib.mrt.s3.service.CloudStoreInf;
import org.cdlib.mrt.s3.store.StoreCloud;
import org.cdlib.mrt.utility.DeleteOnCloseFileInputStream;
import org.cdlib.mrt.utility.FileUtil;
import org.cdlib.mrt.utility.LoggerInf;
import org.cdlib.mrt.utility.StringUtil;
import org.cdlib.mrt.utility.TException;
import org.cdlib.mrt.utility.TFileLogger;

/**
 *
 * @author DLoy
 * 
<pre>
The NodeIO class handles a paramertized definition for all cloud IO members.
A NodeIO name is used to specifically identify which node list to use.

Each node.<seq> line in the list defines a node and cloud definitions for that node

This process is now used on all backend Merritt services.

********************************************************************
Example set:

./nodes-prod.properties
node.1=9001|sdsc|distrib.prod.9001.__
node.2=9103|sdsc|prod-9103
node.3=8001|ucla-prod
node.4=5001|aws-std|uc3-s3mrt5001-prd
node.5=6001|aws-near|uc3-s3mrt6001-prd
node.6=7001|aws-std|uc3-s3mrt1001-prd
node.7=7021|ch-riverside-prd|8100

* ch-riverside-prd
serviceType=cloudhost
base=https://uc3-mrtdat1-dev.cdlib.org:30443/cloudhost

./sdsc.properties
serviceType=swift
access_key=our_service:our_group
secret_key=pwd
host=cloud.sdsc.edu

./ucla-prod
serviceType=pairtree
base=/mrt-ucla-prod/dpr2/repository/node8001/store/fileCloud

./aws-std
serviceType=aws
accessMode=on-line
storageClass=Standard

./aws-near
serviceType=aws
accessMode=near-line

********************************************************************

Process module:
Repository: https://github.com/CDLUC3/mrt-cloud
Project: s3-src
Class: org.cdlib.mrt.s3.service.NodeIO

********************************************************************
SERVICE TYPES:

Swift
*node:
node.<seq>=<storage node>|<properties lookup name>|<swift container>

*Properties:
serviceType=swift
access_key=<Organization key:group key>
secret_key=<password>
host=<server>

*Example
node.2=9103|sdsc|prod-9103
...

./sdsc.properties
serviceType=swift
access_key=our_service:our_group
secret_key=pwd
host=cloud.sdsc.edu

-------------------------------------------------------------------
Pairtree
*node:
node.<seq>=<storage node>|<properties lookup name>

*Properties:
serviceType=pairtree
base=<base directory for tree and data>

*Example:
node.2=8100|csh-pair
...

./csh-pair.properties
serviceType=pairtree
base=./fileCloud

-------------------------------------------------------------------
AWS
*node:
node.<seq>=<storage node>|<properties lookup name>|<AWS bucket>


*Properties:
serviceType=aws
accessMode=
  "on-line" = directly accessible content
  "near-line" = Glacier
storageClass= optional AWS storage class
  "Standard" 
  "ReducedRedundancy"

*Example:
node.4=5001|aws-std|uc3-s3mrt5001-prd
...

./aws-std.properties
serviceType=aws
accessMode=on-line
storageClass=Standard

-------------------------------------------------------------------
Cloudhost 
*node:
node.<seq>=<storage node>|<properties lookup name>|<container number>

*Properties:
serviceType=cloudhost
base=<base url for remote cloudhost>

Example:
node.1=7301|ch-unm-test|8101
...

./ch-unm-test.properties
serviceType=cloudhost
base=http://oneshare-test.unm.edu:8080/cloudhost


********************************************************************
Example set:

./nodes-prod.properties
node.1=9001|sdsc|distrib.prod.9001.__
node.2=9103|sdsc|prod-9103
node.3=8001|ucla-prod
node.4=5001|aws-std|uc3-s3mrt5001-prd
node.5=6001|aws-near|uc3-s3mrt6001-prd
node.6=7001|aws-std|uc3-s3mrt1001-prd
node.7=7021|ch-riverside-prd|8100

* ch-riverside-prd
serviceType=cloudhost
base=https://uc3-mrtdat1-dev.cdlib.org:30443/cloudhost

./sdsc.properties
serviceType=swift
access_key=our_service:our_group
secret_key=pwd
host=cloud.sdsc.edu

./ucla-prod
serviceType=pairtree
base=/mrt-ucla-prod/dpr2/repository/node8001/store/fileCloud

./aws-std
serviceType=aws
accessMode=on-line
storageClass=Standard

./aws-near
serviceType=aws
accessMode=near-line

* 
* Example loading:
* 
            nodeIO = NodeIO.getNodeIO(nodeName, logger);
            NodeIO.AccessNode accessNode = nodeIO.getAccessNode(nodeNumber);
            if (accessNode == null) {
               throw new TException.REQUESTED_ITEM_NOT_FOUND("test fails:"
                  + " - nodeName:" + nodeName
                  + " - nodeNumber:" + nodeNumber
               );
            }
            CloudStoreInf service = accessNode.service;
            String container = accessNode.container;
            StateHandler stateHandler = StateHandler.getStateHandler(service, container, KEY, logger);
</pre>
 */
public class NodeIO 
{
    
    protected static final String NAME = "NodeIO";
    protected static final String MESSAGE = NAME + ": ";
    private static boolean DEBUG = false;
    private static boolean DEBUG_ACCESS = false;
    
    protected HashMap<Long,AccessNode> map = new HashMap<>();
    protected String nodeName = null;
    protected LoggerInf logger = null;
    
    
    public static void main(String[] args) throws Exception {

        LoggerInf logger = new TFileLogger("lockFile", 10, 10);
        NodeIO nodeIO = new NodeIO("nodes-dev", logger);
        nodeIO.printNodes("main dump");
        
        String urlS = "http://store-dev.cdlib.org:35121"
                + "/content/910/ark%3A%2F99999%2Ffk4db8k7z/1/producer%2Fsource.mets.xml?fixity=no";
        //URL url = new URL( "http://store-aws-dev.cdlib.org:35121/content/9001/ark%3A%2F99999%2Ff" 
        //        + "k4ww7m184/1/producer%2Fmrt-datacite.xml");
        //String urlS = "http://store-aws-dev.cdlib.org:35121/content/9001/ark%3A%2F99999%2Ff" 
        //        + "k4ww7m184/1/producer%2Fmrt-datacite.xml";
        AccessKey accessKey = nodeIO.getAccessKey(urlS);
        System.out.println(accessKey.dump("main"));
        if (false) return;
        File tempFile = FileUtil.getTempFile("temp", ".txt");
        try {
            nodeIO.getFile(urlS,tempFile);
            String out = FileUtil.file2String(tempFile);
            System.out.println("OUT:\n" + out);
        } catch (Exception ex) {
            
        } finally {
            tempFile.delete();
        }
    }
    
    
    public static void testmain(String[] args) throws Exception {

        URL aURL = new URL("http://example.com:80/docs/books%30%40/tutorial"
                           + "/index.html?name=networking#DOWNLOADING");

        System.out.println("protocol = " + aURL.getProtocol());
        System.out.println("authority = " + aURL.getAuthority());
        System.out.println("host = " + aURL.getHost());
        System.out.println("port = " + aURL.getPort());
        System.out.println("path = " + aURL.getPath());
        System.out.println("query = " + aURL.getQuery());
        System.out.println("filename = " + aURL.getFile());
        System.out.println("ref = " + aURL.getRef());
        System.out.println("pathdecode = " + URLDecoder.decode(aURL.getPath(), "utf-8"));
    }

    public static AccessNode getCloudNode(String nodeName, long node, LoggerInf logger) 
        throws TException
    {
        NodeIO nodeIO = new NodeIO(nodeName, node,  logger);
        AccessNode cloudNode = nodeIO.getNode(node);
        return cloudNode;
    }

    public static NodeIO getNodeIO(String nodeName, LoggerInf logger) 
        throws TException
    {
        NodeIO nodeIO = new NodeIO(nodeName, logger);
        return nodeIO;
    }
    
    public NodeIO(String nodeName,LoggerInf logger) 
        throws TException
    {
        try {
            if (logger == null) {
                throw new TException.INVALID_OR_MISSING_PARM(MESSAGE + "log missing");
            }
            this.logger = logger;
            this.nodeName= nodeName;
            Properties cloudProp = getNodeProp(nodeName);
            if (cloudProp == null) {
                throw new TException.INVALID_DATA_FORMAT("Unable to locate:" +  nodeName);
            }
            addMap(cloudProp);
            
        } catch (TException tex) {
            throw tex;
            
        } catch (Exception ex) {
            System.out.println(MESSAGE + "Exception:" + ex);
            ex.printStackTrace();
            throw new TException(ex);
        }
    }
    
    public NodeIO(String nodeName, long node, LoggerInf logger) 
        throws TException
    {
        try {
            if (logger == null) {
                throw new TException.INVALID_OR_MISSING_PARM(MESSAGE + "log missing");
            }
            this.logger = logger;
            Properties cloudProp = getNodeProp(nodeName);
            if (cloudProp == null) {
                throw new TException.INVALID_DATA_FORMAT("Unable to locate:" +  nodeName);
            }
            addMap(cloudProp, node);
            
        } catch (TException tex) {
            throw tex;
            
        } catch (Exception ex) {
            System.out.println(MESSAGE + "Exception:" + ex);
            ex.printStackTrace();
            throw new TException(ex);
        }
    }
    
    public NodeIO(Properties cloudProp, LoggerInf logger) 
        throws TException
    {
        try {
            if (logger == null) {
                throw new TException.INVALID_OR_MISSING_PARM(MESSAGE + "log missing");
            }
            this.logger = logger;
            addMap(cloudProp);
            
        } catch (TException tex) {
            throw tex;
            
        } catch (Exception ex) {
            System.out.println(MESSAGE + "Exception:" + ex);
            ex.printStackTrace();
            throw new TException(ex);
        }
    }
    
    private Properties getNodeProp(String propName)
        throws TException
    {
        try {
            AccessNode test = new AccessNode();
            InputStream propStream =  test.getClass().getClassLoader().
                    getResourceAsStream("nodes/" + propName + ".properties");
            if (propStream == null) {
                System.out.println("Unable to find resource:" + propName);
                return null;
            }
            Properties cloudProp = new Properties();
            cloudProp.load(propStream);
            return cloudProp;
            
        } catch (Exception ex) {
            System.out.println(MESSAGE + "Exception:" + ex);
            ex.printStackTrace();
            throw new TException(ex);
        }
        
    }
    
    private void addMap(Properties cloudProp)
        throws TException
    {
        try {
            for(int i=1; true; i++) {
                String line = cloudProp.getProperty("node." + i);
                if (line == null) break;
                addMapEntry(line);
            }
            
        } catch (TException tex) {
            throw tex;
            
        } catch (Exception ex) {
            System.out.println(MESSAGE + "Exception:" + ex);
            ex.printStackTrace();
            throw new TException(ex);
        }
        
    }
    
    private void addMap(Properties cloudProp, long node)
        throws TException
    {
        try {
            for(int i=1; true; i++) {
                String line = cloudProp.getProperty("node." + i);
                if (line == null) break;
                long inNode = getNode(line);
                if (node == inNode) {
                    addMapEntry(line);
                    break;
                }
            }
            
        } catch (TException tex) {
            throw tex;
            
        } catch (Exception ex) {
            System.out.println(MESSAGE + "Exception:" + ex);
            ex.printStackTrace();
            throw new TException(ex);
        }
        
    }
    
    public AccessNode getNode(long node) 
        throws TException
    {
        return map.get(node);
    }
    
    protected long getNode(String line) 
        throws TException
    {
        try {
            if (DEBUG) System.out.println("Add:" + line);
            String[] parts = line.split("\\s*\\|\\s*");
            if ((parts.length < 2) || (parts.length > 3)) {
                throw new TException.INVALID_OR_MISSING_PARM("addMapENtry requires 2 or 3 parts:" + line);
            }
            Long nodeNumber = Long.parseLong(parts[0]);
            return nodeNumber;
            
            
        } catch (TException tex) {
            throw tex;
            
        } catch (Exception ex) {
            System.out.println(MESSAGE + "Exception:" + ex);
            ex.printStackTrace();
            throw new TException(ex);
        }
        
    }
    
    protected void addMapEntry(String line) 
        throws TException
    {
        try {
            if (DEBUG) System.out.println("Add:" + line);
            String[] parts = line.split("\\s*\\|\\s*");
            if ((parts.length < 2) || (parts.length > 3)) {
                throw new TException.INVALID_OR_MISSING_PARM("addMapENtry requires 2 or 3 parts:" + line);
            }
            Long nodeNumber = Long.parseLong(parts[0]);
            String propName = parts[1];
            String container = null;
            if (parts.length == 3) {
                container = parts[2];
            }
            AccessNode copyNode = getAccessNode(nodeNumber, container, propName);
            if (DEBUG) System.out.println(copyNode.dump("copyNode"));
            map.put(nodeNumber, copyNode);
            
            
        } catch (TException tex) {
            throw tex;
            
        } catch (Exception ex) {
            System.out.println(MESSAGE + "Exception:" + ex);
            ex.printStackTrace();
            throw new TException(ex);
        }
        
    }
    
    protected AccessNode getAccessNode(Long nodeNumber, String container, String propName) 
        throws TException
    {
        CloudStoreInf service = null;
        if (DEBUG_ACCESS) System.out.println("getAccessNode:" 
                + " - nodeNumber=" + nodeNumber
                + " - container=" + container
                + " - propName=" + propName
        );
        String accessMode = null;
        try {
            Properties cloudProp = getNodeProp(propName);
            if (cloudProp == null) {
                throw new TException.INVALID_DATA_FORMAT(MESSAGE + "getService - Unable to locate:" +  propName);
            }
            String serviceType = cloudProp.getProperty("serviceType");
            if (StringUtil.isAllBlank(serviceType)) {
                throw new TException.INVALID_DATA_FORMAT(MESSAGE + "getService - serviceType property required but not found for :" +  propName);
            }
            if (serviceType.equals("swift")) {
                service = OpenstackCloud.getOpenstackCloud(cloudProp, logger);
                
            } else if (serviceType.equals("aws")) {
                String storageClassS = cloudProp.getProperty("storageClass");
                if (DEBUG_ACCESS) System.out.println("StorageClassS=" + storageClassS);
                String regionS = cloudProp.getProperty("region");
                service = AWSS3Cloud.getAWSS3Region(storageClassS, regionS, logger);
                
            } else if (serviceType.equals("minio")) {
                String accessKey = cloudProp.getProperty("accessKey");
                String secretKey = cloudProp.getProperty("secretKey");
                String endPoint = cloudProp.getProperty("endPoint");
                if (DEBUG_ACCESS) System.out.println("Minio S3"
                        + " - accessKey=" + accessKey
                        + " - secretKey=" + secretKey
                        + " - endPoint=" + endPoint
                );
                service = AWSS3Cloud.getMinio(
                        accessKey, secretKey, endPoint, logger);
                
            } else if (serviceType.equals("wasabi")) {
                String accessKey = cloudProp.getProperty("accessKey");
                String secretKey = cloudProp.getProperty("secretKey");
                String endPoint = cloudProp.getProperty("endPoint");
                String regionName = cloudProp.getProperty("regionName");
                if (DEBUG_ACCESS) System.out.println("Minio S3"
                        + " - accessKey=" + accessKey
                        + " - secretKey=" + secretKey
                        + " - endPoint=" + endPoint
                        + " - regionName=" + regionName
                );
                service = AWSS3Cloud.getWasabi(
                        accessKey, secretKey, endPoint, regionName, logger);
                
            } else if (serviceType.equals("pairtree")) {
                service = PairtreeCloud.getPairtreeCloud(true, logger);
                container = cloudProp.getProperty("base");
                
            } else if (serviceType.equals("store")) {
                String urlS = cloudProp.getProperty("url");
                Integer node = null;
                String nodeS = cloudProp.getProperty("node");
                if (nodeS != null) {
                    node = Integer.parseInt(nodeS);
                    container = "" + node;
                }
                service = StoreCloud.getStoreCloud(urlS, node, logger);
                
            } else if (serviceType.equals("cloudhost")) {
                String urlS = cloudProp.getProperty("base");
                service = CloudhostAPI.getCloudhostAPI(urlS, logger);
                
            } else {
                throw new TException.INVALID_DATA_FORMAT(MESSAGE + "getService - serviceType not found for :" +  serviceType);
            }
            AccessNode copyNode = new AccessNode(serviceType, accessMode, service, nodeNumber, container);
            return copyNode;
            
            
        } catch (TException tex) {
            tex.printStackTrace();
            throw tex;
            
        } catch (Exception ex) {
            System.out.println(MESSAGE + "Exception:" + ex);
            ex.printStackTrace();
            throw new TException(ex);
        }
        
    }
    
    
    public AccessNode getAccessNode(long nodeNumber) 
    {
        return map.get(nodeNumber);
    }
    
    
    public AccessKey getAccessKey(String storageURLS)
        throws TException
    {    
        AccessKey access = new AccessKey();
        try {
            if (StringUtil.isAllBlank(storageURLS)) {
                throw new TException.INVALID_DATA_FORMAT(MESSAGE + "getAccessKey - storageURL missing");
            }
            int pos = storageURLS.indexOf("/content/");
            if (pos < 0) {
                throw new TException.INVALID_DATA_FORMAT(MESSAGE + "getAccessKey - content not found as part of path:" 
                        + storageURLS);
            }
            String path = storageURLS.substring(pos+9);
            pos = path.indexOf("?");
            if (pos >= 0) {
                path = path.substring(0,pos);
            }
            
            String parts[] = path.split("/");
            
            if (parts.length != 4 ) {
                throw new TException.INVALID_DATA_FORMAT(MESSAGE + "getAccessKey - path invalid:" 
                        + path);
            }
            String nodeS = parts[0];
            Long nodeNumber = Long.parseLong(nodeS);
            String ark = URLDecoder.decode(parts[1], "utf-8");
            String versionS = parts[2];
            Long versionNumber = Long.parseLong(versionS);
            String fileID = URLDecoder.decode(parts[3], "utf-8");
            access.key = ark + "|" + versionNumber + "|" + fileID;
            access.accessNode = getAccessNode(nodeNumber);
            if (access.accessNode == null) {
                return null;
            }
            return access;
            
            
        } catch (TException tex) {
            throw tex;
            
        } catch (Exception ex) {
            System.out.println(MESSAGE + "Exception:" + ex);
            ex.printStackTrace();
            throw new TException(ex);
        }
        
    }
    
    public void getFile(String storageURLS, File outfile)
        throws TException
    {    
        try {
            AccessKey access = getAccessKey(storageURLS);
            if (access == null) {
                throw new TException.REQUESTED_ITEM_NOT_FOUND (MESSAGE + "Unable to access:" + storageURLS);
            }
            CloudStoreInf service = access.accessNode.service;
            String key = access.key;
            String container = access.accessNode.container;
            getFileException(service, container, key, outfile);
            
            
        } catch (TException tex) {
            System.out.println(MESSAGE + "TException:" + tex);
            throw tex;
            
        } catch (Exception ex) {
            System.out.println(MESSAGE + "Exception:" + ex);
            ex.printStackTrace();
            throw new TException(ex);
        }
        
    }
    
    protected void getFileException(CloudStoreInf service, String container, String key, File outfile)
        throws TException
    {
        CloudResponse response = new CloudResponse(container, key);
        if (container == null) {
            throw new TException.REQUESTED_ITEM_NOT_FOUND("Container not found:"
                    + " - service=" + service.getType().toString()
                    + " - key=" + key
            );
        }
        service.getObject(container, key, outfile, response);
        Exception exception = response.getException();
        if (exception != null) {
            if (exception instanceof TException) {
                throw (TException)exception;

            } else {
                throw new TException(exception)  ;  
            }
        }
    }
    
    public InputStream getInputStream(String storageURLS)
        throws TException
    {    
        if (DEBUG) System.out.println("NodeIO: getInputStream entered:" + storageURLS);
        DeleteOnCloseFileInputStream deleteInputStream = null;
        try {
            File tempFile = FileUtil.getTempFile("temp", ".txt");
            AccessKey access = getAccessKey(storageURLS);
            if (access == null) {
                throw new TException.EXTERNAL_SERVICE_UNAVAILABLE(MESSAGE + "Unable to access:" + storageURLS);
            }
            CloudStoreInf service = access.accessNode.service;
            String key = access.key;
            String container = access.accessNode.container;
            if (DEBUG_ACCESS) System.out.println(MESSAGE + "getInputStream"
                    + " - service=" + service.getType().toString()
                    + " - container=" + container
                    + " - key=" + key
            );
            getFileException(service, container, key, tempFile);
            deleteInputStream = new DeleteOnCloseFileInputStream(tempFile);
            return deleteInputStream;
            
            
        } catch (TException tex) {
            throw tex;
            
        } catch (Exception ex) {
            System.out.println(MESSAGE + "Exception:" + ex);
            ex.printStackTrace();
            throw new TException(ex);
        }
        
    }

    public Collection<AccessNode> getCollection() {
        return map.values();
    }

    public HashMap<Long, AccessNode> getMap() {
        return map;
    }
    
    public void printNodes(String header)
    {
        System.out.println("\nNodeIO:" + header);
        int cnt = 0;
        Collection<AccessNode> collection = getCollection();
        for (AccessNode node : collection) {
            System.out.println(node.dump("" + cnt));
            cnt++;
        }
    }
    
    public void printNodes(String header, long nodeNumber)
    {
        System.out.println("\nNodeIO:" + header);
        int cnt = 0;
        Collection<AccessNode> collection = getCollection();
        for (AccessNode node : collection) {
            if (node.nodeNumber == nodeNumber) {
                System.out.println(node.dump("--" + nodeNumber + "--"));
                break;
            }
        }
    }

    public String getNodeName() {
        return nodeName;
    }
    
    public static class AccessKey
    {
        public AccessNode accessNode = null;
        public String key = null;
        public AccessKey() { }
        public AccessKey(
                AccessNode accessNode,
                String key) {
            this.accessNode = accessNode;
            this.key = key;
        }
        
        public String dump(String header)
        {
            StringBuffer buf = new StringBuffer();
            if (!StringUtil.isAllBlank(header)) {
                buf.append(header);
            }
            buf.append("AccessKey:\n");
            buf.append(accessNode.dump(" - AccessKey"));
            buf.append("\n - key:" + key);
            return buf.toString();
        }
    }
    
    public static class AccessNode {
        public CloudStoreInf service = null;
        public String serviceType = null;
        public String accessMode = null;
        public Long nodeNumber = null;
        public String container = null;
        public AccessNode() { }
        public AccessNode(
            String serviceType,
            String accessMode,
            CloudStoreInf service,
            Long nodeNumber,
            String container
        ) {
            this.serviceType = serviceType;
            this.accessMode = accessMode;
            this.service = service;
            this.nodeNumber = nodeNumber;
            this.container = container;
        }
        
        public String dump(String header)
        {
            StringBuffer buf = new StringBuffer();
            if (!StringUtil.isAllBlank(header)) {
                buf.append(header);
            }
            buf.append(" AccessNode:"
                    + " - nodeNumber:" + nodeNumber
                    + " - serviceType:" + serviceType
                    + " - accessMode:" + accessMode
                    + " - container:" + container
            );
            return buf.toString();
        }
    }
    
    
}

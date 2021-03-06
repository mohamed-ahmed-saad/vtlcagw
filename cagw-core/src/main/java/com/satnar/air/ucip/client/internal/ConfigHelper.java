package com.satnar.air.ucip.client.internal;

import java.net.MalformedURLException;
import java.net.URL;

import com.satnar.air.ucip.client.UcipException;
import com.satnar.air.ucip.client.xmlrpc.ConfigParams;
import com.satnar.air.ucip.client.xmlrpc.XmlRpcClient;
import com.satnar.air.ucip.client.xmlrpc.XmlRpcClientFactory;
import com.satnar.air.ucip.client.xmlrpc.XmlRpcException;
import com.satnar.air.ucip.client.xmlrpc.internal.DefaultXmlRpcClientFactory;
import com.satnar.common.LogService;


public class ConfigHelper {
    
    
    private static final String UCIP_EP_COUNT = "ucipEndpointCount";
    private static final String OWN_HOST_NAME = "ownHostName";
    private static final String OWN_NODE_TYPE = "ownNodeType";
    private static final String NEGOTIATED_CAPABILITIES = "negotiatedCapabilities";
    private static final String UCIP_PREFIX = "ucip.";
    private static final String CONN_TIMEOUT = ".connectionTimeout";
    private static final String REPLY_TIMEOUT = ".replyTimeout";
    private static final String IDLE_TIMEOUT = ".idleTimeout";
    private static final String IDLE_CONN_TIMEOUT_INTERVAL = ".idleConnectionTimeoutInterval";
    private static final String MAX_CONNECTIONS = ".maxConnections";
    private static final String MAX_TOTAL_CONNECTIONS = ".maxTotalConnections";
    private static final String PASSWORD = ".password";
    private static final String RETRY_COUNT = ".retryCount";
    private static final String STALE_CHECK = ".staleCheckingEnabled";
    private static final String USE_APACHE = ".useApacheHttpClient";
    private static final String USER_AGENT = ".userAgent";
    private static final String USERNAME = ".username";
    private static final String SO_TIMEOUT = ".soTimeout";
    private static final String URL_ADDRESS = ".urlAddress";
    private static final String SITE = ".site";
    private static final String DEFAULT_NAI = ".defaultNai";
    
    
    
    
    public static void initializeConfig(AirClientImpl client) throws UcipException {
        String param = null;
        
        // own host name
        LogService.appLog.debug("Configuring ownHostName...");
        param = client.getConfig().getProperty(OWN_HOST_NAME);
        if (param == null || param.equalsIgnoreCase(""))
            throw new UcipException(OWN_HOST_NAME + " is not set or empty!");
        client.setOriginHostName(param);
        
        // own node type
        LogService.appLog.debug("Configuring ownHostType...");
        param = client.getConfig().getProperty(OWN_NODE_TYPE);
        if (param == null || param.equalsIgnoreCase(""))
            throw new UcipException(OWN_HOST_NAME + " is not set or empty!");
        client.setOriginNodeType(param);
        
        
        // UCIP Endpoint Count...
        LogService.appLog.debug("Configuring ucipEndpointCount...");
        int ucipCount = 0;
        param = client.getConfig().getProperty(UCIP_EP_COUNT);
        if (param == null || param.equalsIgnoreCase(""))
            throw new UcipException("'ucipEndpointCount' is not set!");
        
        try {
            ucipCount = Integer.parseInt(param);
            if (ucipCount == 0) 
                throw new UcipException("'ucipEndpointCount' is set to Zero (0)");
        } catch (NumberFormatException e) {
            throw new UcipException("'ucipEndpointCount' is not numeric! Found: " + param);
        }
        
        // negotiated capabilities
        LogService.appLog.debug("Configuring negotiatedCapabilities...");
        param = client.getConfig().getProperty(NEGOTIATED_CAPABILITIES);
        if (param == null || param.equalsIgnoreCase(""))
            throw new UcipException(NEGOTIATED_CAPABILITIES + " is not set or empty!");
        try {
            client.setNegotiatedCapabilities(Integer.parseInt(param));
        } catch (NumberFormatException e) {
            client.setNegotiatedCapabilities(805646916);
        }
        LogService.appLog.debug(String.format("Negotiated Capabilities '%d' set in air client", client.getNegotiatedCapabilities()));
        
        client.setDefaultSite("1"); //veittel specific hard code
        LogService.appLog.debug("Default Site '1' set in air client");
        
        
        XmlRpcClientFactory factory = new DefaultXmlRpcClientFactory();
        for (int i =1; i <= ucipCount; i++) {
            ConfigParams configParams = new ConfigParams();
            
            // default nai
            String key = UCIP_PREFIX + i + DEFAULT_NAI;
            LogService.appLog.debug(String.format("Configuring %s...", key));
            param = client.getConfig().getProperty(key);
            if (param == null || param.equalsIgnoreCase(""))
                throw new UcipException(key + " is not set or empty!");
            client.setDefaultNai(param);
            LogService.appLog.debug(String.format("Default NAI '%s' set in air client", client.getDefaultNai()));
            
            // connection timeout
            key = UCIP_PREFIX + i + CONN_TIMEOUT;
            LogService.appLog.debug(String.format("Configuring %s...", key));
            param = client.getConfig().getProperty(key);
            if (param == null || param.equalsIgnoreCase(""))
                throw new UcipException(key + " is not set or empty!");
            
            try {
                configParams.setConnectionTimeout(Integer.parseInt(param));
            } catch (NumberFormatException e) {
                // can also set to 30 secs but sounds crazy
                throw new UcipException(key + " is not numeric! Found: " + param);
            }
            
            key = UCIP_PREFIX + i + REPLY_TIMEOUT;
            LogService.appLog.debug(String.format("Configuring %s...", key));
            param = client.getConfig().getProperty(key);
            if (param == null || param.equalsIgnoreCase(""))
                throw new UcipException(key + " is not set or empty!");
            
            try {
                configParams.setReplyTimeout(Integer.parseInt(param));
            } catch (NumberFormatException e) {
                // can also set to 30 secs but sounds crazy
                throw new UcipException(key + " is not numeric! Found: " + param);
            }
            
            key = UCIP_PREFIX + i + IDLE_TIMEOUT;
            LogService.appLog.debug(String.format("Configuring %s...", key));
            param = client.getConfig().getProperty(key);
            if (param == null || param.equalsIgnoreCase(""))
                throw new UcipException(key + " is not set or empty!");
            
            try {
                configParams.setIdleConnctionTimeout(Integer.parseInt(param));
            } catch (NumberFormatException e) {
                // can also set to 30 secs but sounds crazy
                throw new UcipException(key + " is not numeric! Found: " + param);
            }
            
            key = UCIP_PREFIX + i + IDLE_CONN_TIMEOUT_INTERVAL;
            LogService.appLog.debug(String.format("Configuring %s...", key));
            param = client.getConfig().getProperty(key);
            if (param == null || param.equalsIgnoreCase(""))
                throw new UcipException(key + " is not set or empty!");
            
            try {
                configParams.setIdleConnectionTimeoutInterval(Integer.parseInt(param));
            } catch (NumberFormatException e) {
                // can also set to 30 secs but sounds crazy
                throw new UcipException(key + " is not numeric! Found: " + param);
            }
            
            key = UCIP_PREFIX + i + MAX_CONNECTIONS;
            LogService.appLog.debug(String.format("Configuring %s...", key));
            param = client.getConfig().getProperty(key);
            if (param == null || param.equalsIgnoreCase(""))
                throw new UcipException(key + " is not set or empty!");
            
            try {
                configParams.setMaxConnectionsPerHost(Integer.parseInt(param));
            } catch (NumberFormatException e) {
                throw new UcipException(key + " is not numeric! Found: " + param);
            }
            
            key = UCIP_PREFIX + i + MAX_TOTAL_CONNECTIONS;
            LogService.appLog.debug(String.format("Configuring %s...", key));
            param = client.getConfig().getProperty(key);
            if (param == null || param.equalsIgnoreCase(""))
                throw new UcipException(key + " is not set or empty!");
            
            try {
                configParams.setMaxTotalConnections(Integer.parseInt(param));
            } catch (NumberFormatException e) {
                throw new UcipException(key + " is not numeric! Found: " + param);
            }
            
            key = UCIP_PREFIX + i + PASSWORD;
            LogService.appLog.debug(String.format("Configuring %s...", key));
            param = client.getConfig().getProperty(key);
            if (param == null || param.equalsIgnoreCase(""))
                throw new UcipException(key + " is not set or empty!");
            configParams.setPassword(param);
            
            key = UCIP_PREFIX + i + RETRY_COUNT;
            LogService.appLog.debug(String.format("Configuring %s...", key));
            param = client.getConfig().getProperty(key);
            if (param == null || param.equalsIgnoreCase(""))
                throw new UcipException(key + " is not set or empty!");
            
            try {
                configParams.setRetryCount(Integer.parseInt(param));
            } catch (NumberFormatException e) {
                throw new UcipException(key + " is not numeric! Found: " + param);
            }
            
            key = UCIP_PREFIX + i + STALE_CHECK;
            LogService.appLog.debug(String.format("Configuring %s...", key));
            param = client.getConfig().getProperty(key);
            if (param == null || param.equalsIgnoreCase(""))
                throw new UcipException(key + " is not set or empty!");
            configParams.setStaleCheckingEnabled(Boolean.parseBoolean(param));
            
            key = UCIP_PREFIX + i + USE_APACHE;
            LogService.appLog.debug(String.format("Configuring %s...", key));
            param = client.getConfig().getProperty(key);
            if (param == null || param.equalsIgnoreCase(""))
                throw new UcipException(key + " is not set or empty!");
            configParams.setUseApacheHttpClient(Boolean.parseBoolean(param));
            
            key = UCIP_PREFIX + i + USER_AGENT;
            LogService.appLog.debug(String.format("Configuring %s...", key));
            param = client.getConfig().getProperty(key);
            if (param == null || param.equalsIgnoreCase(""))
                throw new UcipException(key + " is not set or empty!");
            configParams.setUserAgent(param);
            
            key = UCIP_PREFIX + i + USERNAME;
            LogService.appLog.debug(String.format("Configuring %s...", key));
            param = client.getConfig().getProperty(key);
            if (param == null || param.equalsIgnoreCase(""))
                throw new UcipException(key + " is not set or empty!");
            configParams.setUsername(param);
            
            key = UCIP_PREFIX + i + SO_TIMEOUT;
            LogService.appLog.debug(String.format("Configuring %s...", key));
            param = client.getConfig().getProperty(key);
            if (param == null || param.equalsIgnoreCase(""))
                throw new UcipException(key + " is not set or empty!");
            
            try {
                configParams.setSoTimeout(Integer.parseInt(param));
            } catch (NumberFormatException e) {
                throw new UcipException(key + " is not numeric! Found: " + param);
            }
            
            key = UCIP_PREFIX + i + URL_ADDRESS;
            LogService.appLog.debug(String.format("Configuring %s...", key));
            param = client.getConfig().getProperty(key);
            if (param == null || param.equalsIgnoreCase(""))
                throw new UcipException(key + " is not set or empty!");
            
            try {
                new URL(param);
                configParams.setUrl(param);
            } catch (MalformedURLException e) {
                throw new UcipException(key + " is not numeric! Found: " + param);
            }
            
            key = UCIP_PREFIX + i + SITE;
            LogService.appLog.debug(String.format("Configuring %s...", key));
            param = client.getConfig().getProperty(key);
            if (param == null || param.equalsIgnoreCase(""))
                throw new UcipException(key + " is not set or empty!");
            
            
            
            try {
                XmlRpcClient rpcClient = factory.create(configParams);
                client.addSitePeer(param, rpcClient);
                LogService.appLog.debug(String.format("Added client connection#" + i));
            } catch (XmlRpcException e) {
                throw new UcipException(e.code, e.getMessage(), e);
            }
        }
        LogService.appLog.info("Air Client is now configured and ready for use!!");
    }
}

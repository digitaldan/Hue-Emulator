package com.hueemulator.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.ObjectMapper;

import com.hueemulator.emulator.Controller;
import com.hueemulator.model.PHBridgeConfiguration;
import com.hueemulator.server.handlers.ConfigurationAPI;
import com.hueemulator.server.handlers.GroupsAPI;
import com.hueemulator.server.handlers.LightsAPI;
import com.hueemulator.server.handlers.SchedulesAPI;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;


public class Server {

    private HttpServer httpServer;

    public Server(PHBridgeConfiguration bridgeConfiguration, Controller controller, String portNumber) throws IOException {
        InetSocketAddress addr = new InetSocketAddress(Integer.valueOf(portNumber));

        httpServer = HttpServer.create(addr, 0);

        createContext(bridgeConfiguration, controller);
        httpServer.setExecutor(Executors.newCachedThreadPool());  
    }

    public void createContext(PHBridgeConfiguration bridgeConfiguration, Controller controller) {
        httpServer.createContext("/api", new MyHandler(bridgeConfiguration, controller));
    }

    public void removeContext() {
        httpServer.removeContext("/api");
    }

    public HttpServer getHttpServer() {
        return httpServer;
    }

    public void setHttpServer(HttpServer httpServer) {
        this.httpServer = httpServer;
    }
}

class MyHandler implements HttpHandler {

    private PHBridgeConfiguration bridgeConfiguration;
    private Controller controller;
    private LightsAPI lightsAPIhandler;
    private ConfigurationAPI configurationAPIhandler;
    private GroupsAPI groupsAPIhandler;
    private SchedulesAPI schedulesAPIhandler;

    public MyHandler(PHBridgeConfiguration bridgeConfiguration, Controller controller) {
        this.bridgeConfiguration = bridgeConfiguration;
        this.controller          = controller;

        lightsAPIhandler = new LightsAPI();
        groupsAPIhandler = new GroupsAPI();
        schedulesAPIhandler = new SchedulesAPI();
        configurationAPIhandler = new ConfigurationAPI();
    }

    public void handle(HttpExchange exchange) throws IOException {
        String requestMethod = exchange.getRequestMethod();
        String url = exchange.getRequestURI().toString();

        OutputStream responseBody = exchange.getResponseBody();
        Headers responseHeaders = exchange.getResponseHeaders();
        responseHeaders.set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, 0);
        ObjectMapper mapper = new ObjectMapper();


        String urlElements[] = url.split("/");   


        if (url.equals("/api") || url.equals("/api/")) {
            configurationAPIhandler.createNewUsername(bridgeConfiguration, responseBody, requestMethod);
        }
        // Check if username is on the whitelsit.  If not a JSON "Unauthoized User" response is sent back.
        else if (!configurationAPIhandler.isValidUserName(bridgeConfiguration, responseBody, urlElements)) {
            configurationAPIhandler.returnErrorResponse("1", "unauthorized user", responseBody);
            return;
        }


        if (requestMethod.equalsIgnoreCase("GET")) {
            handleGet(mapper, url, responseBody, urlElements);
            responseBody.close();
        }
        else if (requestMethod.equalsIgnoreCase("DELETE")) {
            handleDelete(mapper, responseBody, urlElements);
            responseBody.close();
        }
        else  if (requestMethod.equalsIgnoreCase("PUT") || requestMethod.equalsIgnoreCase("POST")) {

            InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(),"utf-8");
            BufferedReader br = new BufferedReader(isr);

            String jSONString="";
            String line="";
            while ((line = br.readLine()) != null) {
                jSONString += line;
            }

            if (requestMethod.equalsIgnoreCase("PUT")) {
                handlePut(mapper, url, responseBody, jSONString, urlElements);
            }
            if (requestMethod.equalsIgnoreCase("POST")) {
                handlePost(mapper, url, responseBody, jSONString, urlElements);
            }
        } 
    }

    public void handlePut(ObjectMapper mapper, String url, OutputStream responseBody, String jSONString, String[] urlElements) throws JsonParseException, IOException  {
        int noURLEelements=urlElements.length;
        String lastURLElement = urlElements[noURLEelements-1];

        if (urlElements[noURLEelements-2].equals("lights")) {
            String light=urlElements[noURLEelements-1];
            lightsAPIhandler.setLightAttributes_1_5(mapper, jSONString, bridgeConfiguration, responseBody, controller, light);
        }
        else if (lastURLElement.equals("state")) {
            lightsAPIhandler.setLightState_1_6(mapper, jSONString, bridgeConfiguration, responseBody, controller, urlElements[noURLEelements-2]);
        }
        else if (lastURLElement.equals("action")) {
            groupsAPIhandler.setGroupState_2_5(mapper, jSONString, bridgeConfiguration, responseBody, controller, urlElements[noURLEelements-2],lightsAPIhandler);
        }
        else if (urlElements[noURLEelements-2].equals("groups")) {
            String groupIdentifier=urlElements[noURLEelements-1];         
            groupsAPIhandler.setGroupAttributes_2_4(mapper, jSONString, bridgeConfiguration, responseBody, controller, groupIdentifier);
        } 
        else if (urlElements[noURLEelements-2].equals("schedules")) {
            String scheduleIdentifier=urlElements[noURLEelements-1];         
            schedulesAPIhandler.setScheduleAttributes_3_4(mapper, jSONString, bridgeConfiguration, responseBody, controller, scheduleIdentifier);
        } 
    }

    public void handlePost(ObjectMapper mapper, String url, OutputStream responseBody, String jSONString, String[] urlElements) throws JsonParseException, IOException  {
        int noURLEelements=urlElements.length;
        String lastURLElement = urlElements[noURLEelements-1];

        if (lastURLElement.equals("schedules")) {
            schedulesAPIhandler.createSchedule_3_2(mapper, jSONString, bridgeConfiguration, responseBody, controller);
        }
        else if (lastURLElement.equals("groups")) {
            groupsAPIhandler.createGroup_2_2(mapper, jSONString, bridgeConfiguration, responseBody, controller);
        }

    }

    public void handleGet(ObjectMapper mapper, String url, OutputStream responseBody, String[] urlElements) throws JsonGenerationException, IOException {

        int noURLEelements=urlElements.length;
        String lastURLElement = urlElements[noURLEelements-1];

        // URL Ends with /lights
        if (lastURLElement.equals("lights")) {
            lightsAPIhandler.getAllLights_1_1(bridgeConfiguration, responseBody, controller);
        }
        else if (urlElements[noURLEelements-2].equals("lights")) {
            String light=urlElements[noURLEelements-1];
            lightsAPIhandler.getLightAttributes_1_4(mapper, bridgeConfiguration, responseBody, controller, light);
        }
        else if (lastURLElement.equals("groups")) {
            groupsAPIhandler.getAllGroups_2_1(mapper, bridgeConfiguration, responseBody, controller);
        }
        else if (lastURLElement.equals("config")) {  
            configurationAPIhandler.getConfig_4_2(mapper, bridgeConfiguration, responseBody, controller);
        }
        else if (lastURLElement.equals("schedules")) {  
            schedulesAPIhandler.getAllSchedules_3_1(bridgeConfiguration, responseBody, controller);
        }
        else if (urlElements[noURLEelements-2].equals("schedules")) {
            String scheduleId=urlElements[noURLEelements-1];
            schedulesAPIhandler.getScheduleAttributes_3_3(mapper, bridgeConfiguration, responseBody, controller, scheduleId);
        }
        else if (urlElements[noURLEelements-2].equals("groups")) {
            String groupId=urlElements[noURLEelements-1];
            groupsAPIhandler.getGroupAttributes_2_3(mapper, bridgeConfiguration, responseBody, controller, groupId);
        }
        else {
            configurationAPIhandler.getFullState_4_5(mapper, bridgeConfiguration, responseBody, controller);       
        }
    }

    public void handleDelete(ObjectMapper mapper, OutputStream responseBody, String[] urlElements) throws JsonParseException, IOException  {
        int noURLEelements=urlElements.length;
        String lastURLElement = urlElements[noURLEelements-1];

        if (urlElements[noURLEelements-2].equals("schedules")) {
            schedulesAPIhandler.deleteSchedule_3_5(mapper, bridgeConfiguration, responseBody, controller, lastURLElement);
        }           
        else if (urlElements[noURLEelements-2].equals("groups")) {
            groupsAPIhandler.deleteGroup_2_6(mapper, bridgeConfiguration, responseBody, controller, lastURLElement);
        }           

    }

}
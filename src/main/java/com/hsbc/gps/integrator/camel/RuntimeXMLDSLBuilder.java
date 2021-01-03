package com.hsbc.gps.integrator.camel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*//Start: Apache Camel Version 3.5 imports
import org.apache.camel.model.Model;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.model.RouteTemplateDefinition;
import org.apache.camel.model.RouteTemplatesDefinition;
import org.apache.camel.spi.XMLRoutesDefinitionLoader;
//End: Apache Camel Version 3.5 imports */

import org.apache.camel.Route;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.model.rest.RestsDefinition;
import org.apache.camel.model.rest.VerbDefinition;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Configuration
public class RuntimeXMLDSLBuilder extends RuntimeCamelDSLLoader {


    @Scheduled(fixedDelay = 5000, initialDelay = 1000)
    private void loadXMLDSLRoutes() {

        System.out.println("****************** RuntimeXMLDSLBuilder.loadXMLDSLRoutes() method called *************************");
        checkAndLoadFile();
    }

    public void refreshRoutes(String dslPath) {
        System.out.println("RuntimeXMLDSLBuilder.refreshRoutes() method called.");
        try {

            Resource[] resourcesRoutes;
            if (dslPath.toUpperCase().endsWith(".XML")) {
                resourcesRoutes = resourceResolver.getResources(dslPath);
            } else {
                resourcesRoutes = resourceResolver.getResources(dslPath + "/**/*.xml");
            }

            Map<String, RouteDefinition> routesDefMap = new HashMap<String, RouteDefinition>();
            Map<String, RestDefinition> restsDefMap = new HashMap<String, RestDefinition>();

            for (Resource resource : resourcesRoutes) {
                List<RouteDefinition> routesDefList = new ArrayList<RouteDefinition>();
                List<RestDefinition> restsDefList = new ArrayList<RestDefinition>();
                //List<RouteTemplateDefinition> routesTemplateDefList = new ArrayList<RouteTemplateDefinition>(); //Apache Camel Version 3.5 code

                System.out.println("Route File Name : " + resource.getFilename());
                if (resource.getFile().canRead() && resource.getFile().length() > 0) {

                    try {
                        /*//Start: Apache Camel Version 3.5 code
                        ExtendedCamelContext extendedCamelContext = camelContext.adapt(ExtendedCamelContext.class);
                        XMLRoutesDefinitionLoader xmlLoader = extendedCamelContext.getXMLRoutesDefinitionLoader();
                        RoutesDefinition routesDef = (RoutesDefinition) xmlLoader.loadRoutesDefinition(camelContext,resource.getInputStream());
                        RestsDefinition restsDef = (RestsDefinition) xmlLoader.loadRestsDefinition(camelContext,resource.getInputStream());
                        RouteTemplatesDefinition routesTemplateDef = (RouteTemplatesDefinition) xmlLoader.loadRouteTemplatesDefinition(camelContext,resource.getInputStream());
                        //End: Apache Camel Version 3.5 code */

                        //Start:Apache Camel Version 2.25.3 code. This code needs to be removed once Version 3.5 jars are available in nexus
                        RoutesDefinition routesDef = camelContext.loadRoutesDefinition(resource.getInputStream());
                        RestsDefinition restsDef = camelContext.loadRestsDefinition(resource.getInputStream());
                        //End:Apache Camel Version 2.25.3 code. This code needs to be removed once Version 3.5 jars are available in nexus
                        
                        if (routesDef != null) {
                            routesDefList.addAll(routesDef.getRoutes());
                        }

                        if (restsDef != null) {
                            List<RestDefinition> list = restsDef.getRests();
                            for (RestDefinition restdef : list) {
                                List<VerbDefinition> verbs = restdef.getVerbs();
                                for (VerbDefinition verb : verbs) {
                                    System.out.println("XML DSL RestDefinition Verb Id : " + verb.getId());
                                }

                            }
                            restsDefList.addAll(restsDef.getRests());
                        }

                        /*//Apache Camel Version 3.5 code
                        if (routesTemplateDef != null) {
                            routesTemplateDefList.addAll(routesTemplateDef.getRouteTemplates());
                            camelContext.getExtension(Model.class).addRouteTemplateDefinitions(routesTemplateDefList);
                        }*/

                        for (RouteDefinition routedef : routesDefList) {
                            System.out.println("XML DSL Route Definition : " + routedef.toString());

                            if (!routedef.getId().isEmpty()) {
                                System.out.println("XML route ID added :  " + routedef.getId());
                                routesDefMap.put(routedef.getId(), routedef);
                            } else {
                                System.out.println("XML route is ignored due to ID is missing :  " + routedef.getDescriptionText());
                            }
                        }

                        for (RestDefinition restDef : restsDefList) {
                            System.out.println("XML DSL Rest Definition ID : " + restDef.getId());

                            if (!restDef.getId().isEmpty()) {
                                System.out.println("XML rest ID added :  " + restDef.getId());
                                List<VerbDefinition> verbsList = restDef.getVerbs();
                                List<VerbDefinition> newVerbs = new ArrayList<VerbDefinition>();
                                for (VerbDefinition verb : verbsList) {
                                    if (!verb.getId().isEmpty()) {
                                        System.out.println("XML rest verb ID added :  " + verb.getId());
                                        newVerbs.add(verb);
                                    } else {
                                        System.out.println("XML rest verb is ignored due to ID is missing :  " + verb.getDescriptionText());
                                    }
                                }
                                restDef.setVerbs(newVerbs);//Set new list of verbes with ID
                                restsDefMap.put(restDef.getId(), restDef);
                            } else {
                                System.out.println("XML rest is ignored due to ID is missing :  " + restDef.getDescriptionText());
                            }
                        }

                    } catch (Exception exception) {
                        exception.printStackTrace();
                    }
                }
            }

            if (!routesDefMap.isEmpty()) {
                // update the routes (add will remove and shutdown first)
                //camelContext.getExtension(Model.class).addRouteDefinitions(routesDefMap.values()); //Apache Camel Version 3.5 code
                camelContext.addRouteDefinitions(routesDefMap.values()); //Apache Camel Version 2.25.3 code. This code needs to be removed once Version 3.5 jars are available in nexus

                List<Route> routes = camelContext.getRoutes();
                for (Route route : routes) {
                    System.out.println("From camelContext : " + route.getId());
                }
            }

            if (!restsDefMap.isEmpty()) {
                // update the rests (add will remove and shutdown first)
                //camelContext.getExtension(Model.class).addRestDefinitions(restsDefMap.values(), true); //Apache Camel Version 3.5 code
                camelContext.addRestDefinitions(restsDefMap.values()); //Apache Camel Version 2.25.3 code. This code needs to be removed once Version 3.5 jars are available in nexus
            }

        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

}

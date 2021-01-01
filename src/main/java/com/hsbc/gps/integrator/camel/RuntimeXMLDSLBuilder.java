package com.hsbc.gps.integrator.camel;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.Route;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.model.Model;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RouteTemplateDefinition;
import org.apache.camel.model.RouteTemplatesDefinition;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.model.rest.RestsDefinition;
import org.apache.camel.model.rest.VerbDefinition;
import org.apache.camel.spi.XMLRoutesDefinitionLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Configuration
public class RuntimeXMLDSLBuilder implements Processor {
    @Autowired
    private CamelContext camelContext;

    @Autowired
    private ProducerTemplate producerTemplate;

    @Autowired
    private ResourcePatternResolver resourceResolver;

    @Value("${integratorengine.feed}")
    private String integratorfeed;

    private WatchService watcher;
    private Map<WatchKey, Path> watchKeys;

    RuntimeXMLDSLBuilder() {
        watchKeys = new HashMap<WatchKey, Path>();
        this.resourceResolver = resourceResolver == null ? new PathMatchingResourcePatternResolver() : resourceResolver;
    }

    @Autowired
    RuntimeXMLDSLBuilder(ResourcePatternResolver resourceResolver) {
        watchKeys = new HashMap<WatchKey, Path>();
        this.resourceResolver = resourceResolver == null ? new PathMatchingResourcePatternResolver() : resourceResolver;
    }

    @PostConstruct
    public void init() {
        try {
            Resource routesFolder = resourceResolver.getResource(integratorfeed);

            watcher = FileSystems.getDefault().newWatchService();
            System.out.println("Folder Path : " + routesFolder.getFile().getPath());
            Path dirPath = Paths.get(routesFolder.getFile().getPath());
            walkAndRegisterDirectories(dirPath);

        } catch (Exception exception) {
            exception.printStackTrace();
        }

        refreshRoutes(integratorfeed);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        System.out.println("RuntimeXMLDSLBuilder.process() method called.");
        refreshRoutes(integratorfeed);
    }

    private void refreshRoutes(String dslPath) {
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
                List<RouteTemplateDefinition> routesTemplateDefList = new ArrayList<RouteTemplateDefinition>();

                System.out.println("Route File Name : " + resource.getFilename());
                if (resource.getFile().canRead() && resource.getFile().length() > 0) {

                    try {
                        ExtendedCamelContext extendedCamelContext = camelContext.adapt(ExtendedCamelContext.class);
                        XMLRoutesDefinitionLoader xmlLoader = extendedCamelContext.getXMLRoutesDefinitionLoader();
                        RoutesDefinition routesDef = (RoutesDefinition) xmlLoader.loadRoutesDefinition(camelContext,
                                resource.getInputStream());
                        RestsDefinition restsDef = (RestsDefinition) xmlLoader.loadRestsDefinition(camelContext,
                        resource.getInputStream());
                        RouteTemplatesDefinition routesTemplateDef = (RouteTemplatesDefinition) xmlLoader.loadRouteTemplatesDefinition(camelContext,
                        resource.getInputStream());
                            
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

                        if (routesTemplateDef != null) {
                            routesTemplateDefList.addAll(routesTemplateDef.getRouteTemplates());
                            camelContext.getExtension(Model.class).addRouteTemplateDefinitions(routesTemplateDefList);
                        }

                        for (RouteDefinition routedef : routesDefList) {
                            System.out.println("XML DSL Route Definition : " + routedef.toString());

                            if (!routedef.getRouteId().isEmpty()) {
                                System.out.println("XML route ID added :  " + routedef.getRouteId());
                                routesDefMap.put(routedef.getRouteId(), routedef);
                            }
                            else {
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
                                    if(!verb.getId().isEmpty()) {
                                        System.out.println("XML rest verb ID added :  " + verb.getId());
                                        newVerbs.add(verb);
                                    }
                                    else {
                                        System.out.println("XML rest verb is ignored due to ID is missing :  " + verb.getDescriptionText());
                                    }
                                }
                                restDef.setVerbs(newVerbs);//Set new list of verbes with ID
                                restsDefMap.put(restDef.getId(), restDef);
                            }
                            else {
                                System.out.println("XML rest is ignored due to ID is missing :  " + restDef.getDescriptionText());
                            }
                        }

                    } catch (FileNotFoundException exception) {
                        exception.printStackTrace();
                    } catch (Exception exception) {
                        throw RuntimeCamelException.wrapRuntimeException(exception);
                    }
                }
            }

            if (!routesDefMap.isEmpty()) {
                // update the routes (add will remove and shutdown first)
                camelContext.getExtension(Model.class).addRouteDefinitions(routesDefMap.values());

                List<Route> routes = camelContext.getRoutes();
                for (Route route : routes) {
                    System.out.println("From camelContext : " + route.getRouteId());
                }
            }

            if (!restsDefMap.isEmpty()) {
                // update the rests (add will remove and shutdown first)
                camelContext.getExtension(Model.class).addRestDefinitions(restsDefMap.values(), true);
            }
            
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    @Scheduled(fixedDelay = 5000, initialDelay = 1000)
    private void loadXMLDSLRoutes() {

        System.out.println(
                "****************** RuntimeXMLDSLBuilder.loadXMLDSLRoutes() method called *************************");

        try {

            WatchKey watchKey = watcher.take();
            Path dir = watchKeys.get(watchKey);
            if (dir == null) {
                System.err.println("WatchKey not recognized!!");
            } else {
                for (WatchEvent<?> evt : watchKey.pollEvents()) {
                    System.out.printf("****************************************************\n");
                    System.out.printf("  %s %d %s\n", evt.kind(), evt.count(), evt.context());
                    System.out.printf("****************************************************\n");

                    Path evtPath = (Path) evt.context();
                    Path fullPath = dir.resolve(evtPath);
                    if (Files.isDirectory(fullPath)) {
                        walkAndRegisterDirectories(fullPath);
                    }

                    if (evtPath.toString().toUpperCase().endsWith(".XML")) {
                        if (evt.kind() != ENTRY_CREATE) {
                            producerTemplate.sendBody("direct:IntegratorRoutesRefresh", "Refresh Routes");
                        } else {
                            refreshRoutes("file:" + fullPath.toFile().getAbsolutePath());
                        }
                    }
                }
            }

            // reset key and remove from set if directory no longer accessible
            boolean valid = watchKey.reset();
            if (!valid) {
                watchKeys.remove(watchKey);
            }

        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    private void registerDirectory(Path dir) throws IOException {
        WatchKey key = dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
        watchKeys.put(key, dir);
    }

    private void walkAndRegisterDirectories(final Path start) throws IOException {
        // register directory and sub-directories
        Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                registerDirectory(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}

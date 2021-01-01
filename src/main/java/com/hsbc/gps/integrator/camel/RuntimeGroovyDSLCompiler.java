package com.hsbc.gps.integrator.camel;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
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
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.model.rest.RestsDefinition;
import org.apache.camel.model.rest.VerbDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.SingletonBeanRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import groovy.lang.GroovyClassLoader;
import groovy.util.GroovyScriptEngine;
import groovy.util.ResourceException;
import groovy.util.ScriptException;

@Component
@Configuration
public class RuntimeGroovyDSLCompiler implements Processor {

    private final GroovyClassLoader loader;
    //private final GroovyShell shell;

    @Autowired
    ApplicationContext applicationContext;

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
    private BeanDefinitionRegistry beanFactory;

    public RuntimeGroovyDSLCompiler() {
        loader = new GroovyClassLoader(this.getClass().getClassLoader());
        //shell = new GroovyShell(loader, new Binding());
        watchKeys = new HashMap<WatchKey, Path>();
        this.resourceResolver = resourceResolver == null ? new PathMatchingResourcePatternResolver() : resourceResolver;
    }

    @Autowired
    RuntimeGroovyDSLCompiler(ResourcePatternResolver resourceResolver) {
        loader = new GroovyClassLoader(this.getClass().getClassLoader());
        //shell = new GroovyShell(loader, new Binding());
        watchKeys = new HashMap<WatchKey, Path>();
        this.resourceResolver = resourceResolver == null ? new PathMatchingResourcePatternResolver() : resourceResolver;
    }

    @PostConstruct
    public void init() {
        try {
            beanFactory = (BeanDefinitionRegistry) applicationContext.getAutowireCapableBeanFactory();
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
        System.out.println("RuntimeGroovyDSLCompiler.process() method called.");
        refreshRoutes(integratorfeed);
    }

    private void refreshRoutes(String dslPath) {
        try {

            Resource[] groovyResources;
            if (dslPath.toUpperCase().endsWith(".GROOVY")) {
                groovyResources = resourceResolver.getResources(dslPath);
            } else {
                groovyResources = resourceResolver.getResources(dslPath + "/**/*.groovy");
            }

            for (Resource resource : groovyResources) {

                System.out.println("Route File Name : " + resource.getFilename());
                if (resource.getFile().canRead() && resource.getFile().length() > 0) {

                    //Due to security reason commenting this code of executing the standalone script.
                    /*if (resource.getFilename().endsWith("Main.groovy")) {
                        Object objScript = runGroovyInShell(resource.getFile().toPath());
                        System.out.println("Groovy Main Script file running result : " + objScript);
                    } else*/ {
                        List<Object> objectsList = loadGroovyScript(integratorfeed, resource.getFile().toPath());
                        List<RouteBuilder> routesList = new ArrayList<RouteBuilder>();

                        for (Object obj : objectsList) {
                            System.out.println("Groovy object type : " + obj.getClass().getCanonicalName());

                            if (obj instanceof RouteBuilder) {
                                routesList.add((RouteBuilder) obj);
                            } else {

                                Object existingBean = camelContext.getRegistry()
                                        .lookupByName(obj.getClass().getCanonicalName());

                                if (existingBean != null) {

                                    System.out.println(
                                            "Bean reference already exist in the context registry. Replace the bean : "
                                                    + existingBean.getClass().getCanonicalName());

                                    // beanFactory.removeBeanDefinition(obj.getClass().getCanonicalName());
                                    ((DefaultListableBeanFactory) beanFactory)
                                            .destroySingleton(obj.getClass().getCanonicalName());
                                }

                                // camelContext.getRegistry().bind(obj.getClass().getCanonicalName(), obj);
                                ((SingletonBeanRegistry) beanFactory)
                                        .registerSingleton(obj.getClass().getCanonicalName(), obj);
                            }
                        }

                        for (RouteBuilder routeBuilder : routesList) {
                            final String routeId = routeBuilder.getClass().getSimpleName();
                            System.out.println("Groovy route builder Id : " + routeId);
                            
                            RestsDefinition restsDefinition = routeBuilder.getRestCollection();
                            if(restsDefinition != null) {
                                List<RestDefinition> restsDefList = restsDefinition.getRests();
                                for (RestDefinition restDef : restsDefList) {
                                    System.out.println("Groovy DSL Rest Definition ID : " + restDef.getId());
        
                                    if (!restDef.getId().isEmpty()) {
                                        System.out.println("Groovy rest ID added :  " + restDef.getId());
                                        List<VerbDefinition> verbsList = restDef.getVerbs();
                                        List<VerbDefinition> newVerbs = new ArrayList<VerbDefinition>();
                                        for (VerbDefinition verb : verbsList) {
                                            if(!verb.getId().isEmpty()) {
                                                System.out.println("Groovy rest verb ID added :  " + verb.getId());
                                                newVerbs.add(verb);
                                            }
                                            else {
                                                System.out.println("Groovy rest verb is ignored due to ID is missing :  " + verb.getDescriptionText());
                                            }
                                        }
                                        restDef.setVerbs(newVerbs);//Set new list of verbes with ID
                                    }
                                    else {
                                        System.out.println("Groovy rest is ignored due to ID is missing :  " + restDef.getDescriptionText());
                                    }
                                }
                            }

                            camelContext.addRoutes(routeBuilder);
                        }
                    }
                }
            }
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    @Scheduled(fixedDelay = 5000, initialDelay = 1000)
    private void loadGroovyDSLRoutes() {

        System.out.println(
                "****************** RuntimeGroovyDSLCompiler.loadGroovyDSLRoutes() method called *************************");

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

                    if (evtPath.toString().toUpperCase().endsWith(".GROOVY")) {
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

    private List<Object> loadGroovyScript(String basePath, Path javaFile)
            throws IllegalAccessException, InstantiationException, IOException, IllegalArgumentException,
            InvocationTargetException, NoSuchMethodException, SecurityException, ResourceException, ScriptException {

        loader.clearCache(); // Clear previous loaded cache

        Resource[] resources = resourceResolver.getResources(basePath);
        GroovyScriptEngine gse = new GroovyScriptEngine(resources[0].getFile().getAbsolutePath());
        // Binding binding = new Binding();
        Class<?> classObj = gse.loadScriptByName(javaFile.toFile().getAbsolutePath());

        // Class<?> classObj = loader.parseClass(javaFile.toFile());
        List<Object> objectsList = new ArrayList<Object>();

        // for (Class<?> classDef : loader.getLoadedClasses()) {
        for (Class<?> classDef : ((GroovyClassLoader) classObj.getClassLoader()).getLoadedClasses()) {
            objectsList.add(classDef.getDeclaredConstructor().newInstance());
        }

        if (objectsList.isEmpty()) {
            objectsList.add(classObj.getDeclaredConstructor().newInstance());
        }

        return objectsList;
    }

    //Due to security reason commenting this code of executing the standalone script.
    /*private Object runGroovyInShell(Path javaFile) throws IOException {
        Script script = shell.parse(javaFile.toFile());
        return script.run(); // Groovy with implicite or explicite main method implementation.
                      // Standalone groovy script with Filename format *Main.groovy
    }*/

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

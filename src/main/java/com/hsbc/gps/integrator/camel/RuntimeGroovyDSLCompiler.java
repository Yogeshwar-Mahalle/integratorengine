package com.hsbc.gps.integrator.camel;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.FailedToCreateRouteException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.model.rest.RestsDefinition;
import org.apache.camel.model.rest.VerbDefinition;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.springframework.beans.factory.config.SingletonBeanRegistry;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.context.support.GenericWebApplicationContext;

import groovy.lang.GroovyClassLoader;
import groovy.util.GroovyScriptEngine;
import groovy.util.ResourceException;
import groovy.util.ScriptException;

@Component
@Configuration
public class RuntimeGroovyDSLCompiler extends RuntimeCamelDSLLoader {

    private final CompilerConfiguration compilerConfiguration;
    private final GroovyClassLoader loader;
    // private final GroovyShell shell;

    public RuntimeGroovyDSLCompiler() {
        compilerConfiguration = new CompilerConfiguration();
        compilerConfiguration.setSourceEncoding(CompilerConfiguration.DEFAULT_SOURCE_ENCODING);
        compilerConfiguration.setTargetBytecode(CompilerConfiguration.JDK8);
        loader = new GroovyClassLoader(this.getClass().getClassLoader(), compilerConfiguration);
        // shell = new GroovyShell(loader, new Binding(), compilerConfiguration);
    }

    @Scheduled(fixedDelay = 5000, initialDelay = 1000)
    private void loadGroovyDSLRoutes() {

        System.out.println(
                "****************** RuntimeGroovyDSLCompiler.loadGroovyDSLRoutes() method called *************************");
        checkAndLoadFile();
    }

    public void refreshRoutes(String dslPath) {
        System.out.println("RuntimeGroovyDSLCompiler.refreshRoutes() method called.");
        try {
            Resource[] groovyResources;
            boolean bSingleFileRefresh = false;
            if (dslPath.toUpperCase().endsWith(".GROOVY")) {
                groovyResources = resourceResolver.getResources(dslPath);
                bSingleFileRefresh = true;
            } else {
                groovyResources = resourceResolver.getResources(dslPath + "/**/*.groovy");
            }

            List<RouteBuilder> routesList = new ArrayList<RouteBuilder>();
            for (Resource resource : groovyResources) {

                System.out.println("Route File Name : " + resource.getFilename());
                if (resource.getFile().canRead() && resource.getFile().length() > 0) {

                    // Due to security reason commenting this code of executing the standalone
                    // script.
                    /*
                     * if (resource.getFilename().endsWith("Main.groovy")) { Object objScript =
                     * runGroovyInShell(resource.getFile().toPath());
                     * System.out.println("Groovy Main Script file running result : " + objScript);
                     * } else
                     */
                    {
                        List<Object> objectsList = loadGroovyScript(resource.getFile().toPath());

                        for (Object obj : objectsList) {
                            System.out.println("Groovy object type : " + obj.getClass().getCanonicalName());

                            if (obj instanceof RouteBuilder) {
                                routesList.add((RouteBuilder) obj);
                            } else {

                                Object integratorDefaultProcessorBean = camelContext.getRegistry().lookupByName("IntegratorDefaultProcessor");

                                if (integratorDefaultProcessorBean != null) { 
                                    //((BeanDefinitionRegistry) beanFactory).removeBeanDefinition("IntegratorDefaultProcessor");
                                    ((DefaultListableBeanFactory) beanFactory).destroySingleton("IntegratorDefaultProcessor"); //Remove instance
                                }

                                Object existingBean = camelContext.getRegistry()
                                        .lookupByName(obj.getClass().getCanonicalName());

                                if (existingBean != null) {

                                    System.out.println(
                                            "Bean reference already exist in the camel context registry. Replace the bean : "
                                                    + existingBean.getClass().getCanonicalName());
                                    
                                    ((DefaultListableBeanFactory) beanFactory).destroySingleton(obj.getClass().getCanonicalName()); //Remove instance
                                            
                                    //((BeanDefinitionRegistry) beanFactory).removeBeanDefinition(obj.getClass().getCanonicalName());
                                    //((GenericWebApplicationContext) applicationContext).removeBeanDefinition(obj.getClass().getCanonicalName());//Remove definition of bean

                                    //If Single file refresh in which existing bean is modified then need to refresh all routers which may refering the bean
                                    if(bSingleFileRefresh) {
                                        producerTemplate.sendBody("direct:IntegratorRoutesRefresh", "Refresh Routes");
                                        break;
                                    }
                                }

                                //((GenericWebApplicationContext) applicationContext).registerBean(obj.getClass().getCanonicalName(), obj.getClass(), obj);
                                //camelContext.getRegistry().bind(obj.getClass().getCanonicalName(), obj);
                                ((SingletonBeanRegistry) beanFactory).registerSingleton(obj.getClass().getCanonicalName(), obj);
                            }
                        }
                    }
                }
            }

            for (RouteBuilder routeBuilder : routesList) {

                final String routeId = routeBuilder.getClass().getSimpleName();
                System.out.println("Groovy route builder Id : " + routeId);

                RestsDefinition restsDefinition = routeBuilder.getRestCollection();
                if (restsDefinition != null) {
                    List<RestDefinition> restsDefList = restsDefinition.getRests();
                    for (RestDefinition restDef : restsDefList) {
                        System.out.println("Groovy DSL Rest Definition ID : " + restDef.getId());

                        if (!restDef.getId().isEmpty()) {
                            System.out.println("Groovy rest ID added :  " + restDef.getId());
                            List<VerbDefinition> verbsList = restDef.getVerbs();
                            List<VerbDefinition> newVerbs = new ArrayList<VerbDefinition>();
                            for (VerbDefinition verb : verbsList) {
                                if (!verb.getId().isEmpty()) {
                                    System.out.println("Groovy rest verb ID added :  " + verb.getId());
                                    newVerbs.add(verb);
                                } else {
                                    System.out
                                            .println("Groovy rest verb is ignored due to ID is missing :  "
                                                    + verb.getDescriptionText());
                                }
                            }
                            restDef.setVerbs(newVerbs);// Set new list of verbes with ID
                        } else {
                            System.out.println("Groovy rest is ignored due to ID is missing :  "
                                    + restDef.getDescriptionText());
                        }
                    }
                }

                boolean bTryRestart = false;
                int retryCount = 10;
                do {
                    try {
                        camelContext.addRoutes(routeBuilder);
                        bTryRestart = false;
                    } catch (FailedToCreateRouteException routeEx) {

                        String exCause = routeEx.getMessage();
                        System.out.println(exCause);
                        String errString = "No bean could be found in the registry for: ";
                        exCause = exCause.substring(exCause.indexOf(errString) + errString.length(),
                                exCause.length());
                        exCause = exCause.substring(0, exCause.indexOf(" of type:"));
                        // Create the bean or object depends on the property file
                        ((GenericWebApplicationContext) applicationContext).registerBean(exCause,
                                IntegratorDefaultProcessor.class, () -> new IntegratorDefaultProcessor());

                        bTryRestart = true;
                        --retryCount;
                    }
                } while (bTryRestart && retryCount > 0);
            }

        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    private List<Object> loadGroovyScript(Path javaFile)
            throws IllegalAccessException, InstantiationException, IOException, IllegalArgumentException,
            InvocationTargetException, NoSuchMethodException, SecurityException, ResourceException, ScriptException {

        loader.clearCache(); // Clear previous loaded cache

        // Binding binding = new Binding();
        Resource resource = resourceResolver.getResource(integratorfeed);
        GroovyScriptEngine groovyScriptEngine = new GroovyScriptEngine(resource.getFile().getAbsolutePath());
        groovyScriptEngine.setConfig(compilerConfiguration);
        
        Class<?> classObj = groovyScriptEngine.loadScriptByName(javaFile.toFile().getAbsolutePath());
        // Class<?> classObj = loader.parseClass(javaFile.toFile());

        List<Object> objectsList = new ArrayList<Object>();

        // for (Class<?> classDef : loader.getLoadedClasses()) {
        for (Class<?> classDef : ((GroovyClassLoader) classObj.getClassLoader()).getLoadedClasses()) {
            System.out.println("Groovy Class loading :  " + classDef.getCanonicalName());
            if( classDef.getCanonicalName() != null ) {
                objectsList.add(classDef.getDeclaredConstructor().newInstance());
            }
        }

        if (objectsList.isEmpty()) {
            objectsList.add(classObj.getDeclaredConstructor().newInstance());
        }

        return objectsList;
    }

    // Due to security reason commenting this code of executing the standalone
    // script.
    /*
     * private Object runGroovyInShell(Path javaFile) throws IOException { Script
     * script = shell.parse(javaFile.toFile()); return script.run(); // Groovy with
     * implicite or explicite main method implementation. // Standalone groovy
     * script with Filename format *Main.groovy }
     */

}

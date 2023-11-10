package com.ybm.integrator.camel;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.PostConstruct;

import com.ybm.integrator.IntegratorEngineApplication;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

@Configuration
public abstract class RuntimeCamelDSLLoader implements Processor {

    private final Lock mutex = new ReentrantLock();

    @Autowired
    protected ConfigurableApplicationContext applicationContext;

    @Autowired
    protected CamelContext camelContext;

    @Autowired
    protected ResourcePatternResolver resourceResolver;

    @Value("${integratorengine.feed}")
    protected String integratorfeed;

    @Autowired
    protected ProducerTemplate producerTemplate;

    private WatchService watcher;
    private Map<WatchKey, Path> watchKeys;
    protected BeanDefinitionRegistry beanFactory;

    abstract protected void refreshRoutes(String dslPath);

    RuntimeCamelDSLLoader() {
        watchKeys = new HashMap<WatchKey, Path>();
        this.resourceResolver = resourceResolver == null ? new PathMatchingResourcePatternResolver() : resourceResolver;
    }

    @Autowired
    RuntimeCamelDSLLoader(ResourcePatternResolver resourceResolver) {
        watchKeys = new HashMap<WatchKey, Path>();
        this.resourceResolver = resourceResolver == null ? new PathMatchingResourcePatternResolver() : resourceResolver;
    }

    @PostConstruct
    public void init() {
        try {
            Resource routesFolder = resourceResolver.getResource(integratorfeed);
            beanFactory = (BeanDefinitionRegistry) applicationContext.getAutowireCapableBeanFactory();
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
        System.out.println("RuntimeCamelDSLLoader.process() method called.");
        refreshRoutes(integratorfeed);
    }

    public void registerDirectory(Path dir) throws IOException {
        WatchKey key = dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
        watchKeys.put(key, dir);
    }

    public void walkAndRegisterDirectories(final Path start) throws IOException {
        // register directory and sub-directories
        Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                registerDirectory(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public void checkAndLoadFile() {
        try {

            WatchKey watchKey = watcher.poll();// take();
            if (watchKey != null) {
                Path dir = watchKeys.get(watchKey);
                if (dir == null) {
                    System.err.println("WatchKey not recognized!!");
                } else {
                    for (WatchEvent<?> evt : watchKey.pollEvents()) {
                        System.out.printf(
                                "********************************************************************************************\n");
                        System.out.printf("  Event kind : %s  Event Count : %d   File Name : %s\n", evt.kind(),
                                evt.count(), evt.context());
                        System.out.printf(
                                "********************************************************************************************\n");

                        Path evtPath = (Path) evt.context();
                        Path fullPath = dir.resolve(evtPath);
                        if (Files.isDirectory(fullPath)) {
                            walkAndRegisterDirectories(fullPath);
                        }

                        if ((evtPath.toString().toUpperCase().endsWith(".XML")
                                && (this instanceof RuntimeXMLDSLBuilder))
                                || (evtPath.toString().toUpperCase().endsWith(".GROOVY")
                                        && (this instanceof RuntimeGroovyDSLCompiler))) {
                            if (evt.kind() == ENTRY_DELETE) {
                                try {
                                    mutex.lock();
                                    IntegratorEngineApplication.restartApplication(); // Restart the application to
                                                                                      // fresh load everything and get
                                                                                      // cleaned routes of deleted file
                                } finally {
                                    mutex.unlock();
                                }
                            } else if (evt.kind() == ENTRY_MODIFY) {
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
            }

        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }
}

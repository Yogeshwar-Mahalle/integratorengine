package com.hsbc.gps.integrator.camel;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

@Component
public class IntegratorRoutesRefresh extends RouteBuilder {

    @Override
    public void configure() throws Exception {

        boolean startupRoute = true;
        from("direct:IntegratorRoutesRefresh").routeId("IntegratorRoutesRefreshID")
                .autoStartup(startupRoute)
                .process("runtimeXMLDSLBuilder")
                .process("runtimeGroovyDSLCompiler")
                .to("log:All XML and Java DSL files are reloaded");

    }

}

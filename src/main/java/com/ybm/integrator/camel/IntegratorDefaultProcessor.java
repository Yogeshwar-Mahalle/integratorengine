package com.ybm.integrator.camel;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.stereotype.Component;

@Component
public class IntegratorDefaultProcessor implements Processor {
    @Override
    public void process(Exchange exchange) throws Exception {
        System.out.println( "****************************************************************************************************************" );
        System.out.println( "******** IntegratorDefaultProcessor is called due to Processor bean used in the route is not provided **********" );
        System.out.println( "****************************************************************************************************************" );
    }
}

package com.hsbc.gps.integrator.controller;

import java.util.Map;

import org.apache.camel.ExchangePattern;
import org.apache.camel.ProducerTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/fusion-pil")
@RestController
public class RestApiIntegratorEngineController {

    @Autowired
    ProducerTemplate camelConnector;
    
    @PostMapping(value="/{target-sys}/{request-type}" )
    @ResponseBody
    public Object forwardToCamelRouter( @PathVariable("target-sys") String targetSys, 
                                        @PathVariable("request-type") String requesType,
                                        @RequestHeader Map<String, Object> headers,
                                        @RequestBody String payload ) {

        System.out.println( "============================================================");
        System.out.println( "Request received : "); 
        System.out.println( "============================================================");
        System.out.println( "Headers : " + headers );
        System.out.println( "Payload : " + payload );
        System.out.println( "Target System : " + targetSys );
        System.out.println( "Request Type : " + requesType );
        System.out.println( "============================================================");

        String camelEndpoint = "direct://" + targetSys + "-" + requesType;
        return camelConnector.sendBodyAndHeaders(camelEndpoint, ExchangePattern.InOut, payload, headers);
    }
}

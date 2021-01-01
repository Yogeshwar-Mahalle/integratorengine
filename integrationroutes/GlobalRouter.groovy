
//@Grab('org.apache.camel.springboot:camel-spring-boot-starter:3.7.0')

import org.apache.camel.*
import org.apache.camel.impl.*
import org.apache.camel.builder.*
import org.apache.camel.model.rest.*
import org.springframework.http.*


class GlobalRouter extends RouteBuilder {
    def void configure() {

        from("direct://fusion-pil-status").routeId("GlobalGroovyRouter-fusion-pil-status")
            .log(LoggingLevel.INFO, "Request received in GlobalRouter : ${body()}")
            .process("GlobalProcessor")
            //.transform().constant("Response from GlobalRouter")

    }
}

import org.apache.camel.*
import org.apache.camel.impl.*
import org.apache.camel.builder.*


public class acclookupRouter extends RouteBuilder {

    def void configure() {

        from("direct://acclookup-check").routeId("GlobalGroovyRouter-acclookup-check")
            .log(LoggingLevel.INFO, "Request received in Account Lookup : #{body}")
            .process("acclookupProcessor")
            //.transform().constant("Response from Account Lookup")
    }
}

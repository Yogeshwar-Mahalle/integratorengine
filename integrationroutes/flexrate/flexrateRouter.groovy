import org.apache.camel.*
import org.apache.camel.impl.*
import org.apache.camel.builder.*


public class flexrateRouter extends RouteBuilder {

    def void configure() {

        from("direct://flexrate-spotrate").routeId("GlobalGroovyRouter-flexrate-spotrate")
            .log(LoggingLevel.INFO, "Request received in Flexrate : #{body}")
            .process("flexrateProcessor")
            //.transform().constant("Response from FlexRate")

    }
}

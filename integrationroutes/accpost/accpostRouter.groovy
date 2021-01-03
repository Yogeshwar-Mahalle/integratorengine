import org.apache.camel.*
import org.apache.camel.impl.*
import org.apache.camel.builder.*


public class accpostRouter extends RouteBuilder {

    def void configure() {

        from("direct://accpost-pnp").routeId("accpostRouter-accpost-pnp")
            .log(LoggingLevel.INFO, "Request received in Account Posting : #{body}")
            .process("accpostProcessor")
            //.transform().constant("Response from Account Posting")
    }
}

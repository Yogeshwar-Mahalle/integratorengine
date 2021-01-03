import org.apache.camel.*
import org.apache.camel.impl.*
import org.apache.camel.builder.*


public class balinqRouter extends RouteBuilder {

    def void configure() {

        from("direct://balinq-earmark").routeId("balinqRouter-balinq-earmark")
            .log(LoggingLevel.INFO, "Request received in Balance Inquiry : #{body}")
            .process("balinqProcessor")
            //.transform().constant("Response from Balance Inquiry")
    }
}

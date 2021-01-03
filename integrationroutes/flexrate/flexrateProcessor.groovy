import org.apache.camel.Exchange;
import org.apache.camel.Processor;


public class flexrateProcessor implements Processor {

    public void process(Exchange exchange) throws Exception {

        println "*********************************************************"
        println "******** I am from flexrateProcessor version-1 **********"
        println "*********************************************************"

        String contentType = exchange.getIn().getHeader(Exchange.CONTENT_TYPE, String.class);
        String path = exchange.getIn().getHeader(Exchange.HTTP_URI, String.class);
        //path = path.substring(path.lastIndexOf("/"));

        exchange.getOut().setHeader(Exchange.CONTENT_TYPE, contentType + "; charset=UTF-8");
        exchange.getOut().setHeader("PATH", path);
        exchange.getOut().setBody("Response from FlexRate.");
    }
}


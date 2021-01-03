import org.apache.camel.Exchange;
import org.apache.camel.Processor;


public class GlobalProcessor implements Processor {

    public void process(Exchange exchange) throws Exception {

        String contentType = exchange.getIn().getHeader(Exchange.CONTENT_TYPE, String.class);
        String path = exchange.getIn().getHeader(Exchange.HTTP_URI, String.class);
        //path = path.substring(path.lastIndexOf("/"));

        //assertEquals(Exchange.CONTENT_TYPE, contentType, "Get a wrong content type");
        // assert camel http header
        String charsetEncoding = exchange.getIn().getHeader(Exchange.HTTP_CHARACTER_ENCODING, String.class);
        //assertEquals(charsetEncoding, "Get a wrong charset name from the message header", "UTF-8");
        // assert exchange charset
        //assertEquals(exchange.getProperty(Exchange.CHARSET_NAME), "Get a wrong charset naem from the exchange property", "UTF-8");
        exchange.getOut().setHeader(Exchange.CONTENT_TYPE, contentType + "; charset=UTF-8");
        exchange.getOut().setHeader("PATH", path);
        exchange.getOut().setBody("Response from GlobalProcessor.  ${exchange.getIn().getBody().getAt('text')}");

    }
}

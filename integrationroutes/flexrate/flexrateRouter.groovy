import org.apache.camel.*
import org.apache.camel.impl.*
import org.apache.camel.builder.*
import javax.xml.bind.*
import java.util.*
import org.apache.camel.component.jackson.*
import org.apache.camel.converter.jaxb.*
import org.apache.camel.model.dataformat.*
import javax.xml.bind.annotation.*


public class flexrateRouter extends RouteBuilder {

    def void configure() {


        from("direct://flexrate-spotrate").routeId("flexrateRouter-flexrate-spotrate")
            .log(LoggingLevel.INFO, "Request received in Flexrate : ${body()}")
            .process("flexrateProcessor")
            //.transform().constant("Response from FlexRate")


        // XML Data Format
        JaxbDataFormat xmlDataFormat = new JaxbDataFormat()
        JAXBContext con = JAXBContext.newInstance(Employee.class)
        xmlDataFormat.setContext(con)

        // JSON Data Format
        JacksonDataFormat jsonDataFormat = new JacksonDataFormat(Employee.class)
        //XmlJsonDataFormat xmlJsonFormat = new XmlJsonDataFormat();
        
        from("direct://flexrate-dealrate").routeId("flexrateRouter-flexrate-dealrate")
            .log(LoggingLevel.INFO, "Request received in Flexrate : ${body()}")
            .doTry().unmarshal(xmlDataFormat)
            .log(LoggingLevel.INFO, "After unmarshalling XML to Java in Flexrate : ${body()}")
            .process(new Processor() {
                public void process(Exchange exchange) throws Exception {
                    Employee employee = exchange.getIn().getBody(Employee.class)
                    employee.setEmpName("Yogeshwar Mahalle")
                    exchange.getIn().setBody(employee)
                }
            })
            .log(LoggingLevel.INFO, "After Processing in Flexrate : ${body()}")
            .marshal(jsonDataFormat)
            .log(LoggingLevel.INFO, "After Java to Json Marshling in Flexrate : ${body()}")
            .unmarshal(jsonDataFormat)
            .log(LoggingLevel.INFO, "After Json to Java Unmarshling in Flexrate : ${body()}")
            //.marshal(xmlJsonFormat)
            .marshal(xmlDataFormat)
            .log(LoggingLevel.INFO, "After Java to XML Marshling in Flexrate : ${body()}")
            .setBody(xpath("//employee/empName/text()", String.class))
            .log(LoggingLevel.INFO, "After XPATH in Flexrate : ${body()}")
            .bean(ByeMessage.class, "doSomething")
            .log(LoggingLevel.INFO, "After Bean method call in Flexrate : ${body()}")
            .to("mock:flexrateapi")
            .doCatch(Exception.class)
            .process( new Processor() {
                public void process(Exchange exchange) throws Exception {
                    Exception cause = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class)
                    println cause
                }
            }) 

    }
}

public class ByeMessage {
     public void doSomething(Exchange exchange) {
         String str = "Bye " + exchange.getIn().getBody()
         exchange.getIn().setBody(str)
    }
}

@XmlRootElement(name = "employee")
@XmlAccessorType(XmlAccessType.FIELD)
public class Employee {

    private String empName
    private int empId

    public String getEmpName() {
        return empName
    }

    public void setEmpName(String empName) {
        this.empName = empName
    }

    public int getEmpId() {
        return empId
    }

    public void setEmpId(int empId) {
        this.empId = empId
    }
}

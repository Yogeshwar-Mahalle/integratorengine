FROM openjdk:8-jre

WORKDIR /app

COPY target/integratorengine-*.jar integratorengine.jar

EXPOSE 8080

CMD [ "java", "-jar", "integratorengine.jar" ]

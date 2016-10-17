FROM maven:3-jdk-8

WORKDIR /opt/sch
ADD . /opt/sch
RUN mvn clean package

# TODO: This is bogus. This is simply to be used for Registrator registration.
EXPOSE 65000

CMD ["java", "-jar", "/opt/sch/target/dcae-service-change-handler.jar", "prod", "http://consul:8500/v1/kv/service-change-handler?raw=true"]

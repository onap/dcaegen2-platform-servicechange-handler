FROM maven:3-jdk-8
RUN apt-get update && apt-get install -y sendmail

WORKDIR /opt/sch
ADD . /opt/sch
RUN mvn clean package

# TODO: This is bogus. This is simply to be used for Registrator registration.
EXPOSE 65000

COPY startSCH.sh /opt/sch
RUN chmod +x /opt/sch/startSCH.sh
CMD ["/opt/sch/startSCH.sh"]

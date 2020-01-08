FROM openjdk:13

COPY kafka-manager-2.0.0.2.zip /opt

RUN yum -y install unzip
RUN cd /opt && unzip kafka-manager-2.0.0.2.zip

CMD /opt/kafka-manager-2.0.0.2/bin/kafka-manager
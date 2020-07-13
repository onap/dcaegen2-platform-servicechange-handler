#!/bin/bash

grep "^nameserver" /opt/onap/etc/resolv.conf >> /etc/resolv.conf

service sendmail start

java -Dlogback.configurationFile=logback.xml -jar /opt/onap/target/dcae-service-change-handler.jar prod http://consul:8500/v1/kv/service-change-handler?raw=true


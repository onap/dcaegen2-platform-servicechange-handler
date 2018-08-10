#!/bin/bash

grep "^nameserver" /opt/sch/etc/resolv.conf >> /etc/resolv.conf

service sendmail start

java -Dlogback.configurationFile=logback.xml -jar /opt/sch/target/dcae-service-change-handler.jar prod http://consul:8500/v1/kv/service-change-handler?raw=true


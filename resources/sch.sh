#!/bin/bash
# ================================================================================
# Copyright (c) 2017-2020 AT&T Intellectual Property. All rights reserved.
# ================================================================================
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
# ============LICENSE_END=========================================================
#
# ECOMP is a trademark and service mark of AT&T Intellectual Property.

# The following variables are checked if set, if not set then an error is raised
# PATH_TO_CACERT is the full file path to the cacert that must be added to the
# existing keystore
if [ -z "$PATH_TO_CACERT" ]; then
    # TODO: Make this variable not required and thus not do the keytool call
    echo "Missing required environment variable: PATH_TO_CACERT"
    echo "Please set this variable to the full local path of the CA cert pem file that is to be added"
    echo "Example: PATH_TO_CACERT=/opt/cert/cacert.pem"
    exit 1
fi

# SCH_ARGS are all the args to be passed into the SCH java run command
if [ -z "$SCH_ARGS" ]; then
    echo "Missing required environment variable: SCH_ARGS"
    echo "Please set this variable to the command-line args to be used to run service change handler"
    echo "Example: SCH_ARGS=prod /opt/config.json"
    echo "Example: SCH_ARGS=prod http://consul:8500/v1/kv/service-change-handler?raw=true"
    exit 1
fi

# Add the cacert to validate inventory's cert to support TLS.  This command is
# allowed to fail when there is no need for https.
# NOTE: This user must have permission to write to /usr/local/openjdk-11/lib/security/cacerts
keytool -importcert -file $PATH_TO_CACERT -cacerts -alias "inventory" -noprompt -storepass changeit

# Now launch SCH
java -jar /opt/servicechange-handler.jar $SCH_ARGS

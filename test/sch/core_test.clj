; ============LICENSE_START=======================================================
; org.onap.dcae
; ================================================================================
; Copyright (c) 2017-2018 AT&T Intellectual Property. All rights reserved.
; ================================================================================
; Licensed under the Apache License, Version 2.0 (the "License");
; you may not use this file except in compliance with the License.
; You may obtain a copy of the License at
;
;      http://www.apache.org/licenses/LICENSE-2.0
;
; Unless required by applicable law or agreed to in writing, software
; distributed under the License is distributed on an "AS IS" BASIS,
; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
; See the License for the specific language governing permissions and
; limitations under the License.
; ============LICENSE_END=========================================================
;
; ECOMP is a trademark and service mark of AT&T Intellectual Property.

(ns sch.core-test
  (:use (clojure test))
  (:require [sch.core :refer [create-distribution-client-config deploy-artifacts-ex!]]
            [clj-fakes.core :as f])
  (:import (org.onap.sdc.utils DistributionStatusEnum))
  )


(deftest test-deploy-artifacts-ex
  (letfn [(deploy-artifacts-echo [to-post posted to-delete deleted inventory-uri
                             service-metadata requests]
            to-post posted to-delete deleted)
          (send-dist-status-only-ok [artifact status]
            (if (not= (. DistributionStatusEnum ALREADY_DEPLOYED) status)
              (throw (Exception. "Distribution status should be ALREADY DEPLOYED"))
              ))
          (send-comp-done-status-only-ok [msg artifact status]
            (if (not= (. DistributionStatusEnum COMPONENT_DONE_OK) status)
              (throw (Exception. "Distribution status should be COMPONENT DONE OK"))
              ))]
    (let [service-metadata [{:resources [{:resourceInvariantUUID "123"
                                          :artifacts [:artifactName "type-foo"]
                                          }]}]
          requests [{:asdcResourceId "123" :typeName "type-foo"}]
          deploy-artifacts (partial deploy-artifacts-echo requests [] [] [])
          ]
      (f/with-fakes
        (f/patch! #'sch.handle/deploy-artifacts! deploy-artifacts)
        (is (= nil (deploy-artifacts-ex! "http://inventory" service-metadata requests
                                         send-dist-status-only-ok send-comp-done-status-only-ok "" ""))))
      )))


(deftest test-create-distribution-client-config
  (let [config { :asdcDistributionClient { :environmentName "ONAP-AMDOCS"
                                           :asdcAddress "10.0.3.1:8443"
                                           :msgBusAddress "message-router-hostname"
                                           :keyStorePassword nil
                                           :pollingInterval 20
                                           :consumerGroup "dcae"
                                           :asdcUri "https://10.0.3.1:8443"
                                           :consumerId "dcae-sch"
                                           :pollingTimeout 20
                                           :user "dcae"
                                           :keyStorePath nil
                                           :password "some-password"
                                           :isFilterInEmptyResources false
                                           :activateServerTLSAuth false
                                           :useHttpsWithDmaap true }}

        dcc (create-distribution-client-config config)
        ]
    (is (= (. dcc isUseHttpsWithDmaap) true))
    (is (= (. dcc getMsgBusAddress) ["message-router-hostname"]))
    (is (= (. dcc isConsumeProduceStatusTopic) false))
    )
  )

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

(ns sch.asdc-client-test
  (:use (clojure test))
  (:require [sch.asdc-client :as ac])
  )


(deftest test-create-asdc-conn
  (let [config-good { :asdcUri "https://asdc-please-work:8443"
                      :user "foo-user"
                      :password "foo-password"
                      :consumerId "foo-id"
                      :activateServerTLSAuth true }
        actual (ac/create-asdc-conn { :asdcDistributionClient config-good })
        [uri user password consumer-id insecure?] actual
        ]
    (is (= (str uri) (:asdcUri config-good)))
    (is (= user (:user config-good)))
    (is (= password (:password config-good)))
    (is (= consumer-id (:consumerId config-good)))
    (is (= insecure? (not (:activateServerTLSAuth config-good))))
    ))


(deftest test-get-consumer-id
  (let [consumer-id "SOME-CONSUMER-ID"
        asdc-conn [nil nil nil consumer-id nil]]
    (is (= consumer-id (ac/get-consumer-id asdc-conn)))
    ))


(deftest test-construct-service-path
  (let [service-uuid "abc123"]
    (is (= "/sdc/v1/catalog/services/abc123/metadata"
           (ac/construct-service-path service-uuid)))
    ))

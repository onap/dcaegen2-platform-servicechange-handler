; ============LICENSE_START=======================================================
; org.onap.dcae
; ================================================================================
; Copyright (c) 2017 AT&T Intellectual Property. All rights reserved.
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

(ns sch.parse-test
  (:use (clojure test))
  (:require [cheshire.core :refer [parse-stream]]
            [sch.parse :refer :all])
  )


; TODO: May want to use fixtures..
(defn read-change-event!
  [event-file-path]
  (with-open [rdr (clojure.java.io/reader event-file-path)]
    (parse-stream rdr true))
  )

(deftest generate-test
  (letfn [(get-blueprint [artifact-url]
            "Fake blueprint")]
    (let [change-event (read-change-event! "fixtures/4_insert.json")
          get-locations (partial get-service-locations slurp)
          service-types (generate-dcae-service-type-requests get-blueprint
                                                             get-locations
                                                             change-event)
          expected-st { :asdcResourceId "3d5927fc-a28e-41e9-9e79-57289aa7f754",
                        :asdcServiceId "9eaf59ee-2fe0-48a9-8d20-6f9b09ba807b",
                        :owner "MICHAEL SHITRIT",
                        :typeName "sample-blueprint",
                        :typeVersion 2,
                        :blueprintTemplate "Fake blueprint",
                        :asdcServiceURL "/sdc/v1/catalog/services/9eaf59ee-2fe0-48a9-8d20-6f9b09ba807b/metadata"
                        :serviceLocations ["CLLI1" "CLL2"] }
          ]

      (is (= (count service-types) 1))
      (is (= expected-st (first service-types)))
      )))

(deftest pick-out-artifact-test
  (let [service-metadata (read-change-event! "fixtures/4_insert.json")
        stub-request { :asdcResourceId "3d5927fc-a28e-41e9-9e79-57289aa7f754"
                       :typeName "sample-blueprint" }
        actual-artifact (pick-out-artifact service-metadata stub-request)]
    (is (= (:artifactName actual-artifact) (:typeName stub-request)))
    ))

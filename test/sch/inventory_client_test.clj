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

(ns sch.inventory-client-test
  (:use (clojure test))
  (:require [sch.inventory-client :as ic]
            [cheshire.core :refer [generate-string]])
  )


(deftest test-create-inventory-conn
  (let [inventory-uri "http://inventory"]
    (is (= inventory-uri (str (ic/create-inventory-conn {:dcaeInventoryClient {:uri inventory-uri}}))))
    ))


(deftest append-params
  (let [append-params #'sch.inventory-client/append-params]
  (is (= "http://inventory?some-field=some-value" (append-params "http://inventory" ["some-field" "some-value"])))
  ))


(deftest test-get-service-types
  (letfn [(fake-get [result url request]
            {:status 200 :body result})]
    (let [results {:items [{:typeId "123"}]}
          conn (ic/create-inventory-conn "http://inventory")
          fake-get-success (partial fake-get (generate-string results))
          nada (intern 'clj-http.client 'get fake-get-success)]
      (is (= (:items results) (ic/get-service-types! conn [])))
      )))


(deftest test-post-service-types
  (letfn [(fake-post [result url request]
            {:status 200 :body result})]
    (let [result {:typeId "123"}
          conn (ic/create-inventory-conn "http://inventory")
          fake-post-success (partial fake-post (generate-string result))
          nada (intern 'clj-http.client 'post fake-post-success)]
      (is (= result (ic/post-service-type! conn {})))
      )))

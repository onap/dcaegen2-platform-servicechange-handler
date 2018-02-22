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

(ns sch.handle-test
  (:use (clojure test))
  (:require [cheshire.core :refer [parse-stream]]
            [sch.handle :refer :all])
  )


(deftest test-should-update
  (is (= false (should-update? {:typeVersion 2} {:typeVersion 3})))
  (is (= true (should-update? {:typeVersion 3} {:typeVersion 2})))
  )


(deftest test-should-insert
  (is (= true (should-insert? {} nil)))
  (is (= true (should-insert? {} {})))
  (is (= false (should-insert? {} {:somekey "yo"})))
  )


(deftest test-find-service-types-to-post
  (letfn [(fake-get-service-types [result inventory-uri query-params]
            result)]
    (let [find-service-types-to-post #'sch.handle/find-service-types-to-post!
          service-type-requests [{:typeName "some-type" :asdcServiceId "abc"
                                  :asdcResourceId "123" :typeVersion 3}]
          fake-get-service-types-insert (partial fake-get-service-types {})
          nada (intern 'sch.inventory-client 'get-service-types! fake-get-service-types-insert)]
      (is (= service-type-requests (find-service-types-to-post "http://inventory"
                                                               service-type-requests)))
      )))


(deftest test-post-service-types
  (letfn [(fake-post-service-type [inventory-uri request]
            (assoc request :typeId "123"))]
    (let [service-type-requests [{:typeName "some-type" :asdcServiceId "abc"
                                  :asdcResourceId "123" :typeVersion 3}]
          post-service-types #'sch.handle/post-service-types!
          nada (intern 'sch.inventory-client 'post-service-type! fake-post-service-type)]
      (is (= {:typeId "123" :typeName "some-type" :asdcServiceId "abc"
              :asdcResourceId "123" :typeVersion 3}
             (first (post-service-types "http://inventory" service-type-requests))))
      )))


(deftest test-find-service-types-to-delete
  (letfn [(fake-get-service-types [result inventory-uri query-params]
            result)]
    (let [find-service-types-to-delete #'sch.handle/find-service-types-to-delete!
          service-type-requests [{:typeName "some-type" :asdcServiceId "abc"
                                  :asdcResourceId "123" :typeVersion 3}]
          fake-get-service-types-delete (partial fake-get-service-types
                                                 [{ :typeName "some-type"
                                                   :asdcServiceId "abc"
                                                   :asdcResourceId "456"
                                                   :typeVersion 3 }])
          nada (intern 'sch.inventory-client 'get-service-types!
                       fake-get-service-types-delete)]
      (is (= 1 (count (find-service-types-to-delete "http://inventory" "abc"
                                                    service-type-requests))))
      )))


(deftest test-delete-service-types
  (letfn [(fake-delete-service-type [inventory-uri type-id]
            type-id)]
    (let [service-type-requests [{:typeId "def" :typeName "some-type"
                                  :asdcServiceId "abc" :asdcResourceId "123" :typeVersion 3}]
          delete-service-types #'sch.handle/delete-service-types!
          nada (intern 'sch.inventory-client 'delete-service-type! fake-delete-service-type)]
      (is (= "def" (first (delete-service-types "http://inventory" service-type-requests))))
      )))


(deftest deployed-funcs-test
  (let [requests [{:asdcResourceId "123" :typeName "pizza"}
                  {:asdcResourceId "456" :typeName "hamburger"}
                  {:asdcResourceId "789" :typeName "hotdog"}]
        attempted [{:asdcResourceId "456" :typeName "hamburger"}
                   {:asdcResourceId "789" :typeName "hotdog"}]
        completed [{:asdcResourceId "789" :typeName "hotdog"}]
        ]
    (is (= (deployed-ok attempted completed) completed))
    (is (= (deployed-error attempted completed) [(first attempted)]))
    (is (= (deployed-already requests attempted) [(first requests)]))
    ))

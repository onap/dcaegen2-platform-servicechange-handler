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

(ns sch.inventory-client
  (:require [clj-http.client :as client]
            [clojure.tools.logging :as logger :refer [error]]
            [cheshire.core :refer [parse-string]]
            [org.bovinegenius.exploding-fish :refer [uri param]])
  (:gen-class))


(defn create-inventory-conn
  [config]
  (uri (get-in config [:dcaeInventoryClient :uri])))

; HTTP client to DCAE inventory

(defn- append-params
  "Appends arbitrary list of parameter pairs to a URI
 
  `params` must be in the form [*field name* *value* ...]

  Returns the updated URI"
  [uri params]
  (let [[field value & more-params] params
        uri-updated (param uri field value)]
    (if more-params
      (append-params uri-updated more-params)
      uri-updated)))


(defn get-service-types!
  "GET DCAE service types from inventory

  TODO: Now its generic, how to put checks?"
  [inventory-uri query-params]
  (let [path "/dcae-service-types"
        inventory-uri (append-params (assoc inventory-uri :path path)
                                     query-params)
        resp (client/get (str inventory-uri) { :content-type :json })]
    (if (= (:status resp) 200)
      (:items (parse-string (:body resp) true))
      (error (str "GET dcae-service-types failed: " (:status resp) ", " (:body resp)))
      )))


(defn post-service-type!
  [inventory-uri dcae-service-type]
  (let [resp (client/post (str (assoc inventory-uri :path "/dcae-service-types"))
               { :content-type :json
                 :form-params dcae-service-type })]
    (if (= (:status resp) 200)
      (parse-string (:body resp) true)
      (error (str "POST dcae-service-types failed: " (:status resp) ", " (:body resp)))
      )))


(defn delete-service-type!
  [inventory-uri service-type-id]
  (let [path (str "/dcae-service-types/" service-type-id)
        resp (client/delete (str (assoc inventory-uri :path path)))]
    (if (or (= (:status resp) 200) (= (:status resp) 410))
      service-type-id
      (error (str "DELETE dcae-service-types failed: " (:status resp) ", " (:body resp)))
      )))

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

(ns sch.asdc-client
  (:require [clj-http.client :as client]
            [taoensso.timbre :as timbre :refer [error]]
            [cheshire.core :refer [parse-string]]
            [org.bovinegenius.exploding-fish :refer [uri param]])
  (:gen-class))


(defn create-asdc-conn

  ([asdc-uri user password consumer-id]
   [(uri asdc-uri) user password consumer-id])

  ([config]
   (let [config-asdc (:asdcDistributionClient config)]
     (create-asdc-conn (:asdcUri config-asdc) (:user config-asdc)
                       (:password config-asdc) (:consumerId config-asdc))))
  )


(defn get-consumer-id
  [asdc-conn]
  (get asdc-conn 3))

(defn construct-service-path
  [service-uuid]
  (str "/asdc/v1/catalog/services/" service-uuid "/metadata"))


(defn get-artifact!
  [connection artifact-path]
  (let [[asdc-uri user password instance-id] connection
        target-uri (assoc asdc-uri :path artifact-path)
        resp (client/get (str target-uri) { :basic-auth [user password]
                                            :headers { "X-ECOMP-InstanceID" instance-id } })]
    (if (= (:status resp) 200)
      ; Response media type is application/octet-stream
      ; TODO: Use X-ECOMP-RequestID?
      (:body resp)
      (error (str "GET asdc artifact failed: " (:status resp) ", " (:body resp))))
    ))

(defn get-service-metadata!
  [connection service-uuid]
  (let [[asdc-uri user password instance-id] connection
        target-uri (assoc asdc-uri :path (construct-service-path service-uuid))
        resp (client/get (str target-uri) { :basic-auth [user password]
                                            :headers { "X-ECOMP-InstanceID" instance-id } })]
    (if (= (:status resp) 200)
      ; Response media type is application/octet-stream
      ; TODO: Use X-ECOMP-RequestID?
      (parse-string (:body resp) true)
      (error (str "GET asdc service metadata failed: " (:status resp) ", " (:body resp))))
    ))

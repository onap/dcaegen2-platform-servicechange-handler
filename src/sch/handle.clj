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

(ns sch.handle
  (:require [clojure.java.io :refer :all]
            [clojure.tools.logging :as logger :refer [info error]]
            [sch.inventory-client :refer [get-service-types! post-service-type!
                                          delete-service-type!]]
            [sch.asdc-client :refer [get-artifact!]]
            [sch.parse :refer [generate-dcae-service-type-requests
                               get-service-locations]]
            )
  (:import (org.onap.sdc.utils DistributionStatusEnum))
  (:gen-class))


; Handle the ASDC distribtuion notification of change events and take action

(defn should-update?
  [service-type-request service-type-prev]
  (if (< (:typeVersion service-type-prev) (:typeVersion service-type-request)) true false))

(defn should-insert?
  [service-type-request service-type-prev]
  (if (empty? service-type-prev) true false))

(defn- find-service-types-to-post!
  [inventory-uri service-type-requests]
  (letfn [(post? [service-type-request]
            (let [type-name (:typeName service-type-request)
                  asdc-service-id (:asdcServiceId service-type-request)
                  asdc-resource-id (:asdcResourceId service-type-request)
                  query-result (get-service-types! inventory-uri
                                                  ["typeName" type-name
                                                   "asdcServiceId" asdc-service-id
                                                   "asdcResourceId" asdc-resource-id
                                                   "onlyActive" false])
                  service-type-prev (first query-result)]

              (cond
                ; Unexpected error from the GET call to inventory
                (nil? query-result) (error "Unexpected error querying inventory")

                ; Insert and update actions are the same
                (or (should-insert? service-type-request service-type-prev)
                    (should-update? service-type-request service-type-prev)) true

                :else (info "Insert/update not needed: " (:typeName service-type-request))
              )))]

    (filter #(post? %1) service-type-requests)))

(defn- post-service-types!
  [inventory-uri service-type-requests]
  (letfn [(post [service-type-request]
            (let [service-type (post-service-type! inventory-uri service-type-request)
                  type-id (:typeId service-type)]
              (if service-type
                (do
                  (info (str "Inserted/updated new dcae service type: " type-id))
                  service-type)
                (error (str "Error inserting/updated new dcae service type: " type-id)))))]
    (remove nil? (map post service-type-requests))
    ))


(defn- find-service-types-to-delete!
  [inventory-uri service-id service-type-requests]
  (let [query-result (get-service-types! inventory-uri ["asdcServiceId" service-id])]

    (letfn [(gone? [resource-id type-name]
              (nil? (some #(and (= resource-id (:asdcResourceId %1))
                                (= type-name (:typeName %1)))
                          service-type-requests)))]

      (filter #(gone? (:asdcResourceId %1) (:typeName %1)) query-result)
      )))

(defn- delete-service-types!
  [inventory-uri service-type-requests]
  (letfn [(delete [service-type-request]
            (let [type-id (delete-service-type! inventory-uri (:typeId service-type-request))]
              (if type-id
                (do
                  (info (str "Deleted dcae service type: " type-id))
                  type-id)
                (error (str "Error deleting dcae service type: " type-id)))))]
    (remove nil? (map delete service-type-requests))
    ))


(defn download-artifacts!
  "Generates dcae service type requests from the service metadata"
  [inventory-uri asdc-conn service-metadata]
  (let [get-artifact-func (partial get-artifact! asdc-conn)
        get-locations-func (partial get-service-locations get-artifact-func)
        requests (generate-dcae-service-type-requests get-artifact-func
                                                      get-locations-func
                                                      service-metadata)]
    (info (str "Done downloading artifacts: " (count requests)))
    requests
    ))

(defn deploy-artifacts!
  "Takes action on dcae service types produced from the service metadata"
  [inventory-uri service-metadata requests]
  (let [service-id (:invariantUUID service-metadata)

        to-post (find-service-types-to-post! inventory-uri requests)
        to-delete (find-service-types-to-delete! inventory-uri service-id requests)
        
        posted (if (not-empty to-post) (post-service-types! inventory-uri to-post))
        deleted (if (not-empty to-delete) (delete-service-types! inventory-uri to-delete))

        stats (zipmap [ :num-requests :num-to-post :num-posted :num-to-delete :num-deleted]
                      (map count [requests to-post posted to-delete deleted]))
        ]

    (info (str "Done deploying artifacts: " stats))
    [to-post posted to-delete deleted]
    ))


(defn handle-change-event!
  "Convenience method that calls download-artifacts then deploy-artifacts
  
  This function takes a service metadata to:
  
  1. Generate dcae service type requests
  2. Posts dcae service type requests that are *new* or *updated*
  3. Deletes dcae service types that are no longer part of a (service, resource)"
  [inventory-uri asdc-conn service-metadata]
  (let [requests (download-artifacts! inventory-uri asdc-conn service-metadata)]
    (deploy-artifacts! inventory-uri service-metadata requests)))

; Classify the outputs from the deploy and download calls

(defn- filtering-lists
  [filter-func requests results]
  (letfn [(success? [request]
              (true? (some #(and (= (:asdcResourceId request) (:asdcResourceId %))
                                 (= (:typeName request) (:typeName %)))
                           results)))]
    (filter-func success? requests)))

; attempted-requests service-types
(def deployed-ok (partial filtering-lists filter))

; attempted-requests service-types
(def deployed-error (partial filtering-lists remove))

; requests attempted-requests
(def deployed-already (partial filtering-lists remove))

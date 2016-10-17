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

(ns sch.parse
  (:require [clojure.java.io :refer :all]
            [taoensso.timbre :as timbre :refer [info error]]
            [sch.asdc-client :refer [construct-service-path]]
            [cheshire.core :refer [parse-string]]
            )
  (:import (org.openecomp.sdc.utils ArtifactTypeEnum))
  (:gen-class))


; Abstraction to parse the ASDC distribution notification of change events and 
; transforms them into DCAE service type requests

(defn get-dcae-artifact-types
  "Returns lazy-seq of string representations of ArtifactTypeEnums that DCAE-related"
  []
  (letfn [(dcae-artifact-type? [artifact-type]
            (boolean (re-find #"DCAE_" artifact-type)))]
    (filter dcae-artifact-type?
      (map #(.name %) (seq (. ArtifactTypeEnum values))))
    ))

(defn dcae-artifact?
  "Checks to see if the artifact is a DCAE artifact"
  [artifact]
  (let [supported-dcae-artifact-types (get-dcae-artifact-types)]
    (true? (some #(= (:artifactType artifact) %) supported-dcae-artifact-types))
    ))


(defn dcae-artifact-inventory-blueprint?
  "Check to see if the artifact is an inventory blueprint"
  [artifact]
  (= (:artifactType artifact) "DCAE_INVENTORY_BLUEPRINT"))


(defn get-service-locations
  "Gets service locations for a given blueprint

  The service location information is attached as a separate artifact. This function
  is responsible for finding the matching locations JSON artifact that is of the form:

  { \"artifactName\": <artifact name of the blueprint artifact>,
    \"locations\": <list of location strings> }"
  [get-artifact-func resource-metadata artifact-name]
  (let [target-artifacts (filter #(= (:artifactType %) "DCAE_INVENTORY_JSON")
                                 (:artifacts resource-metadata))
        inventory-jsons (map #(parse-string (get-artifact-func (:artifactURL %)) true)
                             target-artifacts)
        location-jsons (filter #(and
                                  (= (:artifactName %) artifact-name)
                                  (contains? % :locations)) inventory-jsons)]
    (flatten (map :locations location-jsons))
    ))

(defn generate-dcae-service-type-requests
  "Generates DCAE service type requests from ASDC change event

  The ASDC change event is a nested structure. The single arity of this method
  handles at the service level of the event.  The two arity of this method handles
  at the resource level of the event.

  `get-blueprint-func` is function that takes the `artifactURL` and retrieves
  the DCAE blueprint artifact.

  Returns a list of DCAE service type requests"
  ([get-blueprint-func get-locations-func service-change-event]
   (let [; TODO: Where do I get this from?
         service-location nil
         service-id (:invariantUUID service-change-event)
         service-part { :asdcServiceId service-id
                        :asdcServiceURL (construct-service-path service-id)
                        :owner (:lastUpdaterFullName service-change-event) }]

     ; Given the resource part, create dcae service type requests
     (letfn [(generate-for-resource
               [resource-change-event]
               (let [dcae-artifacts (filter dcae-artifact-inventory-blueprint?
                                            (:artifacts resource-change-event))
                     resource-part { :asdcResourceId
                                    (:resourceInvariantUUID resource-change-event) }]

                 (map #(-> service-part
                           (merge resource-part)
                           ; WATCH! Using artifactName over artifactUUID because artifactUUID
                           ; is variant between versions. ASDC folks should be adding invariant
                           ; UUID.
                           (assoc :typeName (:artifactName %)
                                  :typeVersion (Integer. (:artifactVersion %))
                                  :blueprintTemplate (get-blueprint-func (:artifactURL %))
                                  :serviceLocations (get-locations-func resource-change-event
                                                                        (:artifactName %))
                                  
                                  )
                           )
                      dcae-artifacts)
                 ))]

       (flatten (map #(generate-for-resource %) (:resources service-change-event)))
       ))))


(defn pick-out-artifact
  "Given dcae service type, fetch complementary asdc artifact"
  [service-metadata request]
  (let [target-resource (:asdcResourceId request)
        resource-metadata (first (filter #(= (:resourceInvariantUUID %) target-resource)
                                         (:resources service-metadata)))
        target-artifact (:typeName request)
        artifact-metadata (filter #(= (:artifactName %) target-artifact)
                                  (:artifacts resource-metadata))
        ]
    (first artifact-metadata)))

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

(ns sch.core
  (:require [clojure.java.io :refer :all]
            [cheshire.core :refer [parse-stream parse-string]]
            [clojure.tools.logging :as logger :refer [debug info error]]
            [sch.handle :refer [handle-change-event! download-artifacts! deploy-artifacts!
                                deployed-ok deployed-error deployed-already]]
            [sch.asdc-client :refer [get-service-metadata! create-asdc-conn get-consumer-id]]
            [sch.inventory-client :refer [create-inventory-conn]]
            [sch.parse :refer [get-dcae-artifact-types pick-out-artifact]]
            [sch.util :refer [read-config]]
            [clojure.string :as strlib]
            [postal.core :refer [send-message]]
            )
  (:import (org.onap.sdc.impl DistributionClientFactory)
           (org.onap.sdc.api.consumer IConfiguration INotificationCallback
                                            IDistributionStatusMessage IComponentDoneStatusMessage)
           (org.onap.sdc.utils DistributionActionResultEnum DistributionStatusEnum)
           (com.google.gson Gson)
           )
  (:gen-class))


(defn process-event-from-local-file!
  [event-file-path]
  (with-open [rdr (reader event-file-path)]
    (parse-stream rdr true))
  )

; Distribution client code
; TODO: Should be moved to separate namespace

(defn send-distribution-status!
  "Convenience function used to send distribution status messages"
  [dist-client distribution-id consumer-id artifact status]
  (let [dist-message (proxy [IDistributionStatusMessage] []
                       (getDistributionID [] distribution-id)
                       (getConsumerID [] consumer-id)
                       (getTimestamp []
                         (. java.lang.System currentTimeMillis))
                       (getArtifactURL [] (:artifactURL artifact))
                       (getStatus [] status))
        resp (.sendDeploymentStatus dist-client dist-message)
        ]
    (if (not= (.getDistributionActionResult resp) (. DistributionActionResultEnum SUCCESS))
      (error (str "Problem sending status: " (:artifactName artifact) ", "
                  (str (.getDistributionMessageResult resp)))))
    ))

(defn send-component-done-status!
  "Convenience function used to send component done status messages"
  [dist-client distribution-id consumer-id msg artifact status]
  (let [dist-message (proxy [IComponentDoneStatusMessage] []
                       (getDistributionID [] distribution-id)
                       (getConsumerID [] consumer-id)
                       (getTimestamp []
                         (. java.lang.System currentTimeMillis))
                       (getArtifactURL [] (:artifactURL artifact))
                       (getComponentName [] "service-change-handler")
                       (getStatus [] status))
        resp (if (strlib/blank? msg)
               (.sendComponentDoneStatus dist-client dist-message)
               (.sendComponentDoneStatus dist-client dist-message "failed to deploy to inventory"))
        ]
    (if (not= (.getDistributionActionResult resp) (. DistributionActionResultEnum SUCCESS))
      (error (str "Problem sending status: " (:artifactName artifact) ", "
                  (str (.getDistributionMessageResult resp)))))
    ))

(defn deploy-artifacts-ex!
  "Enhanced deploy artifacts function

  After calling deploy-artifacts!, this method takes the results and sends out
  appropriate distribution status messages per artifact processed"
  [inventory-uri service-metadata requests send-dist-status send-comp-done-status fromEmail toEmail]
  (let [[to-post posted to-delete deleted] (deploy-artifacts! inventory-uri service-metadata
                                                              requests)
        pick-out-artifact (partial pick-out-artifact service-metadata)]

    (dorun (map #(do
                   (send-dist-status (pick-out-artifact %)
                                   (. DistributionStatusEnum DEPLOY_OK))
                   (send-comp-done-status "" (pick-out-artifact %)
                                   (. DistributionStatusEnum COMPONENT_DONE_OK))
                   (if (and (not (strlib/blank? fromEmail)) (not (strlib/blank? toEmail)))
                     (do
                       (debug (str "Sending notification from " fromEmail " to " toEmail))
                       (try
                         (send-message {:from fromEmail
                                        :to [toEmail]
                                        :subject "DCAE inventory blueprint downloaded from ASDC and inserted into inventory DB"
                                        :body (str
                                                "ASDC blueprint has been inserted into inventory <"
                                                inventory-uri
                                                ">.\n"
                                                (pick-out-artifact %)
                                              )
                                       })
                         (catch Exception e (error (str "caught exception from send-message" (.getMessage e))))
                       )
                     )
                   )
                 )
                (deployed-ok to-post posted)))
    (dorun (map #(do
                   (send-dist-status (pick-out-artifact %)
                                   (. DistributionStatusEnum DEPLOY_ERROR))
                   (send-comp-done-status "failed to deploy to inventory" (pick-out-artifact %)
                                   (. DistributionStatusEnum COMPONENT_DONE_ERROR))
                   )
                (deployed-error to-post posted)))
    (dorun (map #(do
                   (send-dist-status (pick-out-artifact %)
                                   (. DistributionStatusEnum ALREADY_DEPLOYED))
                   (send-comp-done-status "" (pick-out-artifact %)
                                   (. DistributionStatusEnum COMPONENT_DONE_OK))
                   )
                (deployed-already requests to-post)))
    ; REVIEW: How about the deleted service types?
    ))


(defn create-distribution-client-config
  [config]
  (let [config-asdc (:asdcDistributionClient config)]
    (proxy [IConfiguration] []
      (getAsdcAddress [] (str (:asdcAddress config-asdc)))
      (getMsgBusAddress [] (java.util.ArrayList.
                              (strlib/split (str (:msgBusAddress config-asdc)) #",")))
      (getUser [] (str (:user config-asdc)))
      (getPassword [] (str (:password config-asdc)))
      (getPollingInterval [] (int (:pollingInterval config-asdc)))
      (getPollingTimeout [] (int (:pollingTimeout config-asdc)))
      ; Note: The following didn't work
      ; (. Arrays asList (. ArtifactTypeEnum values))
      ; Also, cannot just use a narrow list of artifact types in order
      ; to handle the deletion scenario.
      (getRelevantArtifactTypes [] (java.util.ArrayList.
                                      (get-dcae-artifact-types)))
      (getConsumerGroup [] (str (:consumerGroup config-asdc)))
      (getConsumerID [] (str (:consumerId config-asdc)))
      (getEnvironmentName [] (str (:environmentName config-asdc)))
      (getKeyStorePath [] (str (:keyStorePath config-asdc)))
      (getKeyStorePassword [] (str (:keyStorePassword config-asdc)))
      (activateServerTLSAuth [] (boolean (:activateServerTLSAuth config-asdc)))
      (isFilterInEmptyResources [] (boolean (:isFilterInEmptyResources config-asdc)))
      (isUseHttpsWithDmaap [] (boolean (:useHttpsWithDmaap config-asdc true)))
      (isConsumeProduceStatusTopic [] (boolean (:isConsumeProduceStatusTopic config-asdc false)))
      )))

(defn run-distribution-client!
  "Entry point to the core production functionality

  Uses the asdc distribution client and to poll for notification events and makes calls
  to handle those events"
  [dist-client-config inventory-uri asdc-conn fromEmail toEmail]
  (debug "Entering run-distribution-client")
  (let [dist-client (. DistributionClientFactory createDistributionClient)
        dist-client-callback (proxy [INotificationCallback] []
                               (activateCallback [data]
                                 "Callback executed upon notification events

                                 The input parameter is of type `NotificationDataImpl` which fails
                                 to translate via the clojure `bean` call because class is not
                                 public. So mirroring what's done in the distribution client -
                                 use Gson.

                                 Discovered that the notification event and the service metadata
                                 data models are different. Use service metadata because its
                                 richer."
                                 (debug "Entering dist-client-callback")
                                 (let [change-event (parse-string (.toJson (Gson.) data) true)
                                       service-id (:serviceUUID change-event)
                                       distribution-id (:distributionID change-event)
                                       service-metadata (get-service-metadata! asdc-conn
                                                                              service-id)
                                       send-dist-status (partial send-distribution-status!
                                                                 dist-client distribution-id
                                                                 (get-consumer-id asdc-conn))
                                       send-comp-done-status (partial send-component-done-status!
                                                                 dist-client distribution-id
                                                                 (get-consumer-id asdc-conn))
                                       ]

                                   (info (str "Handle change event: " (:serviceName change-event)
                                              ", " distribution-id))

                                   (let [requests (download-artifacts! inventory-uri asdc-conn
                                                                       service-metadata)
                                         artifacts (map #(pick-out-artifact service-metadata %)
                                                        requests)
                                         ]

                                     (dorun (map #(send-dist-status
                                                    % (. DistributionStatusEnum DOWNLOAD_OK))
                                                 artifacts))
                                     (deploy-artifacts-ex! inventory-uri service-metadata
                                                           requests send-dist-status send-comp-done-status
                                                           fromEmail toEmail)
                                     )

                                   )))
        ]
    (let [dist-client-init-result (.init dist-client dist-client-config dist-client-callback)]
      (if (= (.getDistributionActionResult dist-client-init-result)
             (. DistributionActionResultEnum SUCCESS))
        (.start dist-client)
        (error dist-client-init-result))
      )))

(defn -main [& args]
  (let [[mode config-path event-path] args
        config (read-config config-path)
        inventory-uri (create-inventory-conn config)
        asdc-conn (create-asdc-conn config)
        fromEmail (get-in config [:notification :fromEmail])
        toEmail (get-in config [:notification :toEmail])
        ]

    (if (and (not (strlib/blank? fromEmail)) (not (strlib/blank? toEmail)))
      (debug "Email notification enabled")
    )
    (if (= "DEV" (clojure.string/upper-case mode))
      (do
        (info "Development mode")
        (handle-change-event! inventory-uri asdc-conn
                              (process-event-from-local-file! event-path))
        )
      (run-distribution-client!
        (create-distribution-client-config config)
        inventory-uri asdc-conn fromEmail toEmail)
      )

    (info "Done"))
    )

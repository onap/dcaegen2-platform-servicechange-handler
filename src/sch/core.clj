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
            [taoensso.timbre :as timbre :refer [info error]]
            [taoensso.timbre.appenders.3rd-party.rolling :refer [rolling-appender]]
            [sch.handle :refer [handle-change-event! download-artifacts! deploy-artifacts!
                                deployed-ok deployed-error deployed-already]]
            [sch.asdc-client :refer [get-service-metadata! create-asdc-conn get-consumer-id]]
            [sch.inventory-client :refer [create-inventory-conn]]
            [sch.parse :refer [get-dcae-artifact-types pick-out-artifact]]
            [sch.util :refer [read-config]]
            )
  (:import (org.openecomp.sdc.impl DistributionClientFactory)
           (org.openecomp.sdc.api.consumer IConfiguration INotificationCallback
                                            IDistributionStatusMessage)
           (org.openecomp.sdc.utils DistributionActionResultEnum DistributionStatusEnum)
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

(defn deploy-artifacts-ex!
  "Enhanced deploy artifacts function

  After calling deploy-artifacts!, this method takes the results and sends out
  appropriate distribution status messages per artifact processed"
  [inventory-uri service-metadata requests send-dist-status]
  (let [[to-post posted to-delete deleted] (deploy-artifacts! inventory-uri service-metadata
                                                              requests)
        pick-out-artifact (partial pick-out-artifact service-metadata)]

    (dorun (map #(send-dist-status (pick-out-artifact %)
                                   (. DistributionStatusEnum DEPLOY_OK))
                (deployed-ok to-post posted)))
    (dorun (map #(send-dist-status (pick-out-artifact %)
                                   (. DistributionStatusEnum DEPLOY_ERROR))
                (deployed-error to-post posted)))
    (dorun (map #(send-dist-status (pick-out-artifact %)
                                   (. DistributionStatusEnum ALREADY_DEPLOYED))
                (deployed-already requests to-post)))
    ; REVIEW: How about the deleted service types?
    ))


(defn create-distribution-client-config
  [config]
  (let [config-asdc (:asdcDistributionClient config)]
    (proxy [IConfiguration] []
      (getAsdcAddress [] (:asdcAddress config-asdc))
      (getUser [] (:user config-asdc))
      (getPassword [] (:password config-asdc))
      (getPollingInterval [] (:pollingInterval config-asdc))
      (getPollingTimeout [] (:pollingTimeout config-asdc))
      ; Note: The following didn't work
      ; (. Arrays asList (. ArtifactTypeEnum values))
      ; Also, cannot just use a narrow list of artifact types in order
      ; to handle the deletion scenario.
      (getRelevantArtifactTypes [] (java.util.ArrayList.
                                      (get-dcae-artifact-types)))
      (getConsumerGroup [] (:consumerGroup config-asdc))
      (getConsumerID [] (:consumerId config-asdc))
      (getEnvironmentName [] (:environmentName config-asdc))
      (getKeyStorePath [] (:keyStorePath config-asdc))
      (getKeyStorePassword [] (:keyStorePassword config-asdc))
      (activateServerTLSAuth [] (:activateServerTLSAuth config-asdc))
      (isFilterInEmptyResources [] (:isFilterInEmptyResources config-asdc))
      )))

(defn run-distribution-client!
  "Entry point to the core production functionality

  Uses the asdc distribution client and to poll for notification events and makes calls
  to handle those events"
  [dist-client-config inventory-uri asdc-conn]
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
                                 (let [change-event (parse-string (.toJson (Gson.) data) true)
                                       service-id (:serviceUUID change-event)
                                       distribution-id (:distributionID change-event)
                                       service-metadata (get-service-metadata! asdc-conn
                                                                              service-id)
                                       send-dist-status (partial send-distribution-status!
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
                                                           requests send-dist-status)
                                     )

                                   )))
        ]
    (let [dist-client-init-result (.init dist-client dist-client-config dist-client-callback)]
      (if (= (.getDistributionActionResult dist-client-init-result)
             (. DistributionActionResultEnum SUCCESS))
        (.start dist-client)
        (error dist-client-init-result))
      )))

(defn- setup-logging-rolling
  "Setup logging with the rolling appender"
  [{ {:keys [currentLogFilename rotationFrequency]} :logging }]
  (let [rolling-params (when currentLogFilename { :path currentLogFilename })
        rolling-params (when rotationFrequency
                         (assoc rolling-params :pattern (keyword rotationFrequency)))]
    (timbre/merge-config! { :level :debug :appenders { :rolling (rolling-appender rolling-params) } })
    (info "Setup logging: Rolling appender" (if rolling-params rolling-params "DEFAULT"))
  ))


(defn -main [& args]
  (let [[mode config-path event-path] args
        config (read-config config-path)
        inventory-uri (create-inventory-conn config)
        asdc-conn (create-asdc-conn config)
        ]

    (setup-logging-rolling config)

    (if (= "DEV" (clojure.string/upper-case mode))
      (do
        (info "Development mode")
        (handle-change-event! inventory-uri asdc-conn
                              (process-event-from-local-file! event-path))
        )
      (run-distribution-client!
        (create-distribution-client-config config)
        inventory-uri asdc-conn)
      )

    (info "Done"))
    )

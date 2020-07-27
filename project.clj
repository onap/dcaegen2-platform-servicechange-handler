; ============LICENSE_START=======================================================
; org.onap.dcae
; ================================================================================
; Copyright (c) 2017-2020 AT&T Intellectual Property. All rights reserved.
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

; Using lein for REPL and testing because couldn't get Maven clojure plugin to work
; for these functional areas.

(defproject service-change-handler "1.4.0"
  :description "Service change handler"
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [cheshire/cheshire "5.9.0"]
                 [org.clojure/tools.logging "1.1.0"]
                 [clj-http/clj-http "3.10.1"]
                 [org.bovinegenius/exploding-fish "0.3.6"]
                 [clj-yaml/clj-yaml "0.4.0"]
                 [org.onap.sdc.sdc-distribution-client/sdc-distribution-client "1.3.0"]
                 [ch.qos.logback/logback-classic "1.2.3"]
                 ]

  ; TODO: Fill in the onap maven repository info
  :repositories [["onap nexus" "https://nexus.onap.org/content/repositories/snapshots/"]]

  :plugins [[lein-cloverage "1.0.9"]]
  :profiles { :test { :dependencies [[clj-fakes "0.12.0"]] }
              ; Added this for cloverage
              :dev { :dependencies [[clj-fakes "0.12.0"]] } }

  )

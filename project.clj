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

; Using lein for REPL and testing because couldn't get Maven clojure plugin to work
; for these functional areas.

(defproject service-change-handler "0.1.0"
  :description "Service change handler"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [cheshire/cheshire "5.6.3"]
                 [com.taoensso/timbre "4.7.4"]
                 [com.fzakaria/slf4j-timbre "0.3.2"]
                 [clj-http/clj-http "3.3.0"]
                 [org.bovinegenius/exploding-fish "0.3.4"]
                 [clj-yaml/clj-yaml "0.4.0"]
                 [org.openecomp.sdc/sdc-distribution-client "1.1.4"]]

  ; TODO: Fill in the onap maven repository info
  :repositories []

  )

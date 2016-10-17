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

(ns sch.util
  (:require [clj-http.client :as client]
            [clj-yaml.core :as yaml]
            [cheshire.core :refer [parse-string]]
            )
  (:gen-class))


(defn- read-config-http-json
  [config-url]
  (let [resp (client/get config-url)]
    (if (= (:status resp) 200)
      (parse-string (:body resp) true)
      )))

(defn read-config
  "Read configuration from file or from an http server

  Returns a native map representation of the configuration"
  [config-path]
  (letfn [(is-http? [config-path]
            (not (nil? (re-find #"(?:https|http)://.*" config-path))))]
    (if (is-http? config-path)
      (read-config-http-json config-path)
      (yaml/parse-string (slurp config-path))
      )
    ))


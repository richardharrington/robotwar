(defproject robotwar "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [clj-time "0.15.2"]
                 [ring/ring-json "0.5.1"]
                 [ring/ring-core "1.9.5"]
                 [compojure "1.7.0"]]
  :plugins [[lein-ring "0.12.6"]]
  :ring {:handler robotwar.handler/app}
  :profiles {:dev {:dependencies [[midje "1.10.5"]
                                  [ring-mock "0.1.5"]]}}
  :main robotwar.core)

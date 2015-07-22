(defproject robotwar "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :min-lein-version "2.0.0"
  :javac-target "1.7"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [clj-time "0.5.1"]
                 [ring/ring-json "0.3.1"]
                 [ring/ring-core "1.3.1"]]
  :plugins [[lein-ring "0.8.5"]]
  :ring {:handler robotwar.handler/app}
  :profiles {:dev {:dependencies [[midje "1.5.1"]
                                  [ring-mock "0.1.5"]]}}
  :main robotwar.core)

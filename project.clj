(defproject robotwar "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/core.match "0.2.0-rc5"]
                 [clj-time "0.5.1"]
                 [ring/ring-json "0.2.0"]
                 [compojure "1.1.5"]]
  :plugins [[lein-ring "0.8.5"]]
  :ring {:handler robotwar.handler/app}
  :profiles {:dev {:dependencies [[midje "1.5.1"]
                                  [ring-mock "0.1.5"]]}}
  :main robotwar.core)

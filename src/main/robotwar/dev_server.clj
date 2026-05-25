(ns robotwar.dev-server
  (:require [ring.adapter.jetty :refer [run-jetty]]
            [robotwar.handler :as handler]))

(defn -main [& _]
  (run-jetty #'handler/app {:port 3000 :join? true}))

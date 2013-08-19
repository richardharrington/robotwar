(ns robotwar.handler
  (:use compojure.core)
  (:require [compojure.handler :as handler]
            [ring.middleware.json :as middleware]
            [ring.util.response :as response]
            [compojure.route :as route]
            [robotwar.test-programs :as test-programs]
            [robotwar.core :as core]))

(defn take-drop-send 
  "takes a collection agent and a number n,
  returns a collection of the first n items and 
  drops those items from the agent's state.
  TODO: find a built-in way to do this.
  There must be one."
  [a n] 
  (let [coll @a]
    (send a (constantly (drop n coll)))
    (take n coll)))

(def worlds (agent (core/worlds-for-browser-display)))

(defroutes app-routes
  (GET "/programs" [] (response/response test-programs/programs))
  (GET "/worlds/:n" [n] (println "sending 500 more")
                        (response/response 
                              (take-drop-send worlds (Integer/parseInt n))))
  (route/files "/")
  (route/not-found "Not Found"))

(def app
  (-> (handler/site app-routes)
      (middleware/wrap-json-response)
      (middleware/wrap-json-body)))

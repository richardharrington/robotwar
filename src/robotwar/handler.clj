(ns robotwar.handler
  (:use compojure.core)
  (:require [compojure.handler :as handler]
            [ring.middleware.json :as middleware]
            [ring.util.response :as response]
            [compojure.route :as route]
            [robotwar.test-programs :as test-programs]
            [robotwar.core :as core]))

(defroutes app-routes
  (GET "/" [] "Hello World, Welcome to Robotwar.")
  (GET "/json-test" [] (response/response {:foo 6 :bar 8}))
  (GET "/programs" [] (response/response test-programs/programs))
  (GET "/simulations" [] (response/response (take 1 (core/sim-worlds)))) 
  (route/resources "/")
  (route/not-found "Not Found"))

(def app
  (-> (handler/site app-routes)
      (middleware/wrap-json-response)
      (middleware/wrap-json-body)))

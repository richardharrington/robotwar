(ns robotwar.handler
  (:use compojure.core)
  (:require [compojure.handler :as handler]
            [ring.middleware.json :as middleware]
            [ring.util.response :as response]
            [compojure.route :as route]
            [robotwar.source-programs :as source-programs]
            [robotwar.world :as world]
            [robotwar.browser :as browser]))

; TODO: have this source-code-picking be done in requests from the UI
; in the browser, not hard-coded here.
(def progs (concat (vals (select-keys source-programs/programs
                                      [:left-shooter
                                       :right-shooter]))
                   (repeat 2 (:mover source-programs/programs))))

(defn add-game
  "a function to update the games store agent state. 
  It keeps a running store of games, which is added
  to upon request from a browser, who is then
  assigned an id number"
  [{:keys [next-id games]}]
  {:next-id (inc next-id)
   :games (merge games {next-id (-> progs
                                    (world/init-world)
                                    (world/build-combined-worlds)
                                    (browser/worlds-for-browser))})}) 

(defn take-drop-send 
  "takes the games store agent, a game id, and a number n, 
  and returns a collection of the first n items from that game,
  then drops those items from the game in the agent's state."
  [a id n] 
  (let [coll (get-in @a [:games id])]
    (send a #(assoc-in % [:games id] (drop n coll)))
    (take n coll)))

(def games-agent (agent {:next-id 0
                         :games {}}))

(defroutes app-routes
  (GET "/programs" [] (response/response source-programs/programs))
  (GET "/init" [] (let [next-id (:next-id @games-agent)]
                    (send games-agent add-game)
                    (response/response {:id next-id})))
  (GET "/worlds/:id/:n" [id n] (response/response (take-drop-send
                                                    games-agent 
                                                    (Integer/parseInt id) 
                                                    (Integer/parseInt n))))
  (route/files "/")
  (route/not-found "Not Found"))

(def app
  (-> (handler/site app-routes)
      (middleware/wrap-json-response)
      (middleware/wrap-json-body)))

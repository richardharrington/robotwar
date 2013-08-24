(ns robotwar.handler
  (:use [compojure.core]
        [clojure.string :only [split]])
  (:require [compojure.handler :as handler]
            [ring.middleware.json :as middleware]
            [ring.util.response :as response]
            [compojure.route :as route]
            [robotwar.source-programs :as source-programs]
            [robotwar.world :as world]
            [robotwar.browser :as browser]))

(defn parse-program-names
  "takes a string parameter from the browser and returns a seqence
  of program keys"
  [programs-str]
  (map keyword (split programs-str #"\s+")))

(defn get-programs 
  "gets a sequence of programs from the source-code 
  repository. some may be repeats. discards failed matches."
  [program-keys]
  (filter identity (map #(% source-programs/programs) program-keys)))

(defn add-game
  "a function to update the games store agent state. 
  It keeps a running store of games, which is added
  to upon request from a browser, who is then
  assigned an id number"
  [{:keys [next-id games]} programs-str]
  (let [program-names (parse-program-names programs-str)
        programs (get-programs program-names)
        world (world/init-world programs)
        combined-worlds (world/build-combined-worlds world)
        worlds-for-browser (browser/worlds-for-browser combined-worlds)] 
    {:games (merge games {next-id worlds-for-browser})
     :next-id (inc next-id)})) 

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
  (GET "/init" [programs] (let [next-id (:next-id @games-agent)]
                            (send games-agent add-game programs)
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

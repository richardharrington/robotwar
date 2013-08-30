(ns robotwar.handler
  (:use [compojure.core]
        [clojure.string :only [split]]
        [robotwar.constants])
  (:require [compojure.handler :as handler]
            [ring.middleware.json :as middleware]
            [ring.util.response :as response]
            [compojure.route :as route]
            [robotwar.source-programs :as source-programs]
            [robotwar.world :as world]
            [robotwar.browser :as browser]))

(def game-info {:ROBOT-RADIUS ROBOT-RADIUS
                :ROBOT-RANGE-X ROBOT-RANGE-X
                :ROBOT-RANGE-Y ROBOT-RANGE-Y
                :*GAME-SECONDS-PER-TICK* *GAME-SECONDS-PER-TICK*})

(defn parse-program-names
  "takes a string parameter from the browser and returns a seqence
  of program keys"
  [programs-str]
  (map keyword (split programs-str #"[,\s]\s*")))

(defn get-programs 
  "gets a sequence of programs from the source-code 
  repository. some may be repeats. discards failed matches.
  cuts off after five."
  [program-keys]
  (take 5 (filter identity (map #(% source-programs/programs) program-keys))))

(defn add-game
  "a function to update the games-store atom state. 
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
  "takes the games-store atom, a game id, and a number n, 
  and returns a collection of the first n items from that game,
  then drops those items from the game in the atom's state."
  [games-store id n] 
  (let [coll (get-in @games-store [:games id])]
    (swap! games-store #(assoc-in % [:games id] (drop n coll)))
    (take n coll)))

(def games-store (atom {:next-id 0
                        :games {}}))

(defroutes app-routes
  (GET "/program-names" [] (response/response
                             {:names (map name (keys source-programs/programs))}))
  (GET "/init" [programs] (let [next-id (:next-id @games-store)]
                            (swap! games-store add-game programs)
                            (response/response {:id next-id 
                                                :game-info game-info})))
  (GET "/worlds/:id/:n" [id n] (response/response (take-drop-send
                                                    games-store 
                                                    (Integer/parseInt id) 
                                                    (Integer/parseInt n))))
  (route/files "/")
  (route/not-found "Not Found"))

(def app
  (-> (handler/site app-routes)
      (middleware/wrap-json-response)
      (middleware/wrap-json-body)))

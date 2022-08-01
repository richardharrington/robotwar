(ns robotwar.handler
  (:use [clojure.string :only [split]]
        [robotwar.constants])
  (:require [ring.middleware.json :refer [wrap-json-response wrap-json-body]]
            [ring.middleware.file :refer [wrap-file]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.util.response :refer [response not-found]]
            [robotwar.source-programs :as source-programs]
            [robotwar.world :as world]
            [robotwar.browser :as browser]
            [compojure.core :refer :all]
            [compojure.route :as route]))

(def game-info {:ROBOT-RADIUS ROBOT-RADIUS
                :ROBOT-RANGE-X ROBOT-RANGE-X
                :ROBOT-RANGE-Y ROBOT-RANGE-Y
                :*GAME-SECONDS-PER-TICK* *GAME-SECONDS-PER-TICK*})

(defn parse-program-names
  "takes a string parameter from the browser and returns a sequence
  of program keys"
  [programs-str]
  (map keyword (split programs-str #"[,\s]+")))

(defn get-programs
  "gets a sequence of five programs from the source-code repository."
  [program-keys]
  (->> program-keys
       (map source-programs/programs)
       (remove nil?)
       (take 5)))

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

(defroutes my-routes
           (GET "/worlds/:id/:n" [id n]
                (response
                  (take-drop-send
                    games-store
                    (Integer/parseInt id)
                    (Integer/parseInt n))))

           (GET "/program-names" []
                (response
                  {:names (map name (keys source-programs/programs))}))

           (GET "/init" {{programs "programs"} :params}
                (println "in init")
                (println (str programs))
                (swap! games-store add-game programs)
                (response {:id (:next-id @games-store)
                           :game-info game-info})))


;
;(defn handler [{:keys [uri query-params request-method] :as request}]
;  (let [route (fn [acceptable-request-method re action]
;                (when (= request-method acceptable-request-method)
;                  (when-let [[_ & url-params] (consistently-typed-re-matches re uri)]
;                    (apply action url-params))))]
;    (or
;     (route :get #"\/worlds\/(\d+)\/(\d+)"
;            (fn [id n]
;              (response (take-drop-send
;                         games-store
;                         (Integer/parseInt id)
;                         (Integer/parseInt n)))))
;     (route :get #"\/program-names"
;            (fn []
;              (response
;               {:names (map name (keys source-programs/programs))})))
;     (route :get #"\/init"
;            (fn []
;              (let [programs (query-params "programs")
;                    next-id (:next-id @games-store)]
;                (swap! games-store add-game programs)
;                (response {:id next-id
;                           :game-info game-info}))))
;     (not-found "Not Found"))))

(def app
  (-> my-routes
      (wrap-file "public")
      (wrap-json-response)
      (wrap-json-body)
      (wrap-params)))

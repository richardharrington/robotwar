(ns robotwar.world
  (:require [robotwar.constants :refer [ROBOT-RANGE-X ROBOT-RANGE-Y
                                         MAX-BLAST-DAMAGE BLAST-RADIUS]]
            [robotwar.robot :as robot]
            [robotwar.shell :as shell]
            [robotwar.physics :as physics]))

(defn init-world
  "initialize all the variables for a robot world."
  [programs]
  (let [program-count (count programs)]
    (when (< program-count 2)
      (throw (ex-info "Robot world requires at least 2 programs" 
                      {:error :insufficient-programs :count program-count})))
    (when (> program-count 5)
      (throw (ex-info "Robot world supports at most 5 programs" 
                      {:error :too-many-programs :count program-count})))
    {:shells {}
     :next-shell-id 0
     :tick 0
     :robots (vec (map-indexed (fn [idx program]
                                 (robot/init-robot 
                                   idx 
                                   program 
                                   {:pos-x (rand ROBOT-RANGE-X)
                                    :pos-y (rand ROBOT-RANGE-Y)
                                    :aim 0.0
                                    :damage 100.0}))
                               programs))}))

(defn- shell-damage
  [shell robot]
  (let [dx (- (:pos-x robot) (:pos-x shell))
        dy (- (:pos-y robot) (:pos-y shell))
        distance (physics/rw-sqrt (+ (* dx dx) (* dy dy)))
        factor (max 0.0 (- 1.0 (/ distance BLAST-RADIUS)))]
    (* MAX-BLAST-DAMAGE factor factor)))

(defn tick-combined-world
  [starting-world]
  (let [tick (inc (:tick starting-world 0))
        alive-indices (vec (keep-indexed (fn [idx robot]
                                           (when (:alive? robot) idx))
                                         (:robots starting-world)))
        {:keys [shells next-shell-id] :as ticked-robots-world}
          (reduce (fn [{robots :robots :as world} robot-idx]
                    (robot/tick-robot (robots robot-idx) world))
                  starting-world
                  alive-indices)
        collided-world (update ticked-robots-world :robots robot/collision-pass)
        ticked-shells (into {} (for [[id shell] shells
                                   :let [ticked (shell/tick-shell shell)]
                                   :when ticked]
                               [id ticked]))
        live-shells (into {} (remove (fn [[_ s]] (:exploded s)) ticked-shells))
        exploded-shells (vals (filter (fn [[_ s]] (:exploded s)) ticked-shells))
        damage-per-robot
        (reduce (fn [acc shell]
                  (reduce (fn [acc robot]
                            (if (:alive? robot)
                              (let [damage (shell-damage shell robot)]
                                (if (> damage 0.0)
                                  (update acc (:idx robot) (fnil + 0.0) damage)
                                  acc))
                              acc))
                          acc
                          (:robots collided-world)))
                {}
                exploded-shells)
        damaged-world
        (if (seq damage-per-robot)
          (update collided-world :robots
                  (fn [robots]
                    (mapv (fn [robot]
                            (if-let [damage (get damage-per-robot (:idx robot))]
                              (update robot :damage - damage)
                              robot))
                          robots)))
          collided-world)
        pre-death-alive-idxs (set (keep-indexed (fn [idx robot] (when (:alive? robot) idx))
                                                (:robots damaged-world)))
        dead-marked-world
        (let [robots (:robots damaged-world)
              new-robots (mapv (fn [robot]
                                 (if (and (:alive? robot) (<= (:damage robot) 0.0))
                                   (assoc robot :alive? false :died-at-tick tick)
                                   robot))
                               robots)]
          (assoc damaged-world :robots new-robots))
        post-death-alive-idxs (set (keep-indexed (fn [idx robot] (when (:alive? robot) idx))
                                                 (:robots dead-marked-world)))
        just-died-idxs (filterv #(not (post-death-alive-idxs %)) pre-death-alive-idxs)
        alive-robots (filterv :alive? (:robots dead-marked-world))
        result (cond
                 (= 1 (count alive-robots)) {:winner (:idx (first alive-robots))}
                 (zero? (count alive-robots)) {:game-over? true}
                 :else nil)]
    (assoc dead-marked-world :result result :just-died just-died-idxs :shells live-shells :tick tick)))

(def build-combined-worlds (partial iterate tick-combined-world))

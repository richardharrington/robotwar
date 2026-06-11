(ns robotwar.world
  (:require [robotwar.constants :refer [ROBOT-RANGE-X ROBOT-RANGE-Y]]
            [robotwar.robot :as robot]
            [robotwar.shell :as shell]))

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
     :robots (vec (map-indexed (fn [idx program]
                                 (robot/init-robot 
                                   idx 
                                   program 
                                   {:pos-x (rand ROBOT-RANGE-X)
                                    :pos-y (rand ROBOT-RANGE-Y)
                                    :aim 0.0
                                    :damage 100.0}))
                               programs))}))

(defn tick-combined-world
  [starting-world]
  (let [alive-indices (vec (keep-indexed (fn [idx robot] 
                                           (when (:alive? robot) idx))
                                         (:robots starting-world)))
        {:keys [shells next-shell-id] :as ticked-robots-world} 
          (reduce (fn [{robots :robots :as world} robot-idx]
                    (robot/tick-robot (robots robot-idx) world))
                  starting-world
                  alive-indices)
        ticked-shells (map shell/tick-shell shells)
        live-shells (remove :exploded ticked-shells)
        exploded-shells (filter :exploded ticked-shells)]
    ; TODO: make this a real let-binding, that determines
    ; which robots were damaged.
    (let [damaged-world ticked-robots-world]
      (assoc damaged-world :shells live-shells))))

(def build-combined-worlds (partial iterate tick-combined-world))

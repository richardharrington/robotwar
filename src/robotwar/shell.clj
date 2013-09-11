(ns robotwar.shell
  (:use [robotwar.constants])
  (:require [robotwar.physics :as physics]))

(defn init-shell
  [pos-x pos-y aim distance]
  (let [{unit-x :x unit-y :y} (physics/decompose-angle aim)] 
    {:pos-x pos-x
     :pos-y pos-y
     :v-x (* unit-x SHELL-SPEED)
     :v-y (* unit-y SHELL-SPEED)
     :dest-x (+ pos-x (* unit-x distance))
     :dest-y (+ pos-y (* unit-y distance))}))

(defn tick-shell
  [{:keys [pos-x pos-y v-x v-y dest-x dest-y] :as shell}]
  (let [delta-x (* v-x *GAME-SECONDS-PER-TICK*)
        delta-y (* v-y *GAME-SECONDS-PER-TICK*)
        remaining-x (- dest-x pos-x)
        remaining-y (- dest-y pos-y)]
    (if (and (<= (Math/abs remaining-x) (Math/abs delta-x))
             (<= (Math/abs remaining-y) (Math/abs delta-y)))
      nil
      (merge shell {:pos-x (+ pos-x delta-x)
                    :pos-y (+ pos-y delta-y)}))))

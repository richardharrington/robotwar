(ns robotwar.shell
  (:use [robotwar.constants])
  (:require [robotwar.physics :as physics]))

(defn init-shell
  [pos-x pos-y aim distance]
  ; TODO: make the starting point dependent upon the robot radius,
  ; which should be in constants.
  (let [{unit-x :x unit-y :y} (physics/decompose-angle (physics/deg->rad aim))] 
    {:pos-x pos-x
     :pos-y pos-y
     :v-x (* unit-x SHELL-SPEED)
     :v-y (* unit-y SHELL-SPEED)
     :dest-x (+ pos-x (* unit-x distance))
     :dest-y (+ pos-y (* unit-y distance))
     :exploded false})) 

(defn tick-shell
  [{:keys [pos-x pos-y v-x v-y dest-x dest-y exploded] :as shell}]
  (if exploded
    nil
    (let [delta-x (* v-x *GAME-SECONDS-PER-TICK*)
          delta-y (* v-y *GAME-SECONDS-PER-TICK*)
          remaining-x (- dest-x pos-x)
          remaining-y (- dest-y pos-y)]
      ; only need to check one dimension
      (if (and (<= (Math/abs remaining-x) (Math/abs delta-x))
               (<= (Math/abs remaining-y) (Math/abs delta-y)))
        (merge shell {:pos-x dest-x
                      :pos-y dest-y
                      :exploded true})
        (merge shell {:pos-x (+ pos-x delta-x)
                      :pos-y (+ pos-y delta-y)})))))

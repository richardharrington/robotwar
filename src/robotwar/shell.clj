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
     :dest-x (* unit-x distance)
     :dest-y (* unit-y distance)})) 


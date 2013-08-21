(ns robotwar.shell
  (:use [robotwar.constants]))


(defn deg->rad
  [angle]
  (* angle (/ Math/PI 180)))

(defn decompose-angle
  [angle]
  {:x (Math/cos angle)
   :y (Math/sin angle)}) 

(defn new-shell
  [pos-x pos-y aim distance]
  ; TODO: make the starting point dependent upon the robot radius,
  ; which should be in constants.
  (let [{unit-x :x unit-y :y} (decompose-angle (deg->rad aim))] 
    {:pos-x pos-x
     :pos-y pos-y
     :v-x (* unit-x SHELL-SPEED)
     :v-y (* unit-y SHELL-SPEED)
     :dest-x (* unit-x distance)
     :dest-y (* unit-y distance)})) 


(ns robotwar.browser
  (:use [robotwar.constants]))

(defn worlds-for-browser
  "builds a sequence of worlds with the robots' brains
  removed, for more compact transmission by json.
  Fast-forward factor will be dynamically added by animation
  function in browser."
  [worlds]
  (letfn [(necessary-fields [robot]
            (select-keys robot [:pos-x 
                                :pos-y 
                                :aim
                                :damage
                                :shot-timer]))
          (three-sigs [robot]
            (zipmap (keys robot) 
                    (map (fn [x]
                           (double (/ (Math/round (* x 1000)) 1000)))
                         (vals robot))))
          (compact-robots [world]
            (update-in 
              world 
              [:robots]
              #(mapv (comp three-sigs necessary-fields) %)))]
    (map compact-robots worlds)))

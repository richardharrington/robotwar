(ns robotwar.browser
  (:use [robotwar.constants]))

(defn worlds-for-browser
  "builds a sequence of worlds with the robots' brains
  removed, for more compact transmission by json.
  Fast-forward factor will be dynamically added by animation
  function in browser.
  TODO: remove some unnecessary robot fields if we need to 
  for speed (we're going to keep them in now for diagnostic
  purposes in the browser)"
  [worlds]
  (map (fn [world]
         (assoc world :robots (mapv #(dissoc % :brain) (:robots world))))
       worlds))



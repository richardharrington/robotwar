(ns robotwar.legend
  (:require [robotwar.canvas :as canvas]))

(defonce rows (atom []))

(defn- legend-el []
  (.getElementById js/document "legend"))

(defn- health-text [damage]
  (str (max 0 (js/Math.round damage)) "%"))

(defn build-legend! [program-names]
  (when-let [container (legend-el)]
    (set! (.-innerHTML container) "")
    (reset! rows
            (vec
             (map-indexed
              (fn [idx program-name]
                (let [row (.createElement js/document "div")
                      swatch (.createElement js/document "div")
                      name-el (.createElement js/document "div")
                      health-el (.createElement js/document "div")]
                  (set! (.-className row) "legend-row")
                  (set! (.-className swatch) "legend-swatch")
                  (set! (.. swatch -style -backgroundColor)
                        (nth canvas/robot-colors idx))
                  (set! (.-className name-el) "legend-name")
                  (set! (.-textContent name-el) program-name)
                  (set! (.-className health-el) "legend-health")
                  (set! (.-textContent health-el) "100%")
                  (.appendChild row swatch)
                  (.appendChild row name-el)
                  (.appendChild row health-el)
                  (.appendChild container row)
                  {:row row :health health-el}))
              program-names)))))

(defn update-legend! [world]
  (doseq [[robot {:keys [row health]}] (map vector (:robots world) @rows)]
    (set! (.-textContent health) (health-text (:damage robot)))
    (.toggle (.-classList row) "dead" (not (:alive? robot)))))

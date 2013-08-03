(ns robotwar.robot
  (:use [clojure.string :only [join]]
        (robotwar brain game-lexicon)))

; TODO: Fill out this module.
; Probably it will consist mostly of
; 0) An init function, to initialize all the fields containing
;    the external robot information. I think this should be
;    SEPARATE FROM THE REGISTERS, even the ones that are similar.
; 1) Specialty read and write functions for the registers
; 2) Code to deal with the flag when the robot fires a shot (probably this
;    will just involve passing the flag up to the world)
; 3) Something else I can't remember. Maybe put some of the init-register
;    and default-register and register-handling-in-general code in this 
;    module, instead of in brain. Something to think about.
; 4) GENERAL NOTES: A) CHANGE STRINGS TO KEYWORDS EARLY ON.
;                   B) CHANGE SOME OF THESE MODULE LOADINGS FROM 
;                      "USE" TO "REFER", SO THAT THEY HAVE TO USE
;                      FULLY-QUALIFIED NAMES. THAT MIGHT MAKE THINGS
;                      A BIT CLEARER. THE NAMES CAN BE SHORTENED QUITE A BIT,
;                      WHEN LOADED INTO THE MODULES.
;
;(defn make-default-read [register]
;  "takes a register and returns the default version of its :read function,
;  which ignores the `world` parameter and just returns 
;  the :val field from the register."
;  (fn [_] 
;    (:val register)))
;
;(defn make-default-write [robot-idx reg-name]
;  "takes a robot-idx and a reg-name to locate a register, and
;  returns the default version of that register's :write function,
;  which takes a world parameter and a data value and returns the 
;  world with the data value assoc'd into it."
;  (fn [world data]
;   (assoc-in world [:robots robot-idx :registers reg-name :val] data)))
;
;(def default-data 0)
;
;(defn default-register [robot-idx reg-name]
;  (init-register 
;    reg-name)) 
;
;
;(defn init-robot
;  [program x y]
;  {:pos-x x
;   :pos-y y
;   :veloc-x 0
;   :veloc-y 0
;   :accel-x 0
;   :accel-y 0
;   :damage 100})
;   
;(defn init-world
;  "initialize all the variables for a robot world"
;  [width height programs]
;  {:width width
;   :height height
;   :shells []
;   :robots (vec (map-indexed (fn [idx program]
;                               {:brain (init-brain 
;                                         program 
;                                         reg-names
;                                         {(init-register "X" 
;                                                         default-read 
;                                                         default-write
;                                                         (rand-int width))
;                                          (init-register "Y"
;                                                         default-read
;                                                         default-write
;                                                         (rand-int height))})
;                                :icon (str idx)}) 
;                             programs))})
;
;(defn tick-robot
;  [robot world]
;  (let [ticked (tick-brain robot world)]
;    ))

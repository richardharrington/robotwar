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

(defn tick-robot
  [robot world]
  (let [ticked (tick-brain robot world)]
    ))

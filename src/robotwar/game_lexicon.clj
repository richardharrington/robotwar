(ns robotwar.game-lexicon)

; The reason that the reg-names vector is not composed from concatting
; the other two is that the order in reg-names is important for indexing,
; and the X and Y registers have to go in the right place.

(def storage-reg-names [ "A" "B" "C" "D" "E" "F" "G" "H" "I" "J" "K" "L" "M" 
                         "N" "O" "P" "Q" "R" "S" "T" "U" "V" "W" "Z"])

(def special-purpose-reg-names [ "DATA" "X" "Y" "AIM" "SHOT" "RADAR" "DAMAGE" "SPEEDX" "SPEEDY" "RANDOM" "INDEX" ])

(def reg-names [ "DATA" 
                 "A" "B" "C" "D" "E" "F" "G" "H" "I" "J" "K" "L" "M" 
                 "N" "O" "P" "Q" "R" "S" "T" "U" "V" "W" "X" "Y" "Z"
                 "AIM" "SHOT" "RADAR" "DAMAGE" "SPEEDX" "SPEEDY" "RANDOM" "INDEX" ])

(ns rw.lexicon)

(def registers      [ "A" "B" "C" "D" "E" "F" "G" "H" "I" "J" "K" "L" "M"
                      "N" "O" "P" "Q" "R" "S" "T" "U" "V" "W" "X" "Y" "Z"
                      "AIM" "SHOT" "RADAR" "DAMAGE" "SPEEDX" "SPEEDY" "RANDOM" "INDEX" "DATA" ])

(def op-commands    [ "-" "+" "*" "/" "=" "#" "<" ">" ])
(def word-commands  [ "TO" "IF" "GOTO" "GOSUB" "ENDSUB" ])

(def commands (concat op-commands word-commands))

(def get-register-by-idx 
  "to allow use of the INDEX/DATA register pair, we need to have a way of 
  getting registers by their index, but starting from 1, not 0."
  [idx]
  (registers (dec idx)))


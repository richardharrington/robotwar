(ns robotwar.lexicon)

(def registers      [ "DATA" 
                      "A" "B" "C" "D" "E" "F" "G" "H" "I" "J" "K" "L" "M" 
                      "N" "O" "P" "Q" "R" "S" "T" "U" "V" "W" "X" "Y" "Z"
                      "AIM" "SHOT" "RADAR" "DAMAGE" "SPEEDX" "SPEEDY" "RANDOM" "INDEX" "DATA" ])

(def op-commands    [ "-" "+" "*" "/" "=" "#" "<" ">" ])
(def word-commands  [ "TO" "IF" "GOTO" "GOSUB" "ENDSUB" ])

(def commands (concat op-commands word-commands))


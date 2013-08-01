(ns robotwar.kernel-lexicon)

(def op-commands    [ "-" "+" "*" "/" "=" "#" "<" ">" ])
(def word-commands  [ "TO" "IF" "GOTO" "GOSUB" "ENDSUB" ])

(def commands (concat op-commands word-commands))


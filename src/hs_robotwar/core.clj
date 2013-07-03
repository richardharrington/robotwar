(ns hs-robotwar.core)

(defn add-column-metadata
  [s n]
  {:string s, 
   :column n})

(def registers (set (concat (map #(-> % char str) (range 65 91))
                            ["AIM" "SHOT" "RADAR" "DAMAGE" "SPEEDX" "SPEEDY" "RANDOM" "INDEX"])))

(def operators #{\= \< \> \# \+ \- \* \/})

(defn int-str?
  [string]
  (re-find #"^-?\d+$" string))

(defn lex
  [initial-line]
  (loop
    [line initial-line
     partial-token ""
     pos 0
     result []]
    (let [conj-to-result (fn [s] (conj result (add-column-metadata s pos)))
          new-pos (fn [] (- (count initial-line) (count line)))
          previous-token (last result)
          parsing-token? (not (empty? partial-token))
          first-char (first line)
          rest-of-line (rest line)]
      (cond
        (or (empty? line) (= \; first-char)) (if parsing-token?
                                                 (conj-to-result partial-token)
                                                 result)
        (#{\space \tab} first-char) (if parsing-token?
                                        (recur rest-of-line "" pos (conj-to-result partial-token))
                                        (recur rest-of-line "" pos result))
        (= \- first-char) (when (and (int-str? (str (second line)))
                                     (not (or (int-str? previous-token)
                                              (registers previous-token))))
                               ; if true => the minus sign is the beginning of a number
                            (recur rest-of-line (str first-char) (new-pos) result))
        (operators first-char) (recur rest-of-line "" (new-pos) (conj-to-result (str first-char)))
        :else (recur rest-of-line (str partial-token first-char) pos result)))))
  
  
(defn pretty-print-tokens [token-seq]
  (clojure.string/join 
    "\n"
    (map #(format "%2d %s" (:column %) (:string %)) 
         token-seq)))

(defn ppt [token-seq]
  (println (pretty-print-tokens token-seq)))


        
        
        

; (def error-type-map
;   {:parse-error "Parse Error"
;    :end-of-line-error "End of Line Error"})

; (defn error 
;   [error-type error-string]
;   (str (error-type-map error-type) ": " error-string))

; (defn valid-number-string
;   [n]
;   (re-find #"^-?\d+\.?\d*$" n))

; (defn number-or-string
;   [x]
;   (if (valid-number-string x)
;     (Float/parseFloat x)
;     x))

; (defn string-with-idx
;   [s, idx]
;   (if (empty? s)
;     nil
;     {:string s
;      :idx idx}))

; (defn tokens-with-column-numbers
;   "splits a string and returns a sequence of hashmaps,
;   with column-number metadata. separated just by spaces
;   for now. In the future, tokens won't necessarily have
;   spaces between them."
;   [s]
;   (let [len (count s)]
;     (loop [s s
;            idx 0
;            partial-word ""
;            result []]
;       (let [first-s (subs s 0 1)
;             rest-s (subs s 1)]
;         (cond
;           (empty? s) (if (empty? partial-word)
;                        result
;                        (conj result (str-with-idx partial-word idx))
;           (re-find #"\s" first-s) (if (empty? partial-word) 
;                                     (recur rest-s idx "" result)
;                                     (recur rest-s idx "" (conj result (str-with-idx
;                                                                         partial-word idx))))
;           :else (if (empty? partial-word)
;                   (recur rest-s (- len (count s)) first-s result)
;                   (recur rest-s idx (str partial-word first-s) result))))))))
          


; (defn validate-expr
;   "right now just checks to see whether it's a number.
;    will do a lot more things in the future."
;   [expr]
;   (if (number? expr)
;     [expr nil]
;     [expr (error :parse-error "Not a number")]))
  

; (defn evaluate
;   "evaluates an ast (right now just prints it).
;   Checks to see if it's a string because that's
;   the way errors are passed. We'll change that."
;   [ast]
;   (if (string? ast)
;     ast
;     (apply str (interpose " " ast))))
  

; (defn repl
;   "make it so"
;   []
;   (loop []
;     (println (evaluate (parse (lex (read-line)))))
;     (recur)))

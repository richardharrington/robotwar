(ns hs-robotwar.core)

(def registers (set (concat (map #(-> % char str) (range 65 91))
                            ["AIM" "SHOT" "RADAR" "DAMAGE" "SPEEDX" "SPEEDY" "RANDOM" "INDEX"])))

(def operators #{"=" "<" ">" "#" "+" "-" "*" "/"})

(defn int-str?
  [string]
  (re-find #"^-?\d+$" string))

(defn lex
  [initial-line]
  (loop
    [line initial-line
     partial-token ""
     saved-pos 0
     result []]
    (let [conj-with-metadata (fn [coll s n] 
                               (conj coll {:string s, :column n}))
          close-partial-token (fn [] (conj-with-metadata result partial-token saved-pos))
          current-pos (- (count initial-line) (count line))
          previous-token (last result)
          parsing-token? (not (empty? partial-token))
          head (str (first line))
          tail (rest line)]
      (println head (class head))
      (cond
        (or (empty? line) (= ";" head)) (if parsing-token?
                                          (close-partial-token)
                                          result)
        (#{" " "\t"} head) (if parsing-token?
                             (recur tail "" saved-pos (close-partial-token))
                             (recur tail "" saved-pos result))
        (= "-" head) (when (and (int-str? (str (second line)))
                                (not (or (int-str? (previous-token :string))
                                         (registers previous-token))))
                       ; when true => the minus sign is the beginning of a number
                       (recur tail head current-pos result))
        (operators head) (if parsing-token?
                           (recur line "" saved-pos (close-partial-token))
                           (recur tail "" current-pos (conj-with-metadata result head current-pos)))
        :else (recur tail (str partial-token head) saved-pos result)))))

  
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

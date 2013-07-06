(ns hs-robotwar.core)

(use '[clojure.core.match :only (match)])


(def registers (set (concat (map #(-> % char str) (range (int \A) (inc (int \Z))))
                            ["AIM" "SHOT" "RADAR" "DAMAGE" "SPEEDX" "SPEEDY" "RANDOM" "INDEX"])))

(def operators #{\= \< \> \# \+ \- \* \/})

(defn digit?
  [ch]
  (re-find #"\d" (str ch)))

(defn lex
  [initial-line]
  (loop
    [line initial-line
     partial-token []
     saved-pos 0
     result []]
    (let [conj-with-metadata (fn [coll s n] 
                               (conj coll {:token s, :column n}))
          close-partial-token (fn [] (conj-with-metadata result (apply str partial-token) saved-pos))
          current-pos (- (count initial-line) (count line))
          previous-token (:token (last result) "")
          parsing-token? (not (empty? partial-token))
          ; a binary operator is any character on the operator list,
          ; unless it's a minus sign that is actually a unary operator
          ; because the token before it could not be a number
          binary-op? #(and (operators %)
                           (or (not= % \-)
                               (digit? (last previous-token))
                               (registers previous-token)))
          head (first line)
          tail (rest line)]
      (match [head parsing-token?]
        [(:or \; nil) true ]          (close-partial-token)
        [(:or \; nil) false]          result
        [(:or \space \t) true ]       (recur tail [] nil (close-partial-token))
        [(:or \space \t) false]       (recur tail [] nil result)
        ; if it's an operator of any kind and we're currently parsing a token, 
        ; close the partial token and recur on the same character.
        [(_ :guard operators) true]   (recur line [] nil (close-partial-token))
        ; we don't have to check here if the binary operator is false because
        ; if it weren't, it would have been caught by the less restrictive line above.
        [(_ :guard binary-op?) _]     (recur tail 
                                             [] 
                                             nil 
                                             (conj-with-metadata result (str head) current-pos))
        [_ true ]                     (recur tail (conj partial-token head) saved-pos result)
        [_ false]                     (recur tail (conj partial-token head) current-pos result)))))


(def parse
  "will be filled in later -- right now just a pass-through for the repl"
  identity)

(defn pretty-print-tokens [token-seq]
  (clojure.string/join 
    "\n"
    (map #(format "%2d %s" (:column %) (:token %)) 
         token-seq)))

(defn evaluate [token-seq]
  (println (pretty-print-tokens token-seq)))


(defn repl
  "make it so"
  []
  (loop [input (read-line)]
    (when (not= input "exit")
      (println (evaluate (parse (lex input))))
      (recur (read-line)))))

  

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
;     {:token s
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
  


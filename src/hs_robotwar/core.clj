(ns hs-robotwar.core)

(use '[clojure.core.match :only (match)])
(use '[clojure.set :only (union)])
(use '[clojure.string :only (split join)])

(def operators "-+*/=#<>")

(def lex-re (re-pattern (str  "[" operators "]|[^" operators "\\s]+")))
(def operators-set (set (map str operators)))

(def registers (union (set (map #(-> % char str) (range (int \A) (inc (int \Z)))))
                      #{"AIM" "SHOT" "RADAR" "DAMAGE" "SPEEDX" "SPEEDY" "RANDOM" "INDEX"}))

(def commands (union operators-set #{"TO" "IF" "GOTO" "GOSUB" "ENDSUB"})) 

; TODO: FINISH THIS METHOD, AND CONVERT THE WHOLE LEXER TO REGEX
(defn re-seq-with-pos
  [initial-s]
  (let [len (count initial-s)]
    (loop [s initial-s, pos 0, acc []]
      (cond
        (empty? s) acc
        (




(defn conj-with-metadata 
  [coll s n] 
    (conj coll {:token-str s, :pos n}))

(defn lex-line
  [initial-line]
  (loop
    [[head & tail :as line] initial-line
     partial-token []
     saved-pos 0
     result []]
    (let [close-partial-token (fn [] (conj-with-metadata result (apply str partial-token) saved-pos))
          current-pos (- (count initial-line) (count line))
          parsing-token? (not (empty? partial-token))]
      (match [head parsing-token?]
        [(:or \; nil) true ]          (close-partial-token)
        [(:or \; nil) false]          result
        [(:or \space \t) true ]       (recur tail [] nil (close-partial-token))
        [(:or \space \t) false]       (recur tail [] nil result)
        ; if it's an operator and we're currently parsing a token, 
        ; close the partial token and recur on the same character.
        [(_ :guard operators) true ]  (recur line [] nil (close-partial-token))
        [(_ :guard operators) false]  (recur tail 
                                             [] 
                                             nil 
                                             (conj-with-metadata result (str head) current-pos))
        [_ true ]                     (recur tail (conj partial-token head) saved-pos result)
        [_ false]                     (recur tail (conj partial-token head) current-pos result)))))

(defn merge-lexed-tokens
  "helper function for conjoining minus signs to next token
  if they turn out to be unary negative signs"
  [first-token second-token]
  {:token-str (str (:token-str first-token) (:token-str second-token))
   :pos (:pos first-token)})

(defn lex
  [src-code]
  (mapcat lex-line (split src-code #"\n")))

(defn str->int
  "Like Integer/parseInt, but returns nil on failure"
  [s]
  (and (re-find #"^-?\d+$" s)
       (Integer/parseInt s)))

(defn valid-word
   "Capital letters and numbers, starting with a capital letter"
  [s]
  (re-find #"^[A-Z]+\d*$" s))

(defn ignoring-args-thunk [x] (fn [& _] x))

(def return-err (ignoring-args-thunk "Invalid word or symbol"))

(defn parse-token
  "takes a single token and adds the appropriate metadata"
  [{token-str :token-str, pos :pos}]
  (some
    (fn [[parser token-type]]
      (when-let [token-val (parser token-str)]
        {:val token-val, :type token-type, :pos pos}))
    [[registers  :register]
     [commands   :command]
     [str->int   :number]
     [valid-word :label]
     [return-err  :error]]))

(def value-type? #{:number :register})

(defn parse
  "take the tokens and convert them to structured source code ready for compiling"
  [initial-tokens]
  (loop [parsed []
         [{token-str :token-str :as token} & tail :as tokens] initial-tokens]
    (let [previous-parsed-token (last parsed)]
      (cond
        (or (empty? tokens) (= (:type previous-parsed-token) :error))
          parsed
        ; deal with unary negative signs
        (and (= token-str "-") (not-empty tail) (not (value-type? (:type previous-parsed-token)))) 
          (recur parsed (cons (merge-lexed-tokens token (first tail)) (rest tail)))
        :otherwise
          (recur (conj parsed (parse-token token)) tail)))))


(defn pretty-print-tokens [token-seq]
  (join 
    "\n"
    (map #(format "%2d %s %s" (:pos %) (:type %) (:val %)) 
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



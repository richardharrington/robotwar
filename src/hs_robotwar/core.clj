(ns hs-robotwar.core)

(use '[clojure.core.match :only (match)])
(use '[clojure.set :only (union)])


(def operators #{\= \< \> \# \+ \- \* \/})

(def registers (union (set (map #(-> % char str) (range (int \A) (inc (int \Z)))))
                      #{"AIM" "SHOT" "RADAR" "DAMAGE" "SPEEDX" "SPEEDY" "RANDOM" "INDEX"}))

(def commands (union (set (map str operators))
                     #{"TO" "IF" "GOTO" "GOSUB" "ENDSUB"})) 

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
          previous-token (:token-str (last result) "")
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

(defn lex
  [src-code]
  (mapcat lex-line (clojure.string/split src-code #"\n")))

(defn str->int
  "Like Integer/parseInt, but returns nil on failure"
  [s]
  (and (re-find #"^-?\d+$" s)
       (Integer/parseInt s)))

(defn valid-word
  "Capital letters and numbers, starting with a capital letter"
  [s]
  (re-find #"^[A-Z]+\d*$" s))

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
     [identity   :error]]))



;(def parse
;  "take the tokens and convert them to structured source code ready for compiling"
;  [initial-tokens]
;  (loop [[{token-str :token-str :as head} & tail :as tokens] initial-tokens
;         parsed []]
;    (match [token-str]
;      ["-"] (if (value? (:type (last parsed)))
;              (recur (conj parsed ()

(def parse
  "temporary pipe connector -- to be deleted"
  identity)

(defn pretty-print-tokens [token-seq]
  (clojure.string/join 
    "\n"
    (map #(format "%2d %s" (:pos %) (:token-str %)) 
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



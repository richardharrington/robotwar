(ns hs-robotwar.core)

(use '[clojure.core.match :only (match)])


(def registers (set (concat (map #(-> % char str) (range (int \A) (inc (int \Z))))
                            ["AIM" "SHOT" "RADAR" "DAMAGE" "SPEEDX" "SPEEDY" "RANDOM" "INDEX"])))

(def operators #{\= \< \> \# \+ \- \* \/})


(defn conj-with-metadata 
  [coll s n] 
    (conj coll {:token s, :pos n}))

(defn lex-line
  [initial-line]
  (loop
    [line initial-line
     partial-token []
     saved-pos 0
     result []]
    (let [close-partial-token (fn [] (conj-with-metadata result (apply str partial-token) saved-pos))
          current-pos (- (count initial-line) (count line))
          previous-token (:token (last result) "")
          parsing-token? (not (empty? partial-token))
          head (first line)
          tail (rest line)]
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

(def parse
  "will be filled in later -- right now just a pass-through for the repl"
  identity)

(defn compile-to-obj-code
  "takes a stream of tokens and converts them into robotwar virtual machine code"
  [tokens]
  (loop [tokens tokens
         done? false
         obj-code []]
    ))

(defn pretty-print-tokens [token-seq]
  (clojure.string/join 
    "\n"
    (map #(format "%2d %s" (:pos %) (:token %)) 
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

 

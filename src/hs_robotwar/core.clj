(ns hs-robotwar.core)

(use '[clojure.set :only (union)])
(use '[clojure.string :only (split join)])

(def operators #{ "-" "+" "*" "/" "=" "#" "<" ">"})

(def registers #{ "A" "B" "C" "D" "E" "F" "G" "H" "I" "J" "K" "L" "M"
                  "N" "O" "P" "Q" "R" "S" "T" "U" "V" "W" "X" "Y" "Z"
                  "AIM" "SHOT" "RADAR" "DAMAGE" "SPEEDX" "SPEEDY" "RANDOM" "INDEX"})

(def commands (union operators 
                     #{"TO" "IF" "GOTO" "GOSUB" "ENDSUB"}))

(defn re-seq-with-pos
  "Returns a lazy sequence of successive matches of pattern in string with position.
  Largely stolen from re-seq source."
  [^java.util.regex.Pattern re s]
  (let [m (re-matcher re s)]
    ((fn step []
       (when (.find m)
         (cons [(re-groups m) (.start m)] (lazy-seq (step))))))))

(defn strip-comments
  [line]
  (re-find #"[^;]*" line))

(def lex-re 
  (let [opstring (join operators)]
    (re-pattern (str "[" opstring "]|[^" opstring "\\s]+"))))

(defn lex-line
  [line]
  (map (fn [[s n]] {:token-str s, :pos n}) 
       (re-seq-with-pos lex-re line)))

(defn lex
  [src-code]
  (mapcat lex-line (split src-code #"\n")))

(defn merge-lexed-tokens
  "helper function for conjoining minus signs to next token
  if they turn out to be unary negative signs"
  [first-token second-token]
  {:token-str (str (:token-str first-token) (:token-str second-token))
   :pos (:pos first-token)})

(defn str->int
  "Integer/parseInt, but returns nil on failure"
  [s-raw]
  (try (Integer/parseInt s-raw)
       (catch Exception e nil)))

(defn valid-word
   "Capital letters and numbers, starting with a capital letter"
  [s]
  (re-matches #"^[A-Z][A-Z\d]*" s))

(def return-err (constantly "Invalid word or symbol"))

(def parser-priority
 [[registers  :register]
  [commands   :command]
  [str->int   :number]
  [valid-word :label]
  [return-err :error]])

(defn parse-token
  [{:keys [token-str pos]}]
  (loop [[[parser token-type] & tail] parser-priority]
    (if-let [token-val (parser token-str)]
      {:val token-val, :type token-type, :pos pos}
      (recur tail))))

(defn parse
  "take the tokens and convert them to strucured source code ready for compiling"
  [tokens]
  (reduce (fn [parsed token]
            (if (= (:type (last parsed)) :error)
              parsed
              (conj parsed (parse-token token))))
          []
          tokens))

(def value-type? #{:number :register})

(defn disambiguate-minus-signs
  [initial-tokens]
  (loop [tokens initial-tokens
         acc []]
    (let [{prev-type :type} (last acc)
          {current-val :val, current-pos :pos :as current-token} (first tokens)
          {next-val :val, next-type :type :as next-token} (second tokens)]
      (cond
        (empty? tokens) acc
        (and (not (value-type? prev-type)) (= current-val "-") (= next-type :number))
          (recur (rest (rest tokens)) 
                 (conj acc {:val (- next-val), :pos current-pos, :type :number})) 
        :otherwise (recur (rest tokens) (conj acc current-token))))))

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
      (-> input
          strip-comments
          lex
          parse
          disambiguate-minus-signs
          evaluate)
      (recur (read-line)))))



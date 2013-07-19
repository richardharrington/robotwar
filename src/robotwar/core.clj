(ns robotwar.core)

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

(defn disambiguate-minus-signs
  [initial-tokens]
  (loop [tokens initial-tokens
         results []]
    (let [{prev-type :type} (last results)
          {current-val :val, current-pos :pos :as current-token} (first tokens)
          {next-val :val, next-type :type :as next-token} (second tokens)]
      (cond
        (empty? tokens) results
        (and (not (#{:number :register} prev-type)) (= current-val "-") (= next-type :number))
          (recur (rest (rest tokens)) 
                 (conj results {:val (- next-val), :pos current-pos, :type :number})) 
        :otherwise (recur (rest tokens) (conj results current-token))))))

(defn rw-compile
  "Compiles the tokens into token-pairs. Commands consume the next token.
  Values form the special token-pair comma-value 
  (meaning push the value into the accumulator)"
  [initial-tokens]
  (loop [[token & tail :as tokens] initial-tokens
         result []]
    (if (empty? tokens)
      result
      (case (:type token)
        :command             (recur (rest tail) (conj result [token (first tail)]))
        (:number :register)  (recur tail (conj result [{:val ",", :type :command, :pos (:pos token)} token]))
        :label               (recur tail (conj result [token nil]))))))

(defn pretty-print-tokens [token-seq]
  "This is hacky and just a temporary display function"
  (letfn [(f [[fst snd]]
            (if snd
              (format "%2d %9s %8s %20d %9s %11s"
                      (:pos fst) (:type fst) (:val fst) 
                      (:pos snd) (:type snd) (:val snd))
              (format "%2d %9s %8s"
                      (:pos fst) (:type fst) (:val fst))))]
    (join "\n" (map f token-seq))))

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
          rw-compile
          pretty-print-tokens
          println)
      (recur (read-line)))))



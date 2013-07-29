(ns robotwar.create
  (:refer-clojure :exclude [compile])
  (:use (clojure [string :only [split join]] 
                 [pprint :only [pprint]])
        [robotwar.lexicon :only [registers op-commands commands]]))

(defn re-seq-with-pos
  "Returns a lazy sequence of successive matches of pattern in string with position.
  Largely stolen from re-seq source."
  [^java.util.regex.Pattern re s]
  (let [m (re-matcher re s)]
    ((fn step []
       (when (.find m)
         (cons [(re-groups m) (.start m)] (lazy-seq (step))))))))

(defn split-lines
  [raw-lines]
  (split raw-lines #"\n"))

(defn strip-comments
  [lines]
  (map #(re-find #"[^;]*" %) lines))

(def lex-re 
  (let [op-string (join op-commands)]
    (re-pattern (str "[" op-string "]|[^" op-string "\\s]+"))))

(defn lex-line
  "Helper function for lex. Note: :line and :pos
  are intended to be human-readable for error-reporting
  purposes, so they're indexed from 1."
  [line-num line]
  (map (fn [[s n]] 
         {:token-str s, :line (inc line-num), :pos (inc n)}) 
       (re-seq-with-pos lex-re line)))

(defn lex
  "Lexes a sequence of lines. After this point, line numbers
  are captured as metadata and tokens are no longer grouped by line."
  [lines]
  (apply concat (map-indexed lex-line lines)))

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
 [[(set registers)  :register]
  [(set commands)   :command]
  [str->int         :number]
  [valid-word       :label]
  [return-err       :error]])

(defn parse-token
  [{:keys [token-str pos line]}]
  (loop [[[parser token-type] & tail] parser-priority]
    (if-let [token-val (parser token-str)]
      {:val token-val, :type token-type, :pos pos :line line}
      (recur tail))))

(defn parse
  "take the tokens and convert them to structured source code ready for compiling"
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
          {current-val :val, current-pos :pos, current-line :line :as current-token} (first tokens)
          {next-val :val, next-type :type :as next-token} (second tokens)]
      (cond
        (empty? tokens) results
        (and (not (#{:number :register} prev-type)) (= current-val "-") (= next-type :number))
          (recur (rest (rest tokens)) 
                 (conj results {:val (- next-val), :pos current-pos, :line current-line, :type :number})) 
        :otherwise (recur (rest tokens) (conj results current-token))))))

(defn make-instr-pairs
  "Compiles the tokens into token-pairs. Commands consume the next token.
  Values form the special token-pair that is a comma followed by a value 
  (meaning push the value into the accumulator)"
  [initial-tokens]
  (loop [[token & tail :as tokens] initial-tokens
         result []]
    (if (empty? tokens)
      result
      (case (:type token)
        :command             (recur (rest tail) (conj result [token (first tail)]))
        (:number :register)  (recur tail (conj result [{:val ",", :type :command, :pos (:pos token), :line (:line token)} token]))
        :label               (recur tail (conj result [token nil]))))))

(defn map-labels
  "Maps label-names to their appropriate indexes in the instruction list,
  and remove the labels from the instruction list itself (except as targets)"
  [initial-instrs]
  (loop [[instr & tail :as instrs] initial-instrs
         {label-map :labels instr-vec :instrs :as result} {:labels {}, :instrs []}
         idx 0]
    (if (empty? instrs)
      result
      (let [{command-type :type, command-val :val} (first instr)]
        (if (= command-type :label) 
          (recur tail 
                 {:labels (assoc label-map command-val idx), :instrs instr-vec} 
                 idx)
          (recur tail
                 {:labels label-map, :instrs (conj instr-vec instr)}
                 (inc idx)))))))

(defn compile [string]
  (-> string 
      split-lines
      strip-comments 
      lex 
      parse 
      disambiguate-minus-signs 
      make-instr-pairs 
      map-labels))

(defn repl
  "make it so"
  []
  (loop [input (read-line)]
    (when (not= input "exit")
      (-> input compile pprint)
      (recur (read-line)))))


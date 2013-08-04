(ns robotwar.foundry
  (:require [robotwar.kernel-lexicon])
  (:use (clojure [string :only [split join]] 
                 [pprint :only [pprint]])
        [clojure.core.match :only [match]]))

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
  (let [op-string (join robotwar.kernel-lexicon/op-commands)]
    (re-pattern (str "[" op-string "]|[^" op-string "\\s]+"))))

(defn lex-line
  "Helper function for lex. Note: metadata fields :line and :pos
  are intended to be human-readable for error-reporting
  purposes, so they're indexed from 1."
  [line-num line]
  (map (fn [[s n]] 
         ^{:line (inc line-num), :pos (inc n)} {:token-str s}) 
       (re-seq-with-pos lex-re line)))

(defn lex
  "Lexes a sequence of lines. After this point, tokens 
  are no longer grouped by line (line numbers have been 
  captured in metadata, along with column numbers)."
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

(defn parse-token
  "takes a vector of reg-names and a token with a token-str field and parses the token.
  needs to work with the original token map by using dissoc and into
  (rather than building a new one) because it contains line and column
  number metadata."
  [reg-names {token-str :token-str :as token}]
  (let [parser-priority 
        [[(set reg-names)  :register]
         [(set robotwar.kernel-lexicon/commands) :command]
         [str->int         :number]
         [valid-word       :label]
         [return-err       :error]]]
    (some
      (fn [[parser token-type]]
        (when-let [token-val (parser token-str)]
          (dissoc (into token {:val token-val, :type token-type})
                  :token-str)))
        parser-priority)))

(defn parse
  "take the tokens and convert them to structured source code ready for compiling.
  if there's an error, returns a different type: just the token,
  outside of any sequence."
  [reg-names initial-tokens]
  (loop [[token & tail :as tokens] initial-tokens
         parsed-tokens []]
    (if (empty? tokens)
      parsed-tokens
      (let [{token-type :type :as parsed-token} (parse-token reg-names token)]
        (if (= token-type :error)
          parsed-token
          (recur tail (conj parsed-tokens parsed-token)))))))

(defn disambiguate-minus-signs
  [initial-tokens]
  (loop [tokens initial-tokens
         results []]
    (let [{prev-type :type} (last results)
          [{current-val :val :as current-token} 
           & [{next-val :val, next-type :type :as next-token} :as tail]] tokens]
      (cond
        (empty? tokens) results
        (and (= current-val "-") 
             (= next-type :number) 
             (not (#{:number :register} prev-type)))
          (recur (rest tail) 
                 (conj results (into current-token {:val (- next-val), :type :number}))) 
        :otherwise (recur tail (conj results current-token))))))

(defn make-instr-pairs
  "Compiles the tokens into token-pairs. Commands consume the next token.
  When values are encountered that are not arguments to commands, 
  a special token-pair is created that is a comma followed by the value 
  (meaning push the value into the accumulator). The comma command re-uses
  the same :line and :pos metadata from the token containing the value that is being pushed."
  [initial-tokens]
  (loop [[token & tail :as tokens] initial-tokens
         result []]
    (if (empty? tokens)
      result
      (match [token]
        [{:type (:or :number :register)}] 
          (recur tail (conj result [(into token {:val ",", :type :command}) token]))
        [(:or {:type :label} {:type :command, :val "ENDSUB"})]
          (recur tail (conj result [token nil]))
        [{:type :command}] 
          (recur (rest tail) (conj result [token (first tail)]))))))

; TODO: preserve :line and :pos metadata with labels,
; when labels are transferred from the instruction list to the label map

(defn map-labels
  "Maps label-names to their appropriate indexes in the instruction list,
  and remove the labels from the instruction list itself (except as targets)"
  [initial-instrs]
  (loop [[instr & tail :as instrs] initial-instrs
         result {:labels {} 
                 :instrs []}]
    (if (empty? instrs)
      result
      (let [command (first instr)
            next-instr-num (count (result :instrs))]
        (if (= (command :type) :label) 
          (recur tail (assoc-in result [:labels (command :val)] next-instr-num))
          (recur tail (assoc-in result [:instrs next-instr-num] instr)))))))

(defn assemble [reg-names string]
  "compiles robotwar code, with error-checking beginning after the lexing
  step. All functions that return errors will return a map with the keyword 
  :error, and then a token with a :val field containing the error string, 
  and metadata containing :pos and :line fields containing the location. 
  So far only parse implements error-checking."
  (let [parse-with-reg-names (partial parse reg-names)
        lexed (-> string split-lines strip-comments lex)]
   (reduce (fn [result step]
             (if (= (:type result) :error)
               result
               (step result)))
           lexed
           [parse-with-reg-names disambiguate-minus-signs make-instr-pairs map-labels])))

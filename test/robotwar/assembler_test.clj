(ns robotwar.assembler-test
  (:use (clojure [string :only [join]]
                 [test])
        [robotwar.assembler])
  (:require [robotwar.game-lexicon :as game-lexicon]))

(def line1 "IF DAMAGE # D GOTO MOVE    ; comment or something")
(def line2 "AIM-17 TO AIM              ; other comment")
(def line3 "IF X<-5 GOTO SCAN          ; third comment")

(def line4 "6 to RADAR") ; this will be an error: no lower-case

(def line-no-comments1 "IF DAMAGE # D GOTO MOVE")
(def line-no-comments2 "AIM-17 TO AIM")
(def line-no-comments3 "IF X<-5 GOTO SCAN")

(def multi-line ["SCAN" "6 TO AIM"])
(def lexed-multi-line [{:token-str "SCAN"}
                       {:token-str "6"}
                       {:token-str "TO"}
                       {:token-str "AIM"}])

(def lexed-tokens1 [{:token-str "IF"} 
                    {:token-str "DAMAGE"} 
                    {:token-str "#"} 
                    {:token-str "D"} 
                    {:token-str "GOTO"} 
                    {:token-str "MOVE"}])

(def lexed-tokens2 [{:token-str "AIM"} 
                    {:token-str "-"} 
                    {:token-str "17"} 
                    {:token-str "TO"} 
                    {:token-str "AIM"}])

(def lexed-tokens3 [{:token-str "IF"} 
                    {:token-str "X"} 
                    {:token-str "<"} 
                    {:token-str "-"} 
                    {:token-str "5"} 
                    {:token-str "GOTO"} 
                    {:token-str "SCAN"}])

(def lexed-tokens4 [{:token-str "AIM"} 
                    {:token-str "@"} 
                    {:token-str "17"} 
                    {:token-str "TO"} 
                    {:token-str "AIM"}])

(def parsed-tokens2 [{:val "AIM", :type :register} 
                     {:val "-", :type :command} 
                     {:val 17, :type :number} 
                     {:val "TO", :type :command} 
                     {:val "AIM", :type :register}])

(def parsed-tokens3 [{:val "IF", :type :command} 
                     {:val "X", :type :register} 
                     {:val "<", :type :command} 
                     {:val "-", :type :command} 
                     {:val 5, :type :number} 
                     {:val "GOTO", :type :command} 
                     {:val "SCAN", :type :label}])

(def parsed-tokens4 {:val "Invalid word or symbol", :type :error})

(def minus-sign-disambiguated-tokens3 [{:val "IF", :type :command} 
                                       {:val "X", :type :register} 
                                       {:val "<", :type :command} 
                                       {:val -5, :type :number} 
                                       {:val "GOTO", :type :command} 
                                       {:val "SCAN", :type :label}])

(def minus-sign-disambiguated-tokens6 [{:val "WAIT", :type :label} 
                                       {:val "IF", :type :command} 
                                       {:val "X", :type :register} 
                                       {:val "<", :type :command} 
                                       {:val -5, :type :number} 
                                       {:val "GOTO", :type :command} 
                                       {:val "SCAN", :type :label}])

(def minus-sign-disambiguated-tokens7 [{:val "ENDSUB", :type :command}
                                       {:val 13, :type :number}
                                       {:val "TO", :type :command}
                                       {:val "Y", :type :register}])

(def instr-pairs3 [[{:type :command, :val "IF"} 
                    {:type :register, :val "X"}] 
                   [{:type :command, :val "<"} 
                    {:type :number, :val -5}] 
                   [{:type :command, :val "GOTO"} 
                    {:type :label, :val "SCAN"}]])

(def instr-pairs6 [[{:val "WAIT", :type :label} 
                    nil] 
                   [{:val "IF", :type :command} 
                    {:val "X", :type :register}] 
                   [{:val "<", :type :command} 
                    {:val -5, :type :number}] 
                   [{:val "GOTO", :type :command} 
                    {:val "SCAN", :type :label}]])

(def instr-pairs7 [[{:val "ENDSUB", :type :command} nil]
                   [{:val ",", :type :command}
                    {:val 13, :type :number}]
                   [{:val "TO", :type :command}
                    {:val "Y", :type :register}]])

(def labels-mapped3 {:labels {}, 
                     :instrs [[{:type :command, :val "IF"} 
                               {:type :register, :val "X"}] 
                              [{:type :command, :val "<"} 
                               {:type :number, :val -5}] 
                              [{:type :command, :val "GOTO"} 
                               {:type :label, :val "SCAN"}]]})

(def labels-mapped6 {:labels {"WAIT" 0}, 
                     :instrs [[{:type :command, :val "IF"} 
                               {:type :register, :val "X"}] 
                              [{:type :command, :val "<"} 
                               {:type :number, :val -5}] 
                              [{:type :command, :val "GOTO"} 
                               {:type :label, :val "SCAN"}]]})

(def multi-line-assembled 
  {:labels {},
   :instrs
   [[{:val "IF", :type :command}
     {:val "DAMAGE", :type :register}]
    [{:val "#", :type :command}
     {:val "D", :type :register}]
    [{:val "GOTO", :type :command}
     {:val "MOVE", :type :label}]
    [{:val ",", :type :command}
     {:val "AIM", :type :register}]
    [{:val "-", :type :command}
     {:val 17, :type :number}]
    [{:val "TO", :type :command}
     {:val "AIM", :type :register}]
    [{:val "IF", :type :command}
     {:val "X", :type :register}]
    [{:val "<", :type :command}
     {:val -5, :type :number}]
    [{:val "GOTO", :type :command}
     {:val "SCAN", :type :label}]]})

(def multi-line-assembled-error 
  {:val "Invalid word or symbol", :type :error})


; And now for the tests.
;
(deftest strip-comments-test
  (testing "stripping comments"
    (is (= (strip-comments [line1])
           ["IF DAMAGE # D GOTO MOVE    "]))))

(deftest strip-comments-multiline-test
  (testing "stripping comments multi-line"
    (is (= (strip-comments [line1 line2 line3])
           ["IF DAMAGE # D GOTO MOVE    "
            "AIM-17 TO AIM              "
            "IF X<-5 GOTO SCAN          "]))))

(deftest lex-simple
  (testing "lexing of simple line"
    (is (= (lex [line-no-comments1]) 
           lexed-tokens1))))

(deftest lex-scrunched-chars
  (testing "lexing with no whitespace between operators and operands"
    (is (= (lex [line-no-comments2])
           lexed-tokens2)))) 

(deftest lex-negative-numbers
  (testing "lexing with unary negative operator"
    (is (= (lex [line-no-comments3])
           lexed-tokens3))))

(deftest lex-multi-line
  (testing "lexing multiple lines"
    (is (= (lex multi-line)
           lexed-multi-line))))

(deftest str->int-fail
  (testing "failure of str->int"
    (is (= (str->int "G")
           nil))))

(deftest str->int-success-positive-number
  (testing "str->int with positive number"
    (is (= (str->int "8")
           8))))

(deftest str->int-success-negative-number
  (testing "str->int with negative number"
    (is (= (str->int "-9")
           -9))))

(deftest valid-word-fail-because-lower-case
  (testing "word not valid because lower case"
    (is (= (valid-word "Beatles")
           nil))))

(deftest valid-word-fail-because-starts-with-number
  (testing "word not valid because starts with number"
    (is (= (valid-word "7BEATLES")))))

(deftest valid-word-success
  (testing "valid word"
    (is (= (valid-word "BEATLES7")
           "BEATLES7"))))

(deftest parse-token-register
  (testing "parsing register token"
    (is (= (parse-token {:token-str "AIM"} game-lexicon/reg-names)
           {:val "AIM", :type :register}))))

(deftest parse-token-command-word
  (testing "parsing command token (word)"
    (is (= (parse-token {:token-str "GOTO"} game-lexicon/reg-names)
           {:val "GOTO", :type :command}))))

(deftest parse-token-command-operator
  (testing "parsing command token (operator)"
    (is (= (parse-token {:token-str "#"} game-lexicon/reg-names)
           {:val "#", :type :command}))))

(deftest parse-token-number
  (testing "parsing number token"
    (is (= (parse-token {:token-str "-17"}game-lexicon/reg-names)
           {:val -17, :type :number}))))

(deftest parse-token-label
  (testing "parsing label token"
    (is (= (parse-token {:token-str "SCAN"} game-lexicon/reg-names)
           {:val "SCAN", :type :label}))))

(deftest parse-token-error
  (testing "parsing error token"
    (is (= (parse-token {:token-str "-GOTO"} game-lexicon/reg-names)
           {:val "Invalid word or symbol", :type :error}))))

(deftest parse-tokens-minus-sign
  (testing "parsing tokens with a binary minus sign"
    (is (= (parse lexed-tokens2 game-lexicon/reg-names)
           parsed-tokens2))))

(deftest parse-tokens-negative-sign
  (testing "parsing tokens with a unary negative sign"
    (is (= (parse lexed-tokens3 game-lexicon/reg-names)
           parsed-tokens3))))

(deftest parse-tokens-error
  (testing "parsing tokens with an invalid operator"
    (is (= (parse lexed-tokens4 game-lexicon/reg-names)
           parsed-tokens4))))

(def minus-sign-disambiguated-tokens2 parsed-tokens2)

(deftest disambiguate-minus-signs-binary
  (testing "disambiguating minus signs, result should be subtraction operator"
    (is (= (disambiguate-minus-signs parsed-tokens2)
           parsed-tokens2))))

(deftest disambiguate-minus-signs-unary
  (testing "disambiguating minus signs, result should be unary negative sign"
    (is (= (disambiguate-minus-signs parsed-tokens3)
           minus-sign-disambiguated-tokens3))))

(deftest instr-pairs-no-label
  (testing "instruction pairs with no starting label"
    (is (= (make-instr-pairs minus-sign-disambiguated-tokens3)
           instr-pairs3))))

(deftest instr-pairs-with-label
  (testing "instruction pairs with starting label"
    (is (= (make-instr-pairs minus-sign-disambiguated-tokens6)
           instr-pairs6))))

(deftest instr-pairs-with-endsub
  (testing "instruction pairs with endsub (which takes no argument)"
    (is (= (make-instr-pairs minus-sign-disambiguated-tokens7)
           instr-pairs7))))

(deftest map-labels-no-label
  (testing "mapping labels from instruction pairs with no label"
    (is (= (map-labels instr-pairs3)
           labels-mapped3))))

(deftest map-labels-with-label
  (testing "mapping labels from instruction pairs with starting label"
    (is (= (map-labels instr-pairs6)
           labels-mapped6))))

(deftest assemble-test-success
  (testing "compiling successfully"
    (is (= (assemble (join "\n" [line1 line2 line3]) game-lexicon/reg-names)
           multi-line-assembled))))

(deftest assemble-test-failure
  (testing "assemble results in error"
    (is (= (assemble (join "\n" [line1 line2 line3 line4]) game-lexicon/reg-names)
           multi-line-assembled-error))))

(deftest preserving-line-and-pos-metadata-test
  (testing "line and pos metadata preserved through assembly process"
    (is (= (meta (get-in (assemble (join "\n" [line1 line2 line3]) game-lexicon/reg-names)
                         [:instrs 8 1]))
           {:line 3, :pos 14}))))



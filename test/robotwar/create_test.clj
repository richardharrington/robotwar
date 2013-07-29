(ns robotwar.create-test
  (:refer-clojure :exclude [compile])
  (:require [clojure.test :refer :all]
            [robotwar.create :refer :all])
  (:use [clojure.string :only [join]]))

(def line1 "IF DAMAGE # D GOTO MOVE    ; comment or something")
(def line2 "AIM-17 TO AIM              ; other comment")
(def line3 "IF X<-5 GOTO SCAN          ; third comment")

(def line4 "6 to RADAR") ; this will be an error: no lower-case

(def line-no-comments1 "IF DAMAGE # D GOTO MOVE")
(def line-no-comments2 "AIM-17 TO AIM")
(def line-no-comments3 "IF X<-5 GOTO SCAN")

(def multi-line ["SCAN" "6 TO AIM"])
(def lexed-multi-line [{:token-str "SCAN", :line 1, :pos 1}
                       {:token-str "6", :line 2, :pos 1}
                       {:token-str "TO", :line 2, :pos 3}
                       {:token-str "AIM", :line 2, :pos 6}])

(def lexed-tokens1 [{:token-str "IF", :line 1, :pos 1} 
                    {:token-str "DAMAGE", :line 1, :pos 4} 
                    {:token-str "#", :line 1, :pos 11} 
                    {:token-str "D", :line 1, :pos 13} 
                    {:token-str "GOTO", :line 1, :pos 15} 
                    {:token-str "MOVE", :line 1, :pos 20}])

(def lexed-tokens2 [{:token-str "AIM", :line 1, :pos 1} 
                    {:token-str "-", :line 1, :pos 4} 
                    {:token-str "17", :line 1, :pos 5} 
                    {:token-str "TO", :line 1, :pos 8} 
                    {:token-str "AIM", :line 1, :pos 11}])

(def lexed-tokens3 [{:token-str "IF", :line 1, :pos 1} 
                    {:token-str "X", :line 1, :pos 4} 
                    {:token-str "<", :line 1, :pos 5} 
                    {:token-str "-", :line 1, :pos 6} 
                    {:token-str "5", :line 1, :pos 7} 
                    {:token-str "GOTO", :line 1, :pos 9} 
                    {:token-str "SCAN", :line 1, :pos 14}])

(def lexed-tokens4 [{:token-str "AIM", :line 1, :pos 1} 
                    {:token-str "@", :line 1, :pos 4} 
                    {:token-str "17", :line 1, :pos 5} 
                    {:token-str "TO", :line 1, :pos 8} 
                    {:token-str "AIM", :line 1, :pos 11}])

(def parsed-tokens2 [{:val "AIM", :type :register, :line 1, :pos 1} 
                     {:val "-", :type :command, :line 1, :pos 4} 
                     {:val 17, :type :number, :line 1, :pos 5} 
                     {:val "TO", :type :command, :line 1, :pos 8} 
                     {:val "AIM", :type :register, :line 1, :pos 11}])

(def parsed-tokens3 [{:val "IF", :type :command, :line 1, :pos 1} 
                     {:val "X", :type :register, :line 1, :pos 4} 
                     {:val "<", :type :command, :line 1, :pos 5} 
                     {:val "-", :type :command, :line 1, :pos 6} 
                     {:val 5, :type :number, :line 1, :pos 7} 
                     {:val "GOTO", :type :command, :line 1, :pos 9} 
                     {:val "SCAN", :type :label, :line 1, :pos 14}])

(def parsed-tokens4 {:val "Invalid word or symbol", :type :error, :line 1, :pos 4})

(def minus-sign-disambiguated-tokens3 [{:val "IF", :type :command, :line 1, :pos 1} 
                                       {:val "X", :type :register, :line 1, :pos 4} 
                                       {:val "<", :type :command, :line 1, :pos 5} 
                                       {:val -5, :type :number, :line 1, :pos 6} 
                                       {:val "GOTO", :type :command, :line 1, :pos 9} 
                                       {:val "SCAN", :type :label, :line 1, :pos 14}])

(def minus-sign-disambiguated-tokens6 [{:val "WAIT", :type :label, :line 1, :pos 1} 
                                       {:val "IF", :type :command, :line 1, :pos 6} 
                                       {:val "X", :type :register, :line 1, :pos 9} 
                                       {:val "<", :type :command, :line 1, :pos 10} 
                                       {:val -5, :line 1, :pos 11, :type :number} 
                                       {:val "GOTO", :type :command, :line 1, :pos 14} 
                                       {:val "SCAN", :type :label, :line 1, :pos 19}])

(def instr-pairs3 [[{:line 1, :pos 1, :type :command, :val "IF"} 
                    {:line 1, :pos 4, :type :register, :val "X"}] 
                   [{:line 1, :pos 5, :type :command, :val "<"} 
                    {:line 1, :pos 6, :type :number, :val -5}] 
                   [{:line 1, :pos 9, :type :command, :val "GOTO"} 
                    {:line 1, :pos 14, :type :label, :val "SCAN"}]])

(def instr-pairs6 [[{:val "WAIT", :type :label, :line 1, :pos 1} 
                    nil] 
                   [{:val "IF", :type :command, :line 1, :pos 6} 
                    {:val "X", :type :register, :line 1, :pos 9}] 
                   [{:val "<", :type :command, :line 1, :pos 10} 
                    {:val -5, :line 1, :pos 11, :type :number}] 
                   [{:val "GOTO", :type :command, :line 1, :pos 14} 
                    {:val "SCAN", :type :label, :line 1, :pos 19}]])

(def labels-mapped3 {:labels {}, 
                     :instrs [[{:line 1, :pos 1, :type :command, :val "IF"} 
                               {:line 1, :pos 4, :type :register, :val "X"}] 
                              [{:line 1, :pos 5, :type :command, :val "<"} 
                               {:line 1, :pos 6, :type :number, :val -5}] 
                              [{:line 1, :pos 9, :type :command, :val "GOTO"} 
                               {:line 1, :pos 14, :type :label, :val "SCAN"}]]})

(def labels-mapped6 {:labels {"WAIT" 0}, 
                     :instrs [[{:line 1, :pos 6, :type :command, :val "IF"} 
                               {:line 1, :pos 9, :type :register, :val "X"}] 
                              [{:line 1, :pos 10, :type :command, :val "<"} 
                               {:line 1, :pos 11, :type :number, :val -5}] 
                              [{:line 1, :pos 14, :type :command, :val "GOTO"} 
                               {:line 1, :pos 19, :type :label, :val "SCAN"}]]})

(def multi-line-compiled 
  {:labels {},
   :instrs
   [[{:val "IF", :type :command, :pos 1, :line 1}
     {:val "DAMAGE", :type :register, :pos 4, :line 1}]
    [{:val "#", :type :command, :pos 11, :line 1}
     {:val "D", :type :register, :pos 13, :line 1}]
    [{:val "GOTO", :type :command, :pos 15, :line 1}
     {:val "MOVE", :type :label, :pos 20, :line 1}]
    [{:val ",", :type :command, :pos 1, :line 2}
     {:val "AIM", :type :register, :pos 1, :line 2}]
    [{:val "-", :type :command, :pos 4, :line 2}
     {:val 17, :type :number, :pos 5, :line 2}]
    [{:val "TO", :type :command, :pos 8, :line 2}
     {:val "AIM", :type :register, :pos 11, :line 2}]
    [{:val "IF", :type :command, :pos 1, :line 3}
     {:val "X", :type :register, :pos 4, :line 3}]
    [{:val "<", :type :command, :pos 5, :line 3}
     {:val -5, :type :number, :pos 6, :line 3}]
    [{:val "GOTO", :type :command, :pos 9, :line 3}
     {:val "SCAN", :type :label, :pos 14, :line 3}]]})

(def multi-line-compiled-error 
  {:val "Invalid word or symbol", :type :error, :pos 3, :line 4})


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
    (is (= (parse-token {:token-str "AIM", :line 1, :pos 1})
           {:val "AIM", :type :register, :line 1, :pos 1}))))

(deftest parse-token-command-word
  (testing "parsing command token (word)"
    (is (= (parse-token {:token-str "GOTO", :line 1, :pos 15})
           {:val "GOTO", :type :command, :line 1, :pos 15}))))

(deftest parse-token-command-operator
  (testing "parsing command token (operator)"
    (is (= (parse-token {:token-str "#", :line 1, :pos 11})
           {:val "#", :type :command, :line 1, :pos 11}))))

(deftest parse-token-number
  (testing "parsing number token"
    (is (= (parse-token {:token-str "-17", :line 1, :pos 5}))
        {:val -17, :type :number, :line 1, :pos 5})))

(deftest parse-token-label
  (testing "parsing label token"
    (is (= (parse-token  {:token-str "SCAN", :line 1, :pos 14})
           {:val "SCAN", :type :label, :line 1, :pos 14}))))

(deftest parse-token-error
  (testing "parsing error token"
    (is (= (parse-token {:token-str "-GOTO", :line 1, :pos 24})
           {:val "Invalid word or symbol", :type :error, :line 1, :pos 24}))))

(deftest parse-tokens-minus-sign
  (testing "parsing tokens with a binary minus sign"
    (is (= (parse lexed-tokens2)
           parsed-tokens2))))

(deftest parse-tokens-negative-sign
  (testing "parsing tokens with a unary negative sign"
    (is (= (parse lexed-tokens3)
           parsed-tokens3))))

(deftest parse-tokens-error
  (testing "parsing tokens with an invalid operator"
    (is (= (parse lexed-tokens4)
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

(deftest map-labels-no-label
  (testing "mapping labels from instruction pairs with no label"
    (is (= (map-labels instr-pairs3)
           labels-mapped3))))

(deftest map-labels-with-label
  (testing "mapping labels from instruction pairs with starting label"
    (is (= (map-labels instr-pairs6)
           labels-mapped6))))

(deftest compile-test-success
  (testing "compiling successfully"
    (is (= (compile (join "\n" [line1 line2 line3]))
           multi-line-compiled))))

(deftest compile-test-failure
  (testing "compile results in error"
    (is (= (compile (join "\n" [line1 line2 line3 line4]))
           multi-line-compiled-error))))


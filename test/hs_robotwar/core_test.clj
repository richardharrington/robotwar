(ns hs-robotwar.core-test
  (:require [clojure.test :refer :all]
            [hs-robotwar.core :refer :all]))

(deftest digit?-negative
  (testing "not a digit"
    (is (not (digit? \f)))))

(deftest digit?-char-positive
  (testing "digit char"
    (is (digit? "6"))))

(deftest digit?-str-positive
  (testing "digit str"
    (is (digit? \6))))

(deftest lex-simple
  (testing "lexing of simple line"
    (is (= (lex "IF DAMAGE # D GOTO MOVE")
           [{:token "IF", :column 0} 
            {:token "DAMAGE", :column 3} 
            {:token "#", :column 10} 
            {:token "D", :column 12} 
            {:token "GOTO", :column 14} 
            {:token "MOVE", :column 19}]))))

(deftest lex-scrunched-chars
  (testing "lexing with no whitespace between operators and operands"
    (is (= (lex "AIM-17 TO AIM")
           [{:token "AIM", :column 0} 
            {:token "-", :column 3} 
            {:token "17", :column 4} 
            {:token "TO", :column 7} 
            {:token "AIM", :column 10}]))))

(deftest lex-negative-numbers
  (testing "lexing with unary negative operator"
    (is (= (lex "IF X<-5 GOTO SCAN")
           [{:token "IF", :column 0} 
            {:token "X", :column 3} 
            {:token "<", :column 4} 
            {:token "-5", :column 5} 
            {:token "GOTO", :column 8} 
            {:token "SCAN", :column 13}]))))


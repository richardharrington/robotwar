(ns robotwar.assembler-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [clojure.string :refer [join]]
            [robotwar.assembler :refer [assemble disambiguate-minus-signs lex make-instr-pairs map-labels parse parse-token str->int strip-comments valid-word]]))

(def line1 "IF DAMAGE # D GOTO MOVE    ; comment or something")
(def line2 "AIM-17 TO AIM              ; other comment")
(def line3 "IF X<-5 GOTO SCAN          ; third comment")
(def line4 "6 to RADAR")

(def lexed-tokens2 [[{:token-str "AIM"}
                     {:token-str "-"}
                     {:token-str "17"}
                     {:token-str "TO"}
                     {:token-str "AIM"}]])
(def lexed-tokens3 [[{:token-str "IF"}
                     {:token-str "X"}
                     {:token-str "<"}
                     {:token-str "-"}
                     {:token-str "5"}
                     {:token-str "GOTO"}
                     {:token-str "SCAN"}]])

(deftest assembler-smoke-test
  (testing "core assembler pipeline"
    (is (= ["IF DAMAGE # D GOTO MOVE    "] (strip-comments [line1])))
    (is (= 8 (str->int "8")))
    (is (nil? (str->int "G")))
    (is (= "BEATLES7" (valid-word "BEATLES7")))
    (is (= {:val "GOTO" :type :command} (parse-token {:token-str "GOTO"})))
    (is (= {:val "Invalid word or symbol" :type :error} (parse-token {:token-str "-GOTO"})))
    (is (= [{:val "AIM" :type :register}
            {:val "-" :type :command}
            {:val 17 :type :number}
            {:val "TO" :type :command}
            {:val "AIM" :type :register}]
           (parse lexed-tokens2)))
    (is (= [{:val "IF" :type :command}
            {:val "X" :type :register}
            {:val "<" :type :command}
            {:val -5 :type :number}
            {:val "GOTO" :type :command}
            {:val "SCAN" :type :label}]
           (disambiguate-minus-signs (parse lexed-tokens3))))
    (is (= {:labels {"WAIT" 0}
            :instrs [[{:val "IF" :type :command} {:val "X" :type :register}]
                     [{:val "<" :type :command} {:val -5 :type :number}]
                     [{:val "GOTO" :type :command} {:val "SCAN" :type :label}]]}
           (-> [{:val "WAIT" :type :label}
                {:val "IF" :type :command}
                {:val "X" :type :register}
                {:val "<" :type :command}
                {:val -5 :type :number}
                {:val "GOTO" :type :command}
                {:val "SCAN" :type :label}]
               make-instr-pairs
               map-labels)))
    (is (= {:val "Invalid word or symbol" :type :error}
           (assemble (join "\n" [line1 line2 line3 line4]))))))

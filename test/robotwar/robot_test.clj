^[[AHarrington-MacBook-Pro:robotwar richardharrington$ lein repl
   nREPL server started on port 63807
   REPL-y 0.1.10
   Clojure 1.5.1
       Exit: Control+D or (exit) or (quit)
   Commands: (user/help)
       Docs: (doc function-name-here)
             (find-doc "part-of-name-here")
     Source: (source function-name-here)
             (user/sourcery function-name-here)
    Javadoc: (javadoc java-object-or-class-here)
   Examples from clojuredocs.org: [clojuredocs or cdoc]
             (user/clojuredocs name-here)
             (user/clojuredocs "ns-here" "name-here")
   robotwar.core=> (def zeroed-world (assoc-in world [:robots 0 :pos-x] 0))
   #'robotwar.core/zeroed-world
   robotwar.core=> (def zeroed-registers (get-in world [:robots 0 :brain :registers]))
   #'robotwar.core/zeroed-registers
   robotwar.core=> (def speedy-world (wr (zeroed-registers "SPEEDX") zeroed-world 140))
   #'robotwar.core/speedy-world
   robotwar.core=> (def speedy-worlds (world/iterate-worlds speedy-world))
   #'robotwar.core/speedy-worlds
   robotwar.core=> (take 6 (map (fn [world] {:pos-x (get-in world [:robots 0 :pos-x]) :v-x (get-in world [:robots 0 :v-x]) :desired-v-x (get-in world [:robots 0 :desired-v-x])}) speedy-worlds))
   ({:pos-x 0, :v-x 0, :desired-v-x 140} {:pos-x 20.0, :v-x 40, :desired-v-x 140} {:pos-x 80.0, :v-x 80, :desired-v-x 140} {:pos-x 180.0, :v-x 120, :desired-v-x 140} {:pos-x 315.0, :v-x 140, :desired-v-x 140} {:pos-x 455.0, :v-x 140, :desired-v-x 140})
   robotwar.core=> (pp)
   ({:pos-x 0, :v-x 0, :desired-v-x 140}
     {:pos-x 20.0, :v-x 40, :desired-v-x 140}
     {:pos-x 80.0, :v-x 80, :desired-v-x 140}
     {:pos-x 180.0, :v-x 120, :desired-v-x 140}
     {:pos-x 315.0, :v-x 140, :desired-v-x 140}
     {:pos-x 455.0, :v-x 140, :desired-v-x 140})
   nil
   robotwar.core=> (= 0 0.0)
   false
   robotwar.core=> (= 0 0)
   true
   robotwar.core=>



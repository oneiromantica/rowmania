(ns rowmania.core
  (:require [com.brunobonacci.mulog :as mu]
            [rowmania.system :as system]))

(defn main-
  "Einstiegspunkt für `clj -X:start-server`. Läuft, bis die JVM beendet wird."
  [_]
  (mu/start-publisher! {:type :console})
  (system/start!)
  @(promise))

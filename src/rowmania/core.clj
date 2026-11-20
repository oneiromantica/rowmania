(ns rowmania.core
  (:require [com.brunobonacci.mulog :as mu]
            [rowmania.system :as system])
  (:gen-class))

(defn main-
  "Einstiegspunkt für `clj -X:start-server`. Läuft, bis die JVM beendet wird."
  [_]
  (mu/start-publisher! {:type :console})
  (system/start!)
  @(promise))

(defn -main
  "Einstiegspunkt für das Uberjar (`java -jar`). Kommandozeilenargumente
  werden ignoriert; die Konfiguration kommt aus der Umgebung."
  [& _args]
  (main- {}))

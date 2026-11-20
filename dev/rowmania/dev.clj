(ns rowmania.dev
  "Hot-Reload: Bei Dateiänderungen in src/ und resources/ werden geänderte
  Namespaces neu geladen und das System neu gestartet."
  (:require [clj-reload.core :as reload]
            [integrant.repl :as ig-repl]
            [nextjournal.beholder :as beholder]
            [rowmania.system :refer [system-config]])
  (:import (java.util.concurrent Executors ScheduledExecutorService TimeUnit)))

(def ^:private watched-directories ["src" "resources"])
(def ^:private debounce-ms 150)

(ig-repl/set-prep! (fn [] system-config))
(reload/init {:dirs ["src"]})

(defn reset
  "Lädt geänderte Namespaces nach und startet das System neu.
  Fehler werden gemeldet; die nächste Änderung versucht es erneut."
  []
  (try
    (reload/reload)
    (ig-repl/reset)
    (println "[reload] ok")
    (catch Throwable t
      (println "[reload] Fehler:" (ex-message t))
      (some-> (ex-cause t) ex-message println))))

(defn- debouncer
  "Fasst Ereignisbündel (Editoren schreiben oft mehrfach) zu einem Aufruf zusammen."
  [^ScheduledExecutorService executor delay-ms action]
  (let [pending (atom nil)]
    (fn [& _]
      (swap! pending
             (fn [previous]
               (some-> previous (.cancel false))
               (.schedule executor ^Runnable action ^long delay-ms TimeUnit/MILLISECONDS))))))

(defn start-watching
  "Gibt eine Funktion zurück, die das Beobachten beendet."
  []
  (let [executor (Executors/newSingleThreadScheduledExecutor)
        watcher  (apply beholder/watch
                        (debouncer executor debounce-ms reset)
                        watched-directories)]
    (fn stop-watching []
      (beholder/stop watcher)
      (.shutdown executor))))

(defn watch!
  "Einstieg für `clojure -X:watch`: startet das System und beobachtet Dateien."
  [_]
  (ig-repl/go)
  (start-watching)
  (println "[reload] beobachte" watched-directories)
  @(promise))

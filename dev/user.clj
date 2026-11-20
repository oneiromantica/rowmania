(ns user
  (:require [integrant.repl :as ig-repl]
            [rowmania.dev :as dev]))

(defn reset [] (dev/reset))
(defn start [] (ig-repl/go))
(defn stop  [] (ig-repl/halt))
(defn watch [] (dev/start-watching))

(ns rowmania.system
  (:require [integrant.core :as ig]
            [rowmania.config]
            [rowmania.db]
            [rowmania.server]))

(def system-config
  {:rowmania/config {:file "config.edn"}
   :rowmania/db     {:config (ig/ref :rowmania/config)}
   :rowmania/server {:config (ig/ref :rowmania/config)
                     :db     (ig/ref :rowmania/db)}})

(defn start! [] (ig/init system-config))
(defn stop!  [system] (ig/halt! system))

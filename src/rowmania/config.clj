(ns rowmania.config
  "Liest die Einstellungen mit Aero."
  (:require [aero.core :as aero]
            [clojure.java.io :as io]
            [integrant.core :as ig]))

(defn read-settings
  "Liest eine Aero-Konfigurationsdatei aus dem Classpath."
  [resource-name]
  (aero/read-config (io/resource resource-name)))

(defmethod ig/init-key :rowmania/config
  [_ {:keys [file]}]
  (read-settings file))

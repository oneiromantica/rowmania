(ns rowmania.config-test
  (:require [clojure.test :refer [deftest is]]
            [rowmania.config :as config]))

(deftest settings-have-port-and-store
  (let [settings (config/read-settings "config.edn")]
    (is (pos-int? (:port settings)))
    (is (= :sqlite (get-in settings [:db :store :backend])))))

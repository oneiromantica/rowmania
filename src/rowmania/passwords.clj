(ns rowmania.passwords
  (:require [buddy.hashers :as hashers]))

(defn hash-password [password] (hashers/derive password {:alg :argon2id}))

;; Für unbekannte Nutzer wird gegen diesen Hash geprüft, damit die Antwortzeit
;; nicht verrät, ob es den Namen gibt.
(def ^:private placeholder-hash (delay (hash-password "placeholder")))

(defn verify-password
  "Prüft das Passwort gegen den Hash; nil als Hash (unbekannter Nutzer) ergibt immer false."
  [password password-hash]
  (let [result (:valid (hashers/verify (str password) (or password-hash @placeholder-hash)))]
    (boolean (and password-hash result))))

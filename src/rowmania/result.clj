(ns rowmania.result
  "Ergebnis einer Berechnung, die scheitern kann: {:ok wert} oder {:error text}.")

(defn ok [value] {:ok value})
(defn fail [message] {:error message})
(defn failed? [result] (contains? result :error))

(defn bind
  "Wendet `f` (wert -> result) auf den Wert an, sofern kein Fehler vorliegt."
  [result f]
  (if (failed? result) result (f (:ok result))))

(defn fmap [result f] (bind result (comp ok f)))

(defn traverse
  "Wendet `f` (x -> result) auf alle Elemente an; Ergebnis ist ein Vektor oder der erste Fehler."
  [f xs]
  (reduce (fn [acc x] (bind acc (fn [vs] (fmap (f x) #(conj vs %)))))
          (ok [])
          xs))

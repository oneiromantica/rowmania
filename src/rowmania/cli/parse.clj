(ns rowmania.cli.parse
  "Reine Übersetzung der Kommandozeile in Befehlsdaten:
  argv -> {:ok {:command kw :args map}} oder {:error text}."
  (:require [clojure.string :as str]
            [rowmania.result :as r]
            [rowmania.values :as values]))

;; ---------------------------------------------------------------- Werte

(defn parse-text [token]
  (if (str/blank? token) (r/fail "Leerer Text") (r/ok token)))

(defn parse-reference
  "UUID wird als {:id uuid}, alles andere als {:name text} gelesen."
  [token]
  (if-let [id (parse-uuid token)] (r/ok {:id id}) (r/ok {:name token})))

(def parse-date values/parse-iso-date)

(defn parse-distance
  "Positive ganze Zahl (Meter)."
  [token]
  (let [n (try (Long/parseLong token) (catch NumberFormatException _ nil))]
    (if (and n (pos? n)) (r/ok n) (r/fail (str "Keine positive ganze Zahl: " token)))))

;; -------------------------------------------------------------- Befehle

(def commands
  "Flag -> {:command, :positionals [[key parser]], :options {flag [key parser]}}."
  {"--add-user"               {:command :add-user
                               :positionals [[:name parse-text] [:password parse-text]]}
   "--update-user-name"       {:command :update-user-name
                               :positionals [[:user parse-reference] [:name parse-text]]}
   "--update-user-pass"       {:command :update-user-pass
                               :positionals [[:user parse-reference] [:password parse-text]]}
   "--list-users"             {:command :list-users}
   "--create-challenge"       {:command :create-challenge
                               :options {"--name" [:name parse-text] "--start" [:start parse-date]
                                         "--end" [:end parse-date] "--goal" [:goal parse-distance]}}
   "--update-challenge-name"  {:command :update-challenge-name
                               :positionals [[:challenge parse-reference] [:name parse-text]]}
   "--update-challenge-start" {:command :update-challenge-start
                               :positionals [[:challenge parse-reference] [:start parse-date]]}
   "--update-challenge-end"   {:command :update-challenge-end
                               :positionals [[:challenge parse-reference] [:end parse-date]]}
   "--update-challenge-goal"  {:command :update-challenge-goal
                               :positionals [[:challenge parse-reference] [:goal parse-distance]]}
   "--add-session"            {:command :add-session
                               :positionals [[:challenge parse-reference] [:user parse-reference]]
                               :options {"--date" [:date parse-date] "--distance" [:distance parse-distance]}}})

(def usage
  (str/join "\n"
            ["Nutzung: clojure -M:cli <befehl>"
             "  --add-user NAME PASSWORT"
             "  --update-user-name UUID|NAME NEUER-NAME"
             "  --update-user-pass UUID|NAME PASSWORT"
             "  --list-users"
             "  --create-challenge --name NAME --start JJJJ-MM-TT --end JJJJ-MM-TT --goal METER"
             "  --update-challenge-name|-start|-end|-goal UUID|NAME WERT"
             "  --add-session UUID|CHALLENGE-NAME UUID|NUTZERNAME --date JJJJ-MM-TT --distance METER"]))

(defn- parse-pair [[key parser] token]
  (r/fmap (parser token) (fn [value] [key value])))

(defn- parse-positionals [specs tokens]
  (if (< (count tokens) (count specs))
    (r/fail "Zu wenige Argumente")
    (r/fmap (r/traverse (fn [[spec token]] (parse-pair spec token)) (map vector specs tokens))
            #(into {} %))))

(defn- parse-option [options [flag token]]
  (if-let [spec (get options flag)]
    (parse-pair spec token)
    (r/fail (str "Unbekannte Option: " flag))))

(defn- parse-options [options tokens]
  (if (odd? (count tokens))
    (r/fail "Option ohne Wert")
    (r/bind (r/traverse #(parse-option options %) (partition 2 tokens))
            (fn [pairs]
              (let [args    (into {} pairs)
                    absent  (remove (set (keys args)) (map first (vals options)))]
                (if (seq absent)
                  (r/fail (str "Fehlende Optionen: " (str/join ", " (map name absent))))
                  (r/ok args)))))))

(defn parse-args [argv]
  (let [[flag & tokens] argv
        spec            (get commands flag)
        n               (count (:positionals spec))]
    (if-not spec
      (r/fail (if flag (str "Unbekannter Befehl: " flag) "Kein Befehl angegeben"))
      (r/bind (parse-positionals (:positionals spec) (take n tokens))
              (fn [positional]
                (r/bind (parse-options (:options spec) (drop n tokens))
                        (fn [optional]
                          (r/ok {:command (:command spec) :args (merge positional optional)}))))))))

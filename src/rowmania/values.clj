(ns rowmania.values
  "Werte der Domäne: Einlesen und Anzeigen von Daten und Entfernungen.
  Daten sind Zeitpunkte um Mitternacht UTC, Entfernungen ganze Meter."
  (:require [clojure.string :as str]
            [rowmania.result :as r])
  (:import (java.math BigDecimal RoundingMode)
           (java.time LocalDate ZoneId ZoneOffset)
           (java.time.format DateTimeParseException)
           (java.util Date)))

(def ^:private max-session-metres 1000000)

(defn- local-date [^Date date]
  (-> date .toInstant (.atZone ZoneOffset/UTC) .toLocalDate))

(defn- start-of-day [^LocalDate date]
  (-> date (.atStartOfDay ZoneOffset/UTC) .toInstant Date/from))

(defn parse-iso-date [token]
  (try
    (r/ok (start-of-day (LocalDate/parse token)))
    (catch DateTimeParseException _
      (r/fail (str "Kein ISO-Datum (JJJJ-MM-TT): " token)))
    (catch NullPointerException _
      (r/fail "Kein Datum angegeben"))))

(defn today-in
  "Heutiges Datum in der Zeitzone `zone-id` als Zeitpunkt um Mitternacht UTC."
  [zone-id]
  (start-of-day (LocalDate/now (ZoneId/of zone-id))))

(defn parse-kilometres
  "Kilometer (\"6,5\" oder \"6.5\") als ganze Meter; positiv und höchstens 1000 km."
  [token]
  (let [km (try (BigDecimal. (str/replace (str/trim (str token)) "," "."))
                (catch NumberFormatException _ nil))
        metres (some-> km (.multiply (BigDecimal. 1000)) (.setScale 0 RoundingMode/HALF_UP) .longValue)]
    (if (and metres (pos? metres) (<= metres max-session-metres))
      (r/ok metres)
      (r/fail "Bitte Kilometer zwischen 0,1 und 1000 angeben"))))

(defn kilometres-text
  "6500 -> \"6,5\", 10000 -> \"10\""
  [metres]
  (-> (BigDecimal. (long metres))
      (.divide (BigDecimal. 1000))
      (.setScale 1 RoundingMode/HALF_UP)
      .stripTrailingZeros
      .toPlainString
      (str/replace "." ",")))

(defn kilometres-input
  "Wert für <input type=number>: Punkt als Dezimaltrenner."
  [metres]
  (str/replace (kilometres-text metres) "," "."))

(defn iso-date [date] (str (local-date date)))

(def ^:private weekdays ["Mo" "Di" "Mi" "Do" "Fr" "Sa" "So"])
(def ^:private months
  ["Januar" "Februar" "März" "April" "Mai" "Juni" "Juli" "August" "September"
   "Oktober" "November" "Dezember"])

(defn- day-month [d] (str (.getDayOfMonth d) ". " (months (dec (.getMonthValue d)))))

(defn format-day
  "\"Do, 19. November\""
  [date]
  (let [d (local-date date)]
    (str (weekdays (dec (.getValue (.getDayOfWeek d)))) ", " (day-month d))))

(defn format-period
  "\"1. Oktober bis 31. Dezember 2026\", bei verschiedenen Jahren mit beiden Jahren."
  [start end]
  (let [s (local-date start) e (local-date end)]
    (if (= (.getYear s) (.getYear e))
      (str (day-month s) " bis " (day-month e) " " (.getYear e))
      (str (day-month s) " " (.getYear s) " bis " (day-month e) " " (.getYear e)))))

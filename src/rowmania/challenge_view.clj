(ns rowmania.challenge-view
  "Rein: aus Challenge, Teilnehmern und Sessions entsteht die Übersicht."
  (:require [rowmania.values :as values]))

(def palette ["#e98fa8" "#5aa89d" "#9a86c2" "#f6b99a"])

(def ^:private week-ms (* 7 24 60 60 1000))

(defn- running? [today {:challenge/keys [start end]}]
  (and (not (pos? (compare start today))) (not (neg? (compare end today)))))

(defn choose-challenge
  "Die angeforderte Challenge, sonst eine laufende (die zuletzt begonnene), sonst die jüngste."
  [challenges today requested-id]
  (or (some #(when (= requested-id (:challenge/id %)) %) challenges)
      (last (sort-by (juxt (partial running? today) :challenge/start) challenges))))

(defn- week-index [start date]
  (quot (- (.getTime ^java.util.Date date) (.getTime ^java.util.Date start)) week-ms))

(defn- within? [{:challenge/keys [start end]} date]
  (and (not (neg? (compare date start))) (not (pos? (compare date end)))))

(defn- frequencies-by-week [start sessions]
  (reduce (fn [acc {:session/keys [date distance]}]
            (update acc (week-index start date) (fnil + 0) distance))
          {}
          sessions))

(defn- weekly-totals
  "Kumulierte Meter am Ende jeder der `weeks` Wochen."
  [start weeks sessions]
  (let [per-week (frequencies-by-week start sessions)]
    (vec (rest (reductions + 0 (map #(get per-week % 0) (range weeks)))))))

(defn summarize
  [{:keys [challenge participants sessions today me-id]}]
  (let [{:challenge/keys [start end]} challenge
        counted  (filter #(within? challenge (:session/date %)) sessions)
        by-user  (group-by (comp :user/id :session/user) counted)
        weeks    (max 1 (inc (week-index start (if (neg? (compare today end)) today end))))
        colours  (zipmap (map :user/id (sort-by :user/name participants)) (cycle palette))
        members  (for [{:user/keys [id name]} participants
                       :let [own (get by-user id [])]]
                   {:id id :name name :me? (= id me-id) :colour (colours id)
                    :metres (transduce (map :session/distance) + 0 own)
                    :weekly (weekly-totals start weeks own)})
        total    (transduce (map :metres) + 0 members)]
    {:name    (:challenge/name challenge)
     :period  (values/format-period start end)
     :goal    (:challenge/target-distance challenge)
     :total   total
     :weeks   weeks
     :members (->> members
                   (map #(assoc % :share (if (pos? total) (Math/round (* 100.0 (/ (:metres %) total))) 0)))
                   (sort-by (juxt (comp - :metres) :name)))}))

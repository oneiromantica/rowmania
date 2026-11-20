(ns rowmania.pages
  "Reine Funktionen: Daten rein, HTML-String raus. Gestaltung siehe ui/."
  (:require [hiccup2.core :as hiccup]
            [rowmania.values :as values]))

(def ^:private doctype (hiccup/raw "<!DOCTYPE html>\n"))

(defn document
  "Rahmen aller Seiten. hiccup2 maskiert Textknoten und Attribute."
  [title & body]
  (str (hiccup/html doctype
                    [:html {:lang "de"}
                     [:head
                      [:meta {:charset "utf-8"}]
                      [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
                      [:title title]
                      [:link {:rel "preconnect" :href "https://fonts.bunny.net"}]
                      [:link {:rel "stylesheet"
                              :href "https://fonts.bunny.net/css?family=fraunces:800,900&family=nunito-sans:400,600,700,800&display=swap"}]
                      [:link {:rel "stylesheet" :href "/assets/style.css"}]]
                     (into [:body] body)])))

;; ------------------------------------------------------------------ Login

(defn login-page [{:keys [error name]}]
  (document "RowMania · Anmeldung"
            [:section {:aria-label "Anmeldung"}
             [:div.hero
              [:div.hero-inner
               [:h1.outline "RowMania"]
               [:form.login {:method "post" :action "/login"}
                (when error [:p.error {:role "alert"} error])
                [:label {:for "login-name"} "Name"
                 [:input.field {:id "login-name" :name "name" :type "text" :required true
                                :autocomplete "username" :value name :autofocus true}]]
                [:label {:for "login-pass"} "Passwort"
                 [:input.field {:id "login-pass" :name "password" :type "password" :required true
                                :autocomplete "current-password"}]]
                [:button.btn {:type "submit"} "Einloggen"]]]
              [:div.wave {:aria-hidden "true"}
               [:svg {:viewBox "0 0 1440 110" :preserveAspectRatio "none"}
                [:path {:d "M0 50 Q90 10 180 50 T360 50 T540 50 T720 50 T900 50 T1080 50 T1260 50 T1440 50 V110 H0Z" :fill "#cdbfe6"}]
                [:path {:d "M0 85 Q90 55 180 85 T360 85 T540 85 T720 85 T900 85 T1080 85 T1260 85 T1440 85 V110 H0Z" :fill "#5aa89d"}]]]]]))

;; ------------------------------------------------------------------ Rahmen

(defn- query [challenge-id]
  (if challenge-id (str "?challenge=" challenge-id) ""))

(defn- shell [{:keys [active challenge-id]} & body]
  [:section
   [:div.wrap
    [:header.topbar
     [:a.brand {:href (str "/" (query challenge-id))} "RowMania"]
     [:nav.tabs {:aria-label "Hauptnavigation"}
      [:a.tab (cond-> {:href (str "/" (query challenge-id))} (= active :challenge) (assoc :aria-current "page")) "Challenge"]
      [:a.tab (cond-> {:href (str "/sessions" (query challenge-id))} (= active :sessions) (assoc :aria-current "page")) "Sessions"]]
     [:form.inline {:method "post" :action "/logout"}
      [:button.link {:type "submit"} "Abmelden"]]]
    (into [:div] body)
    [:footer "RowMania"]]])

(defn- no-challenge []
  [:p.empty "Du nimmst noch an keiner Challenge teil."])

;; --------------------------------------------------------------- Challenge

(def ^:private chart-size {:width 760 :height 300 :left 36 :right 24 :top 10 :bottom 30})

(defn- nice-ceiling
  "Kleinste Zahl der Form 1, 2 oder 5 mal Zehnerpotenz, die `value` erreicht (mindestens 10)."
  [value]
  (->> (iterate #(* % 10) 1)
       (mapcat (fn [magnitude] (map #(* % magnitude) [1 2 5])))
       (filter #(and (>= % 10) (>= % value)))
       first))

(defn- chart [{:keys [members weeks]}]
  (let [{:keys [width height left right top bottom]} chart-size
        top-km  (nice-ceiling (/ (apply max 0 (map #(last (:weekly %)) members)) 1000.0))
        x       (fn [i] (+ left (* (/ i (max 1 (dec weeks))) (- width left right))))
        y       (fn [km] (- height bottom (* (/ km top-km) (- height top bottom))))
        ticks   [0 (/ top-km 2) top-km]]
    [:svg#chart {:viewBox (str "0 0 " width " " height) :role "img"
                 :aria-label "Kumulierte Kilometer pro Teilnehmer und Woche"}
     (for [tick ticks]
       (list [:line {:x1 left :x2 (- width right) :y1 (y tick) :y2 (y tick) :stroke "#e8e1f1" :stroke-width 1.5}]
             [:text {:x (- left 8) :y (+ (y tick) 4) :text-anchor "end"} (values/kilometres-text (* tick 1000))]))
     (for [i (range weeks)]
       [:text {:x (x i) :y (- height 8) :text-anchor "middle"} (str "W" (inc i))])
     (for [{:keys [weekly colour name]} members
           :let [points (map-indexed (fn [i metres] [(x i) (y (/ metres 1000.0))]) weekly)
                 [lx ly] (last points)]]
       (list [:polyline {:points (apply str (interpose " " (map (fn [[px py]] (str px "," py)) points)))
                         :fill "none" :stroke colour :stroke-width 4
                         :stroke-linecap "round" :stroke-linejoin "round"}]
             [:circle {:cx lx :cy ly :r 4 :fill colour}]
             [:text {:x (- lx 8) :y (- ly 10) :text-anchor "end" :style "fill:#4b3668"} name]))]))

(defn challenge-page
  [{:keys [summary challenge-id]}]
  (document "RowMania · Challenge"
            (shell {:active :challenge :challenge-id challenge-id}
                   (if-not summary
                     (no-challenge)
                     (let [{:keys [name period goal total members]} summary
                           km-total (values/kilometres-text total)
                           km-goal  (values/kilometres-text goal)]
                       (list
                        [:div.head [:h1.outline.title name] [:p.sub period]]
                        [:div.goal
                         [:div.goal-num.outline km-total [:small (str "von " km-goal " km")]]
                         [:div#track.track {:role "img" :aria-label (str km-total " von " km-goal " Kilometern")}
                          (for [{:keys [metres colour]} members]
                            [:span {:style (str "width:" (min 100.0 (* 100.0 (/ metres goal))) "%;background:" colour)}])]
                         [:ul#contrib.contrib
                          (for [{:keys [name me? colour metres share]} members]
                            [:li [:i {:style (str "background:" colour)}]
                             [:span (str name (when me? " (du)"))]
                             [:span [:b (str (values/kilometres-text metres) " km")] [:em (str share " %")]]])]]
                        [:div.chart-wrap [:h2 "Verlauf"] (chart summary)]))))))

;; ---------------------------------------------------------------- Sessions

(defn- session-row [{:session/keys [id date distance]} {:keys [challenge-id editing-id]}]
  (if (= id editing-id)
    [:li [:form.editing {:method "post" :action (str "/sessions/" id)}
          [:input {:type "hidden" :name "challenge" :value challenge-id}]
          [:label {:for "e-date"} "Datum"
           [:input.field {:id "e-date" :name "date" :type "date" :required true :value (values/iso-date date)}]]
          [:label {:for "e-km"} "Kilometer"
           [:input.field {:id "e-km" :name "km" :type "number" :step "0.1" :min "0" :required true
                          :value (values/kilometres-input distance)}]]
          [:div.actions [:button.btn {:type "submit"} "Speichern"]
           [:a.link {:href (str "/sessions" (query challenge-id))} "Abbrechen"]]]]
    [:li [:span (values/format-day date)]
     [:span.km (str (values/kilometres-text distance) " km")]
     [:a.link {:href (str "/sessions?challenge=" challenge-id "&edit=" id)} "Ändern"]]))

(defn sessions-page
  "`challenges` sind die Challenges des Nutzers, `selected` die gewählte."
  [{:keys [challenges selected sessions editing-id error form today]}]
  (let [challenge-id (:challenge/id selected)]
    (document "RowMania · Sessions"
              (shell {:active :sessions :challenge-id challenge-id}
                     (if-not selected
                       (no-challenge)
                       (list
                        [:div.head
                         [:h1.outline.title "Sessions"]
                         [:div.chips {:role "group" :aria-label "Challenge wechseln"}
                          (for [{:challenge/keys [id name]} challenges]
                            [:a.chip (cond-> {:href (str "/sessions?challenge=" id)}
                                       (= id challenge-id) (assoc :aria-current "true"))
                             name])]]
                        (when error [:p.error {:role "alert"} error])
                        [:form.entry {:method "post" :action "/sessions"}
                         [:input {:type "hidden" :name "challenge" :value challenge-id}]
                         [:label {:for "km"} "Kilometer"
                          [:input.field {:id "km" :name "km" :type "number" :step "0.1" :min "0" :required true
                                         :value (:km form)}]]
                         [:label {:for "date"} "Datum"
                          [:input.field {:id "date" :name "date" :type "date" :required true
                                         :value (or (:date form) (values/iso-date today))}]]
                         [:button.btn {:type "submit"} "Speichern"]]
                        [:ul.list
                         (for [session sessions]
                           (session-row session {:challenge-id challenge-id :editing-id editing-id}))]))))))

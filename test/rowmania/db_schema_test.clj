(ns rowmania.db-schema-test
  (:require [clojure.test :refer [deftest is]]
            [datahike.api :as d]
            [rowmania.test-db :refer [fresh-conn]]))

(deftest session-links-user-and-challenge
  (let [conn         (fresh-conn)
        user-id      (random-uuid)
        challenge-id (random-uuid)]
    (d/transact conn [{:user/id user-id :user/name "Ada" :user/password-hash "hash"}
                      {:challenge/id              challenge-id
                       :challenge/start           #inst "2026-01-01"
                       :challenge/end             #inst "2026-01-31"
                       :challenge/target-distance 100000
                       :challenge/participants    [[:user/id user-id]]}
                      {:session/id        (random-uuid)
                       :session/date      #inst "2026-01-05"
                       :session/distance  5000
                       :session/user      [:user/id user-id]
                       :session/challenge [:challenge/id challenge-id]}])
    (is (= #{["Ada" 5000]}
           (d/q '[:find ?name ?distance
                  :where [?s :session/distance ?distance]
                         [?s :session/user ?u]
                         [?u :user/name ?name]]
                @conn)))
    (is (= #{"Ada"}
           (set (d/q '[:find [?name ...]
                       :in $ ?cid
                       :where [?c :challenge/id ?cid]
                              [?c :challenge/participants ?u]
                              [?u :user/name ?name]]
                     @conn challenge-id))))))

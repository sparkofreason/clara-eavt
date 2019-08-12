(require '[clara-eav.rules :refer :all]
         '[clara.rules :as rules])

(defrule working!
  [[?e :status :warming ?tx]]
  [[?e :t ?t]]
  [:test (> ?t 10)]
  =>
  (println "working!" ?e ?t ?tx)
  (upsert! [[?e :status :working]]))

(defrule stopping!
  [[?e :status :working ?tx]]
  [[?e :t ?t]]
  [:test (> ?t 20)]
  =>
  (println "stopping" ?e ?t ?tx)
  (upsert! [[?e :status :stopping]]))

(defrule stopped!
  [[?e :status :stopping ?tx]]
  [[?e :t ?t]]
  [:test (> ?t 30)]
  =>
  (println "stopped" ?e ?t ?tx)
  (upsert! [[?e :status :stopped]]))

(defquery status [] [[?e :status ?status]])

(defquery facts [] [[?e ?a ?v ?tx]])

(defquery current [] [[?e ?a ?v]])


(defsession foo 'user
  :cache false)

(def s' (-> foo
            (upsert [{:status :warming
                      :t 0}])
            rules/fire-rules))

(def snapshotty (partial snapshot foo))

(rules/query s' status)
(rules/query s' facts)
(current-entities s')

(def s'' (-> s'
             (upsert [[1 :t 11]])
             rules/fire-rules))

(rules/query s'' status)
(rules/query s'' facts)
(current-entities s'')

(def s''' (-> s''
              snapshotty
              (upsert [[1 :t 21]])
              rules/fire-rules))

(rules/query s''' status)
(rules/query s''' facts)
(current-entities s''')

(def s'''' (-> s'''
               snapshotty
               (upsert [[1 :t 31]])
               rules/fire-rules))

(rules/query s'''' status)
(rules/query s'''' facts)
(current-entities s'''')
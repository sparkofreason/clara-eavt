(ns clara-eavt.dsl-test
  (:require [clojure.spec.alpha :as s]
            [clara-eavt.test-helper :as test-helper]
            [clara-eavt.dsl :as dsl]
            [clojure.test :refer [deftest testing is are use-fixtures]]))

(use-fixtures :once test-helper/spec-fixture)

(defn eav [v] (s/conform ::dsl/eav v))
(defn eav-2nd [eav] (cond-> eav (vector? eav) second))

(deftest conform-fact-eav-test
  (testing "Fact-EAV parsing"
    (are [x y] (= x (eav-2nd (eav y)))

               #::dsl{:e '?e, :a '?a, :v '?v}
               ['?e '?a '?v]

               #::dsl{:e 1, :a :todo/done, :v false}
               [1 :todo/done false]

               #::dsl{:e '_, :a :todo/done, :v false}
               ['_ :todo/done false]

               #::dsl{:e 1, :a :todo/done}
               [1 :todo/done]

               #::dsl{:e 1}
               [1]

               #::dsl{:e '_}
               ['_])

    (are [x] (= ::s/invalid (eav x))
             [1 2 3]
             [1 :todo/done false 4 5]
             ['_ 1]
             [])))

(deftest conditions-test
  (testing "Transforming an EAV conditions into a Clara-Rules conditions."
    (are [x y] (= x (#'dsl/conditions (second (eav y))))

               '((= (:e this) 1) (= (:v this) false) (= (:tx-id this) :now))
               [1 :todo/done false]

               '((= (:e this) 1) (= (:v this) false) (= (:tx-id this) ?tx-id) (integer? ?tx-id))
               [1 :todo/done false '?tx-id]

               '((= (:e this) 1) (= (:tx-id this) ?tx-id) (integer? ?tx-id))
               [1 :todo/done '_ '?tx-id]

               '((= (:v this) false) (= (:tx-id this) :now))
               ['_ :todo/done false]

               '((= (:e this) 1) (= (:tx-id this) :now))
               [1 :todo/done]

               '((= (:tx-id this) :now))
               ['_ :todo/done])))

(def todo (eav [1 :todo/done false]))
(def fact-eav #::dsl{:bind 't, :bind-arrow '<-, :eav todo})
(def fact-clara #::dsl{:bind       't, :bind-arrow '<- :type :todo/done,
                       :conditions (#'dsl/conditions (second todo))})

(deftest fact-test
  (testing "Transforming an EAV fact into a Clara-Rules fact."
    (is (= fact-clara (#'dsl/fact fact-eav)))))

(def fact-eav-node [::dsl/fact-eav fact-eav])
(def fact-clara-node [::dsl/fact-clara fact-clara])

(deftest node-test
  (testing "Transforming an EAV fact node into a Clara-Rules fact node."
    (is (= fact-clara-node (#'dsl/node fact-eav-node)))))

(def defrule-eav
  '({:salience 100}
    [Toggle (= e ?e)]
    [[_]]
    [[:eav/transient]]
    [[?e :todo/done ?v]]
    [[?e :todo/done ?v ?tx-id]]
    [?toggle <- Toggle (= e ?e)]
    [?eav <- [?e :todo/done ?v]]
    [:test (= ?e ?v)]
    [:and [:or [Toggle (> c ?e) (= d d?)]
           [[_ :todo/tag :project]]]
     [Toggle (< b ?e)]]
    =>
    (insert! [?e :todo/done (not ?v)])))

(def defrule-clara
  '({:salience 100}
    [Toggle (= e ?e)]
    [:eav/all (= (:tx-id this) :now)]
    [:eav/all (= (:e this) :eav/transient) (= (:tx-id this) :now)]
    [:todo/done (= (:e this) ?e) (= (:v this) ?v) (= (:tx-id this) :now)]
    [:todo/done (= (:e this) ?e) (= (:v this) ?v) (= (:tx-id this) ?tx-id) (integer? ?tx-id)]
    [?toggle <- Toggle (= e ?e)]
    [?eav <- :todo/done (= (:e this) ?e) (= (:v this) ?v) (= (:tx-id this) :now)]
    [:test (= ?e ?v)]
    [:and [:or [Toggle (> c ?e) (= d d?)]
           [:todo/tag (= (:v this) :project) (= (:tx-id this) :now)]]
     [Toggle (< b ?e)]]
    =>
    (insert! [?e :todo/done (not ?v)])))

(deftest transform-rule-test
  (testing "Transforms fact-eav to fact-clara in rule forms"
    (is (= defrule-clara (#'dsl/transform ::dsl/defrule defrule-eav)))))

(def defquery-eav
  '([:?d]
    [Toggle (= e ?e)]
    [[_]]
    [[:eav/transient]]
    [[?e :todo/done ?v]]
    [[?e :todo/done ?v ?tx-id]]
    [?toggle <- Toggle (= e ?e)]
    [?eav <- [?e :todo/done ?v]]
    [:test (= ?e ?v)]
    [:and [:or [Toggle (> c ?e) (= d d?)]
           [[_ :todo/tag :project]]]
     [Toggle (< b ?e)]]))

(def defquery-clara
  '([:?d]
    [Toggle (= e ?e)]
    [:eav/all (= (:tx-id this) :now)]
    [:eav/all (= (:e this) :eav/transient) (= (:tx-id this) :now)]
    [:todo/done (= (:e this) ?e) (= (:v this) ?v) (= (:tx-id this) :now)]
    [:todo/done (= (:e this) ?e) (= (:v this) ?v) (= (:tx-id this) ?tx-id) (integer? ?tx-id)]
    [?toggle <- Toggle (= e ?e)]
    [?eav <- :todo/done (= (:e this) ?e) (= (:v this) ?v) (= (:tx-id this) :now)]
    [:test (= ?e ?v)]
    [:and [:or [Toggle (> c ?e) (= d d?)]
           [:todo/tag (= (:v this) :project) (= (:tx-id this) :now)]]
     [Toggle (< b ?e)]]))

(deftest transform-query-test
  (testing "Transforms fact-eav to fact-clara in query forms"
    (is (= defquery-clara (#'dsl/transform ::dsl/defquery defquery-eav)))))

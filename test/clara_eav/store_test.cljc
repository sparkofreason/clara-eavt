(ns clara-eav.store-test
  (:require [clara-eav.eav :as eav]
            [clara-eav.store :as store]
            [clara-eav.test-helper :as test-helper]
    #?(:clj [clojure.test :refer [deftest testing is are use-fixtures]]
       :cljs [cljs.test :refer-macros [deftest testing is are use-fixtures]]))
  #?(:clj (:import (clojure.lang ExceptionInfo))))

(use-fixtures :once test-helper/spec-fixture)

(deftest state-test
  (testing "Store cleaned after transaction computation"
    (let [store {:max-eid 0
                 :eav-index {}}
          store-tx (assoc store
                     :insertables []
                     :retractables []
                     :tempids {})]
      (is (= store (store/state store-tx))))))

(deftest tempid?-test
  (testing "Are tempids"
    (are [x] (#'store/tempid? x)
      "my-id" 
      -7))
  (testing "Are not tempids"
    (are [x] (not (#'store/tempid? x))
      :global
      10
      #uuid"8ed62381-c3ef-4174-ae8d-87789416bf65")))

(def store
  {:max-eid 1
   :max-tx-id 0
   :eav-index {0 {:todo/text "Buy eggs"}
               1 {:todo/tag :not-cheese}}})

(def eavs
  [(eav/->EAVT "todo1-id" :todo/text "Buy milk" 1)
   (eav/->EAVT "todo1-id" :todo/tag :milk 1)
   (eav/->EAVT 0 :todo/text "Buy eggs" 1)
   (eav/->EAVT 0 :todo/tag :eggs 1)
   (eav/->EAVT -3 :todo/text "Buy ham" 1)
   (eav/->EAVT -3 :todo/tag :ham 1)
   (eav/->EAVT 1 :todo/text "Buy cheese" 1)
   (eav/->EAVT 1 :todo/tag :cheese 1)
   (eav/->EAVT :do :eav/transient "something" 1)
   (eav/->EAVT -9 :eav/transient "something" 1)])

(def insertables'
  [(eav/->EAVT 2 :todo/text "Buy milk" :now)
   (eav/->EAVT 2 :todo/text "Buy milk" 1)
   (eav/->EAVT 2 :todo/tag :milk :now)
   (eav/->EAVT 2 :todo/tag :milk 1)
   (eav/->EAVT 0 :todo/tag :eggs :now)
   (eav/->EAVT 0 :todo/tag :eggs 1)
   (eav/->EAVT 3 :todo/text "Buy ham" :now)
   (eav/->EAVT 3 :todo/text "Buy ham" 1)
   (eav/->EAVT 3 :todo/tag :ham :now)
   (eav/->EAVT 3 :todo/tag :ham 1)
   (eav/->EAVT 1 :todo/text "Buy cheese" :now)
   (eav/->EAVT 1 :todo/text "Buy cheese" 1)
   (eav/->EAVT 1 :todo/tag :cheese :now)
   (eav/->EAVT 1 :todo/tag :cheese 1)
   (eav/->EAVT :do :eav/transient "something" :now)
   (eav/->EAVT 4 :eav/transient "something" :now)])

(def retractables'
  [(eav/->EAVT 1 :todo/tag :not-cheese :now)])

(def tempids'
  {"todo1-id" 2
   -3 3
   -9 4})

(def eav-index'
  {0 #:todo{:tag :eggs
            :text "Buy eggs"}
   1 #:todo{:tag :cheese
            :text "Buy cheese"}
   2 #:todo{:tag :milk
            :text "Buy milk"}
   3 #:todo{:tag :ham
            :text "Buy ham"}})

(deftest -eavs-test
  (testing "Tempids are replaced with generated eids, eids are left alone"
    (let [store' (store/-eavs store [(eav/->EAVT 1 :todo/tag :not-cheese :now)])
          {:keys [retractables max-eid eav-index]} store']
      (are [x y] (= x y)
        retractables' retractables
        max-eid 1
        {0 #:todo{:text "Buy eggs"}} eav-index))))

(deftest +eavs-test
  (testing "Tempids to eids, index updated, retractables detected"
    (is (= {:insertables insertables'
            :retractables retractables'
            :tempids tempids'
            :max-eid 4
            :max-tx-id 1
            :eav-index eav-index'}
           (store/+eavs store eavs)))))

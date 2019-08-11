(ns clara-eav.eav
  "This namespace is about defining EAVs and converting to EAVs from various 
  representations (mainly from an entity map)."
  (:require
   #?@(:clj [[clojure.spec.alpha :as s]]
       :cljs [[cljs.spec.alpha :as s]]))
  #?(:clj (:import (clojure.lang Indexed)
                   (java.util UUID))
     :cljs (:require-macros [clara.rules :as rules])))

(defrecord EAVT [e a v tx-id]
  ;; allows vector destructuring
  #?(:clj Indexed :cljs IIndexed)
  (#?(:clj nth :cljs -nth) [_ i]
    (case i, 0 e, 1 a, 2 v, 3 tx-id,
             #?(:clj (throw (IndexOutOfBoundsException.))
                :cljs (vector-index-out-of-bounds i 3))))
  (#?(:clj nth :cljs -nth) [_ i default]
    (case i, 0 e, 1 a, 2 v, 3 tx-id, default)))

(defn ->EAV
  [e a v]
  (->EAVT e a v nil))

(defn fact-type-fn
  "Clara-Rules `fact-type-fn` function for EAVs used in session creation.
  The attribute `:a` of an EAV it's used as it's type."
  [fact]
  (let [t (type fact)]
    (if (= EAVT t)
      (:a fact)
      t)))

(defn ancestors-fn
  "Clara-Rules `ancestors-fn` function for EAVs used in session creation.
  The `EAV` record type matches all eavs, the :eav/all type does the same."
  [type]
  (if (keyword? type)
    [EAVT :eav/all]
    (ancestors type)))

(s/def ::e #(or (string? %) 
                (keyword? %)
                (int? %)
                (uuid? %)))
(s/def ::a keyword?)
(s/def ::v some?)
(s/def ::tx-id (s/or :int integer? :now #{:now}))

(s/def ::record (s/and #(instance? EAVT %)
                       (s/keys :req-un [::e ::a ::v ::tx-id])))
(s/def ::record-seq (s/coll-of ::record))
(s/def ::vector (s/tuple ::e ::a ::v))
(s/def ::vector-t (s/tuple ::e ::a ::v ::tx-id))
(s/def ::vector-seq (s/coll-of ::vector))
(s/def ::eavt (s/or ::record ::record
                    ::vector-t ::vector-t))
(s/def ::eav-seq (s/coll-of ::eavt))
(s/def ::entity (s/map-of keyword? any?))
(s/def ::entity-seq (s/coll-of ::entity))
(s/def ::tx (s/or ::record ::record
                  ::record-seq ::record-seq
                  ::vector ::vector
                  ::vector-seq ::vector-seq
                  ::eavt ::eavt
                  ::eav-seq ::eav-seq
                  ::entity ::entity
                  ::entity-seq ::entity-seq))

(defn- tempid
  "Generates an uuid as a string to be used as a tempid for an entity map."
  []
  (str #?(:clj (UUID/randomUUID)
          :cljs (random-uuid))))

(s/fdef entity->eav-seq
  :args (s/cat :entity ::entity)
  :ret ::record-seq)
(defn- entity->eav-seq
  "Transforms an `entity` map into a list of EAVs. If the `entity` has a
  `:eav/eid` (set to non-tempid) subsequent operations will have upsert
  semantics. If not, a `:eav/eid` is generated as a tempid and subsequent
  operations will have insert semantics."
  [entity]
  (let [e (:eav/eid entity (tempid))
        ->eavt (fn [[k v]] (->EAVT e k v nil))
        entity' (-> entity
                    (dissoc :eav/eid))]
    (map ->eavt entity')))

(s/fdef eav-seq
        :args (s/cat :tx ::tx)
        :ret ::record-seq)
(defn eav-seq
  [tx]
  (->> tx
       (mapcat (fn [x]
                (cond
                  (instance? EAVT x) [x]
                  (vector? x) [(let [[e a v tx-id] x]
                                 (->EAVT e a v (or tx-id nil)))] ;;TODO - revisit tx-id, hacked to support eav-test
                  (map? x) (entity->eav-seq x)
                  :else (throw (ex-info "Invalid tx-data" {:tx tx})))))
       vec))

#_(defn eav-seq
    "Transforms transaction data `tx` into a sequence of eav records."
    [tx]
    (match/match (s/conform ::tx tx)
      ::s/invalid (throw (ex-info "Invalid transaction data (tx)" {:tx tx}))
      [::record eav-record] [eav-record]
      [::record-seq record-seq] record-seq
      [::vector eav-vector] [(apply ->EAVT eav-vector)]
      [::vector-seq vector-seq] (mapcat eav-seq vector-seq)
      [::eav-seq record-or-vector-seq] (mapcat (comp eav-seq second) record-or-vector-seq)
      [::entity entity] (entity->eav-seq entity)
      [::entity-seq entity-seq] (mapcat entity->eav-seq entity-seq)))

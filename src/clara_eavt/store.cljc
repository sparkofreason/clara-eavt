(ns ^:no-doc clara-eavt.store
  "A store keeps track of max-eid and maintains an EAV index."
  (:require [clara-eavt.eav :as eav]
            [medley.core :as medley]
            #?(:clj  [clojure.spec.alpha :as s]
               :cljs [cljs.spec.alpha :as s])))

(def ^:dynamic *store*
  "Dynamic atom of store to be used in rule productions, similar to other
  mechanisms from Clara."
  nil)

(s/def ::e
  (s/or :string string?
        :keyword keyword?
        :uuid uuid?
        :int int?))

(s/fdef tempid?
  :args (s/cat :e ::e)
  :ret boolean?)
(defn- tempid?
  "True if `e` is a tempid. Strings and negative ints are tempids; keywords,
  positive ints and uuids are not."
  [e]
  (or (string? e)
      (neg-int? e)))

(s/def ::max-eid integer?)
(s/def ::max-tx-id integer?)
(s/def ::eav-index map?)
(s/def ::insertables ::eav/record-seq)
(s/def ::retractables ::eav/record-seq)
(s/def ::tempids (s/map-of tempid? integer?))
(s/def ::store (s/keys :req-un [::max-eid ::max-tx-id ::eav-index]))
(s/def ::store-tx
  (s/keys :req-un [::max-eid ::max-tx-id ::eav-index]
          :opt-un [::insertables ::retractables ::tempids]))

(def init
  {:max-eid   0
   :max-tx-id 0
   :eav-index {}})

(s/fdef state
  :args (s/cat :store ::store-tx)
  :ret ::store)
(defn state
  "Remove extra keys from intermediary steps of computations and returns just 
  the store state."
  [store]
  (select-keys store [:max-eid :max-tx-id :eav-index]))

(s/fdef -eav
  :args (s/cat :store ::store-tx
               :eav ::eav/record)
  :ret ::store-tx)
(defn- -eav
  "Subtracts `eav` from `store` updating it's `:eav-index`. Returns the updated
  `store` including `:retractables` eavs."
  [store eav]
  (let [{:keys [e a v]} eav
        eav (assoc eav :tx-id :now)]
    (if (tempid? e)
      (throw (ex-info "Tempids not allowed in retractions" {:e e}))
      (-> store
          (update :retractables conj eav)
          (medley/dissoc-in [:eav-index e a])))))

(s/fdef -eavs
  :args (s/cat :store ::store
               :eavs ::eav/record-seq)
  :ret ::store-tx)
(defn -eavs
  "Called in retractions to obtain retractables. Throws if tempids are present
  in `eavs`, otherwise updates `store`'s `:eav-index`. Returns the updated store
  including `:retractables` eavs."
  [store eavs]
  (reduce -eav
          (-> store
              (update :max-tx-id inc)
              (assoc :retractables []))
          eavs))

(s/fdef +eav
  :args (s/cat :store ::store-tx
               :eav ::eav/record)
  :ret ::store-tx)
(defn- +eav
  "Adds `eav` to `store` updating it's `:max-eid` and `:eav-index`. Returns the
  updated `store` including `:insertables` eavs, `:retractables` eavs and 
  resolved `:tempids` map of {tempid -> eid}."
  [store eav]
  (let [{:keys [tempids max-eid max-tx-id eav-index]} store
        {:keys [e a v]} eav
        transient? (= :eav/transient a)]
    (if (tempid? e)
      (if-some [eid (get tempids e)]
        (-> store
            (update :insertables conj (assoc eav :e eid :tx-id :now))
            (cond-> (not transient?) (update :insertables conj (assoc eav :e eid :tx-id max-tx-id)))
            (cond-> (not transient?) (assoc-in [:eav-index eid a] v)))
        (let [new-eid (inc max-eid)]
          (-> store
              (update :insertables conj (assoc eav :e new-eid :tx-id :now))
              (cond-> (not transient?) (update :insertables conj (assoc eav :e new-eid :tx-id max-tx-id)))
              (assoc-in [:tempids e] new-eid)
              (assoc :max-eid new-eid)
              (cond-> (not transient?) (assoc-in [:eav-index new-eid a] v)))))
      (if transient?
        (update store :insertables conj (assoc eav :tx-id :now))
        (if-some [v' (get-in eav-index [e a])]
          (cond-> store
                  (not= v v') (-> (update :insertables conj (assoc eav :tx-id :now) (assoc eav :tx-id max-tx-id))
                                  (update :retractables conj (assoc eav :v v' :tx-id :now))
                                  (assoc-in [:eav-index e a] v)))
          (-> store
              (update :insertables conj (assoc eav :tx-id :now) (assoc eav :tx-id max-tx-id))
              (assoc-in [:eav-index e a] v)))))))

(s/fdef +eavs
  :args (s/cat :store ::store
               :eavs ::eav/record-seq)
  :ret ::store-tx)
(defn +eavs
  "Called in upserts to obtain insertables and retractables. Resolves tempids in
  `eavs` and updates `store`'s `:max-id` and `:eav-index`. Returns the updated
  store including `insertables` and `retractables` eavs and resolved tempids map
  {tempid -> eid}."
  [store eavs]
  (reduce +eav
          (-> store
              (update :max-tx-id inc)
              (assoc :insertables []
                     :retractables []
                     :tempids {}))
          eavs))

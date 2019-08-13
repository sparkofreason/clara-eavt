(ns clara-eav.test-helper
  (:require
    [clojure.spec.alpha :as s]
    [pinpointer.core :as p]
    #?@(:clj [[clojure.spec.test.alpha :as st]]
        :cljs [[cljs.spec.test.alpha :as st]])))

(set! s/*explain-out* p/pinpoint-out)

(defn spec-fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))

(ns clara-eavt.test-runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [clara-eavt.eav-test]
            [clara-eavt.store-test]
            [clara-eavt.session-test]
            [clara-eavt.rules-test]))

(doo-tests 'clara-eavt.eav-test
           'clara-eavt.store-test
           'clara-eavt.session-test
           'clara-eavt.rules-test)

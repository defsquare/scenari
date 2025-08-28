(ns scenari.v2.glue-test
  (:require [clojure.test :refer :all]
            [scenari.v2.glue :as glue]))

;; ns-proximity-score tests
(deftest ns-proximity-score-test
  (testing "Calculates proximity score between namespaces"
    (is (= 0 (glue/ns-proximity-score "some.ns" "other.ns")))
    (is (= 1 (glue/ns-proximity-score "some.ns" "some.other")))
    (is (= 2 (glue/ns-proximity-score "some.ns.path" "some.ns.other")))
    (is (= 3 (glue/ns-proximity-score "some.ns.path.one" "some.ns.path.two")))
    (is (= 0 (glue/ns-proximity-score "some" "other")))
    (is (= 0 (glue/ns-proximity-score "" "some")))
    (is (= 0 (glue/ns-proximity-score "some" "")))
    (is (= 1 (glue/ns-proximity-score "" "")))
    (is (= 1 (glue/ns-proximity-score "scenari" "scenari")))
    (is (= 3 (glue/ns-proximity-score "scenari.v2.test" "scenari.v2.test")))))

;; find-closest-glues-by-ns tests
(deftest find-closest-glues-by-ns-test
  (testing "Finds closest glues based on namespace proximity"
    (let [matched-glues [{:ns 'some.ns.far :name 'test-step}
                         {:ns 'some.ns :name 'other-step}
                         {:ns 'other.ns :name 'another-step}]]
      (is (= 2 (count (glue/find-closest-glues-by-ns matched-glues 'some.ns.feature))))
      (is (every? #(contains? #{'some.ns.far 'some.ns} (:ns %))
                 (glue/find-closest-glues-by-ns matched-glues 'some.ns.feature))))))

(deftest multiple-glues-same-score-test
  (testing "When there are multiple glues with same proximity score"
    (let [matched-glues [{:ns 'scenari.v2.glue :name 'test-step}
                         {:ns 'scenari.v2.other :name 'other-step}
                         {:ns 'other.ns :name 'another-step}]]
      (is (= 2 (count (glue/find-closest-glues-by-ns matched-glues 'scenari.v2.test))))
      (is (every? #(contains? #{'scenari.v2.glue 'scenari.v2.other} (:ns %))
                 (glue/find-closest-glues-by-ns matched-glues 'scenari.v2.test))))))

(deftest real-world-namespace-test
  (testing "With real-world namespace examples"
    (let [matched-glues [{:ns 'scenari.v2.glue :name 'test-step}
                         {:ns 'scenari.v2.other-glue.glue :name 'other-step}
                         {:ns 'scenari.other :name 'another-step}]]
      ;; Same namespace matches exactly
      (is (= 1 (count (glue/find-closest-glues-by-ns matched-glues 'scenari.v2.glue))))
      (is (= 'scenari.v2.glue (:ns (first (glue/find-closest-glues-by-ns matched-glues 'scenari.v2.glue)))))

      ;; Namespaces at same depth but different names
      (is (= 2 (count (glue/find-closest-glues-by-ns matched-glues 'scenari.v2.feature))))
      (is (every? #(contains? #{'scenari.v2.glue 'scenari.v2.other-glue.glue} (:ns %))
                 (glue/find-closest-glues-by-ns matched-glues 'scenari.v2.feature)))

      ;; Feature namespace is deeper than glue namespaces
      (is (= 2 (count (glue/find-closest-glues-by-ns matched-glues 'scenari.v2.feature.test))))
      (is (every? #(contains? #{'scenari.v2.glue 'scenari.v2.other-glue.glue} (:ns %))
                 (glue/find-closest-glues-by-ns matched-glues 'scenari.v2.feature.test))))))

;; find-glue-by-step-regex tests
(deftest find-glue-by-step-regex-test
  (testing "Finding glue with exact match"
    (let [step {:sentence "I do something special"}
          ns-feature 'test.ns
          glues [{:step "I do something special"
                  :ns 'test.ns
                  :name 'exact-match-fn}]]
      (is (= 'exact-match-fn (:name (glue/find-glue-by-step-regex step ns-feature glues))))
      (is (= 'test.ns (:ns (glue/find-glue-by-step-regex step ns-feature glues))))))

  (testing "Finding glue with regex pattern"
    (let [step {:sentence "I count 42 items"}
          ns-feature 'test.ns
          glues [{:step "I count {number} items"
                  :ns 'test.ns
                  :name 'matching-fn}]]
      (is (= 'matching-fn (:name (glue/find-glue-by-step-regex step ns-feature glues))))))

  (testing "Finding glue with string pattern"
    (let [step {:sentence "I use \"test-value\" as parameter"}
          ns-feature 'test.ns
          glues [{:step "I use {string} as parameter"
                  :ns 'test.ns
                  :name 'string-param-fn}]]
      (is (= 'string-param-fn (:name (glue/find-glue-by-step-regex step ns-feature glues))))))

  (testing "Multiple matching glues with different namespace proximity"
    (let [step {:sentence "I do common action"}
          ns-feature 'test.ns.feature
          glues [{:step "I do common action"
                  :ns 'other.ns
                  :name 'first-fn}
                 {:step "I do common action"
                  :ns 'test.ns.glue
                  :name 'second-fn}]]
      ;; It should prefer the glue with namespace closer to feature namespace
      (is (= 'second-fn (:name (glue/find-glue-by-step-regex step ns-feature glues))))
      (is (= 'test.ns.glue (:ns (glue/find-glue-by-step-regex step ns-feature glues))))))

  (testing "With token replacement in sentence"
    (let [step {:sentence "The value is 123"}
          ns-feature 'test.ns
          glues [{:step "The value is {number}"
                  :ns 'test.ns
                  :name 'number-fn}]]
      (is (= 'number-fn (:name (glue/find-glue-by-step-regex step ns-feature glues)))))))

(deftest find-glue-with-conflict-test
  (testing "With exact conflict in different namespaces - should throw exception"
    (let [step {:sentence "Duplicate in different namespaces"}
          ns-feature 'test.ns.feature
          glues [{:step "Duplicate in different namespaces"
                  :ns 'test.ns.one
                  :name 'first-fn}
                 {:step "Duplicate in different namespaces"
                  :ns 'test.ns.two
                  :name 'second-fn}]]
      ;; Both namespaces have the same proximity score to feature, should throw
      (is (thrown-with-msg?
            RuntimeException
            #"2 matching functions were found for the following step sentence"
            (with-redefs [clojure.test/do-report (fn [_m] nil)]
              (glue/find-glue-by-step-regex step ns-feature glues)))))))

(deftest simple-missing-glue-test
  (testing "No matching glue returns nil"
    (let [step {:sentence "This has no matching glue"}
          ns-feature 'test.ns
          glues []]
      (is (nil? (with-redefs [clojure.test/do-report (fn [_m] nil)]
                  (glue/find-glue-by-step-regex step ns-feature glues)))))))


(comment
  (run-tests))
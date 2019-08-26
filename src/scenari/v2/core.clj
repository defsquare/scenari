(ns scenari.v2.core
  (:require [clojure.test :as t :refer [is]]
            [scenari.core :as scenari]
            [scenari.utils :as utils]
            [instaparse.transform :as insta-trans]
            [clojure.java.io :as io]
            [clojure.string :as string])
  (:import (org.apache.commons.io FileUtils)))


(def ^:dynamic *testing-vars* (list))
(def ^:dynamic *tested-steps* nil)
(def ^:dynamic *glues* {})

(defn inc-tested-steps [] (dosync (commute *tested-steps* inc)))

(defmethod t/report :begin-feature [m] (t/with-test-out
                                         (t/inc-report-counter :executed-features)
                                         (println (str "________________________"))
                                         (println (str "Feature : " (:feature m)))
                                         (println)))

(defmethod t/report :end-feature [_] (t/with-test-out
                                       (println (str "________________________"))
                                       (println)))

(defmethod t/report :begin-scenario [m] (t/with-test-out
                                          (t/inc-report-counter :executed-scenarios)
                                          (println (str "Testing scenario : " (:scenario m)))))

(defmethod t/report :begin-step [m] (t/with-test-out (println " " (-> m :step :sentence-keyword name) (-> m :step :sentence name))))

(defmethod t/report :step-succeed [m] (t/with-test-out
                                        (inc-tested-steps)
                                        (println (str "      =====> " (:state m)))))

(defmethod t/report :step-failed [m] (t/with-test-out
                                       (println (utils/color-str :red "Step failed"))))

(defmethod t/report :scenario-succeed [m] (t/with-test-out
                                            (t/inc-report-counter :scenarios-succeed)
                                            (println (utils/color-str :green (:scenario m) " succeed !"))
                                            (println)))

(defmethod t/report :scenario-failed [m] (t/with-test-out
                                           (t/inc-report-counter :scenarios-failed)
                                           (println (utils/color-str :red (:scenario m) " failed at step " (:executed-steps m) " of " (:total-steps m)))
                                           (println)))

(defmethod t/report :missing-step [{:keys [step-sentence]}] (t/with-test-out
                                                              (println (utils/color-str :red "Missing step " step-sentence))
                                                              (println (utils/color-str :red (scenari/generate-step-fn {:sentence (get step-sentence :raw)})))))


(defn matching-regex-fn
  "return the tuple of fn/regex as a vector that match the step-sentence"
  [step glues]
  (let [{:keys [sentence]} step
        matching-regexes (filter (fn [regex]
                                   (not (empty? (re-find regex sentence))))
                                 (map re-pattern (keys glues)))]
    (if (> (count matching-regexes) 1)
      (throw (RuntimeException. (str (count matching-regexes) " matching functions were found for the following step sentence:\n " sentence ", please refine your regexes that match: \n" (apply str matching-regexes)))))
    (if (= (count matching-regexes) 0)
      (do (t/do-report {:type :missing-step, :step-sentence step})
          nil))
    (apply concat (select-keys glues [(first matching-regexes)]))))

(defn run-step [scenario-state step]
  (t/do-report {:type :begin-step, :step step})
  (let [[regex step-fn] (matching-regex-fn step *glues*)
        params (scenari/params-from-steps regex step)
        step-result (apply step-fn (cons scenario-state params))
        step-state (last step-result)
        any-fail? (some false? (drop-last step-result))]
    (when any-fail? (do
                      (t/do-report {:type :step-failed})
                      (throw (Exception. "step fail"))))
    (t/do-report {:type :step-succeed, :state step-state})
    step-state))

(defn run-scenario
  [scenario-state steps]
  (when-let [step (first steps)]
    (if-let [result (try
                      (run-step scenario-state step)
                      (catch Throwable e
                        {:ex e}))]
      (if (some? (:ex result))
        result
        (recur result (rest steps))))))

(defn run-scenarios [scenarios]
  (when-let [{name :scenario-name steps :steps} (first scenarios)]
    (t/do-report {:type :begin-scenario, :scenario name})
    (binding [*tested-steps* (ref 0)]
      (let [scenario-result (run-scenario {} steps)]
        (if (:ex scenario-result)
          (t/do-report {:type           :scenario-failed
                        :ex             (:ex scenario-result)
                        :scenario       name
                        :executed-steps @*tested-steps*
                        :total-steps    (count steps)})
          (t/do-report {:type :scenario-succeed :scenario name}))))
    (recur (rest scenarios))))

(defn run-feature [feature]
  (when-let [{glues                            :glues
              {:keys [feature-name scenarios]} :gherkin} (meta feature)]
    (binding [*testing-vars* (conj *testing-vars* feature)
              *glues* (merge *glues* (glues))]
      (t/do-report {:type :begin-feature, :feature feature-name})
      (run-scenarios scenarios)
      (t/do-report {:type :end-feature}))))

(defn file-from-fs-or-classpath [x]
  (let [r (io/resource x)
        f (when (and (instance? java.io.File x) (.exists x)) x)
        f-str (when (and (instance? String x) (.exists (io/as-file x))) x)]
    (io/as-file (or r f f-str))))

(defmulti ->feature
          "Read the spec and execute each step with the code setup by the defgiven, defwhen and defthen macro"
          (fn [spec]
            (letfn [(file-or-dir [x]
                      (cond (.isFile x) :file
                            (.isDirectory x) :dir))]
              (if (instance? String spec)
                (if-let [f (file-from-fs-or-classpath spec)]
                  (file-or-dir f)
                  :feature-as-str)
                (if (instance? java.io.File spec)
                  (file-or-dir spec)
                  (throw (RuntimeException. (str "type " (type spec) "for spec not accepted (only string or file)")))))))
          :default :file)

(defn get-feature-files [basedir]
  (letfn [(find-spec-files [basedir]
            (FileUtils/listFiles
              basedir
              (into-array ["story" "feature"])
              true                                          ;;recursive
              ))]
    (case (str (type basedir))
      "class java.lang.String" (if (.exists (java.io.File. basedir))
                                 (find-spec-files (java.io.File. basedir))
                                 (throw (RuntimeException. (str basedir " doesn't exists in path: " (java.lang.System/getProperty "user.dir")))))
      "class java.io.File" (find-spec-files basedir))))


(defmethod ->feature
  :dir
  [feature]
  (doseq [spec-file (get-feature-files feature)]
    (->feature spec-file)))

(defmethod ->feature
  :file
  [feature]
  (->feature (slurp (file-from-fs-or-classpath feature))))

(defmethod ->feature
  :feature-as-str
  [feature]
  (insta-trans/transform
    {:sentence          str
     :steps             (fn [& contents]
                          {:steps (mapv (fn [[_ [step-key] sentence]] {:sentence-keyword step-key
                                                                       :sentence         sentence
                                                                       :raw              (str (string/capitalize (name step-key)) " " sentence)}) contents)})
     :scenario_sentence (fn [a] {:scenario-name a})
     :scenario          (fn [& contents] (into {} contents))
     :scenarios         (fn [& contents] {:scenarios (into [] contents)})}
    (scenari/gherkin-parser feature)))


(defmacro deffeature [name feature & glues]
  `(do
     (ns-unmap *ns* '~name)
     (def ~(-> name
               (vary-meta assoc :glues `(fn [] (apply merge {} ~@glues)))
               (vary-meta assoc :gherkin (nth (->feature feature) 2) ;TODO refactor to clean feature data structure
                          ))
       (fn [] (run-feature (var ~name))))

     (run-feature #'~(symbol (str (ns-name *ns*) "/" name)))
     ))


;; TODO make a step evaluable as a standalone fun
;; TODO duplication, should be resolve with a macro
(defmacro defgiven [regex params & body]
  `{~regex (fn ~params (into [] [~@body]))})

(defmacro defand [regex params & body]
  `{~regex (fn ~params (into [] [~@body]))})

(defmacro defwhen [regex params & body]
  `{~regex (fn ~params (into [] [~@body]))})

(defmacro defthen [regex params & body]
  `{~regex (fn ~params (into [] [~@body]))})

(comment
  (defmacro steps-definition [& names]
    `(doseq [name# ~names]
       `(defmacro ~name# [regex# params# & body#]
          `{~regex# (fn ~params# (into [] [~@body#]))})))

  (steps-definition 'Given))


(comment t/*report-counters*
         (ref {:step_succeed 0, :test 0, :pass 0, :fail 0, :error 0}))
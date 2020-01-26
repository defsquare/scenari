(ns scenari.v2.core
  (:require [clojure.test :as t :refer [is]]
            [scenari.core :as scenari]
            [scenari.utils :as utils]
            [instaparse.transform :as insta-trans]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.tools.namespace.find :as ns-find])
  (:import (org.apache.commons.io FileUtils)
           (java.util UUID)))


;; ------------------------
;;          LOAD
;; ------------------------
(defn all-glues []
  (->> (all-ns)
       (mapcat #(vals (ns-publics %)))
       (map #(assoc (meta %) :ref %))
       (filter #(contains? % :step))))

(defn matching-regex-fn
  "return the tuple of fn/regex as a vector that match the step-sentence"
  [step]
  (let [{:keys [sentence]} step
        glues (all-glues)
        [matched-glue & conflicts] (filter (fn [{:keys [step]}]
                                             (not (empty? (re-find step sentence))))
                                           glues)]
    (if (not-empty conflicts)
      (throw (RuntimeException. (str (+ (count conflicts) 1) " matching functions were found for the following step sentence:\n " sentence ", please refine your regexes that match: \n" matched-glue "\n" (string/join "\n" conflicts)))))
    (if (not matched-glue)
      (do (t/do-report {:type :missing-step, :step-sentence step})
          nil))
    matched-glue))

(defn tab-params->params [tab-params]
  (when tab-params
    (let [[_ [_ & headers] & rows] tab-params
          param-names (map (comp keyword string/trim) headers)
          params-values (map (comp #(map string/trim %) rest) rows)]
      [{:type :table :val (mapv #(apply hash-map (interleave param-names %)) params-values)}])))

(defn sentence-params->params [[_ val]] {:type :value :val val})

(defn file-from-fs-or-classpath [x]
  (let [r (io/resource x)
        f (when (and (instance? java.io.File x) (.exists x)) x)
        f-str (when (and (instance? String x) (.exists (io/as-file x))) x)]
    (io/as-file (or r f f-str))))

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

(defn find-sentence-params [sentence]
  (insta-trans/transform
    {:SENTENCE (fn [& s] (->> s
                              (filter (fn [[type _]] (= type :string)))
                              (mapv sentence-params->params)))}
    (scenari/sentence-parser sentence)))

(comment
  (find-sentence-params "\"Bob Carter\" de l'organisation \"ElectreNG\""))

(defmulti read-source
          (fn [path]
            (letfn [(file-or-dir [x]
                      (cond (.isFile x) :file
                            (.isDirectory x) :dir))]
              (if (instance? String path)
                (if-let [f (file-from-fs-or-classpath path)]
                  (file-or-dir f)
                  :feature-as-str)
                (if (instance? java.io.File path)
                  (file-or-dir path)
                  (throw (RuntimeException. (str "type " (type path) "for spec not accepted (only string or file)")))))))
          :default :file)

(defmethod read-source
  :dir
  [path]
  (doseq [spec-file (get-feature-files path)]
    (read-source spec-file)))

(defmethod read-source
  :file
  [path-or-source]
  (read-source (slurp (file-from-fs-or-classpath path-or-source))))

(defmethod read-source :feature-as-str [source] source)

(defn ->feature-ast [source hooks]
  (insta-trans/transform
    {:SPEC              (fn [& s] (apply merge s))
     :narrative         (fn [& n] {:feature n})
     :sentence          str
     :steps             (fn [& contents]
                          {:steps (vec (map-indexed (fn [i [_ [step-key] sentence tab-params]]
                                                      (let [step (merge {:sentence-keyword step-key
                                                                         :sentence         sentence
                                                                         :raw              (str (string/capitalize (name step-key)) " " sentence)}
                                                                        (when-let [params (into (find-sentence-params sentence)
                                                                                                (tab-params->params tab-params))]
                                                                          {:params params}))]
                                                        (-> step
                                                            (assoc :order i)
                                                            (assoc :glue (matching-regex-fn step)))))
                                                    contents))})
     :scenario_sentence (fn [a] {:scenario-name a})
     :scenario          (fn [& contents] (into {:id (.toString (UUID/randomUUID))} contents))
     :scenarios         (fn [& contents] {:scenarios (into [] contents)
                                          :pre-run   (map #(assoc (meta %) :ref %) (:pre-run hooks))
                                          })}
    (scenari/gherkin-parser source)))

;; ------------------------
;;          RUN
;; ------------------------

(defn run-step [step scenario-state]
  (let [f (get-in step [:glue :ref])
        params (cons scenario-state (mapv :val (get step :params)))]
    (try (let [result (apply f params)
               state (last result)
               any-fail? (some false? (drop-last result))]
           (-> step
               (assoc :input-state scenario-state)
               (assoc :output-state state)
               (assoc :status (if any-fail? :fail :success))))
         (catch Throwable e
           (-> step
               (assoc :input-state scenario-state)
               (assoc :exception e)
               (assoc :status :fail))))))

(defn run-steps [steps state [step & others]]
  (if-not step
    steps
    (let [{:keys [output-state status] :as step-result} (run-step step state)
          steps (map #(if (= (:order step-result) (:order %)) step-result %) steps)]
      (if (= status :fail)
        steps
        (recur steps output-state others)))))

(defn run-scenario [scenario]
  (let [pending-steps (map #(assoc % :status :pending) (:steps scenario))
        result-steps (run-steps pending-steps {} pending-steps)]
    (-> scenario
        (assoc :steps result-steps)
        (assoc :status (if (contains? (set (map :status result-steps)) :fail) :fail :success)))))

(defn run-scenarios [scenarios [scenario & others]]
  (if-not scenario
    scenarios
    (let [scenario-result (run-scenario scenario)
          scenarios (map #(if (= (:id %) (:id scenario)) scenario-result %) scenarios)]
      (recur scenarios others))))

(defn run-feature [feature]
  (let [{:keys [scenarios pre-run] :as feature-ast} (get (meta feature) :feature-ast)]
    (doseq [{pre-run-fn :ref} pre-run]
      (pre-run-fn))
    (let [scenarios (run-scenarios scenarios scenarios)]
      (-> feature-ast
          (assoc :scenarios scenarios)
          (assoc :status (if (contains? (set (map :status scenarios)) :fail) :fail :success))))))

(defn run-features
  ([] (apply run-features (filter #(some? (:feature-ast (meta %))) (vals (ns-interns *ns*)))))
  ([& features] (map run-feature features)))


;; ------------------------
;;          DEFINE
;; ------------------------
(defmacro deffeature [name feature & [hooks]]
  (let [source# (read-source feature)
        feature-ast# `(->feature-ast ~source# ~hooks)]
    `(do
       (ns-unmap *ns* '~name)
       (t/deftest ~(-> name
                       (vary-meta assoc :source source#)
                       (vary-meta assoc :feature-ast feature-ast#)) []
                                                                    (scenari.v2.test/run-features (var ~name))) ;;TODO circular dependency...
       ~feature-ast#)))


(defn re->symbol [re]
  (-> (str re)
      (string/replace #"\\\"\(\.\*\)\\\"" "param")
      (string/replace #" " "-")
      symbol))

;; TODO make a step evaluable as a standalone fun
;; TODO duplication, should be resolve with a macro
(defmacro defgiven [regex params & body]
  `(defn ~(-> (re->symbol regex)
              (vary-meta assoc :step regex)) ~params (into [] [~@body])))

(defmacro defand [regex params & body]
  `(defn ~(-> (re->symbol regex)
              (vary-meta assoc :step regex)) ~params (into [] [~@body])))

(defmacro defwhen [regex params & body]
  `(defn ~(-> (re->symbol regex)
              (vary-meta assoc :step regex)) ~params (into [] [~@body])))

(defmacro defthen [regex params & body]
  `(defn ~(-> (re->symbol regex)
              (vary-meta assoc :step regex)) ~params (into [] [~@body])))
(ns scenari.v2.core
  (:require [clojure.test :as t]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [instaparse.transform :as insta-trans]
            [scenari.core :as scenari])
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

(defn ns-proximity-score [ns-glue ns-feature]
  (loop [[ns-glue & child-ns-glue] (string/split ns-glue #"\.")
         [ns-feature & child-ns-feature] (string/split ns-feature #"\.")
         score 0]
    (if (or (not ns-feature) (not ns-glue) (not= ns-glue ns-feature))
      score
      (recur child-ns-glue child-ns-feature (inc score)))))

(defn find-closest-glues-by-ns [matched-glues ns-feature]
  (let [[score closest-glues-by-ns] (->> matched-glues
                                         (map #(hash-map (ns-proximity-score (str ns-feature) (str (:ns %))) [%]))
                                         (apply merge-with into)
                                         (apply max-key key))]
    closest-glues-by-ns))


(defn find-glue-by-step-regex
  "return the tuple of fn/regex as a vector that match the step-sentence"
  [step ns-feature]
  (let [{:keys [sentence]} step
        glues (all-glues)
        matched-glues (filter #(seq (re-matches (:step %) sentence)) glues)]
    (cond
      (empty? matched-glues)
      (do (t/do-report {:type :missing-step, :step-sentence step})
          nil)
      (> (count matched-glues) 1)
      (let [[matched-glue & conflicts] (find-closest-glues-by-ns matched-glues ns-feature)]
        (if conflicts
          (throw (RuntimeException. (str (+ (count conflicts) 1) " matching functions were found for the following step sentence:\n " sentence ", please refine your regexes that match: \n" matched-glue "\n" (string/join "\n" conflicts))))
          (assoc matched-glue
            :warning (str (count matched-glues) " matching functions were found for this step sentence"))))
      :else (first matched-glues))))

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

(defn ->feature-ast [source hooks ns-feature]
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
                                                            (assoc :glue (find-glue-by-step-regex step ns-feature)))))
                                                    contents))})
     :scenario_sentence (fn [a] {:scenario-name a})
     :scenario          (fn [& contents] (into {:id       (.toString (UUID/randomUUID))
                                                :pre-run  (map #(assoc (meta %) :ref %) (:pre-scenario-run hooks))
                                                :post-run (map #(assoc (meta %) :ref %) (:post-scenario-run hooks))}
                                               contents))
     :scenarios         (fn [& contents] {:scenarios (into [] contents)
                                          :pre-run   (map #(assoc (meta %) :ref %) (:pre-run hooks))})}
    (scenari/gherkin-parser source)))

;; ------------------------
;;          RUN
;; ------------------------

(defn run-step [step scenario-state]
  (binding [clojure.test/*report-counters* (ref clojure.test/*initial-report-counters*)]
    (let [f (get-in step [:glue :ref])
          params (cons scenario-state (mapv :val (get step :params)))]
      (try (let [result (apply f params)
                 state (last result)
                 any-fail? (> (:fail (deref clojure.test/*report-counters*)) 0)]
             (-> step
                 (assoc :input-state scenario-state)
                 (assoc :output-state state)
                 (assoc :status (if any-fail? :fail :success))))
           (catch Throwable e
             (-> step
                 (assoc :input-state scenario-state)
                 (assoc :exception e)
                 (assoc :status :fail)))))))

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
        _ (doseq [{pre-run-fn :ref} (:pre-run scenario)]
            (pre-run-fn))
        result-steps (run-steps pending-steps {} pending-steps)
        _ (doseq [{post-run-fn :ref} (:post-run scenario)]
            (post-run-fn))]
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
  (let [feature# `~(eval feature)
        name# `~(if (symbol? name) name (eval name))
        source# (read-source feature#)
        feature-ast# `(->feature-ast ~source# ~hooks *ns*)]
    `(do
       (ns-unmap *ns* '~name#)
       (require '[scenari.v2.test])
       (t/deftest ~(-> name#
                       (vary-meta assoc :source source#)
                       (vary-meta assoc :feature-ast feature-ast#)) []
                                                                    (scenari.v2.test/run-features (var ~name#)))
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
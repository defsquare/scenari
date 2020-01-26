(ns scenari.v2.test
  (:require [clojure.test :as t]
            [scenari.core :as scenari]
            [scenari.utils :as utils]))

(def ^:dynamic *feature-succeed* nil)
(def initial-report {:executed-features 0 :feature-succeed 0 :feature-failed 0 :scenarios-succeed 0 :scenarios-failed 0})

(defmethod t/report :begin-feature [m] (t/with-test-out
                                         (t/inc-report-counter :executed-features)
                                         (println (str "________________________"))
                                         (println (str "Feature : " (:feature m)))
                                         (println)))

(defmethod t/report :feature-succeed [_] (t/inc-report-counter :feature-succeed))

(defmethod t/report :end-feature [{:keys [succeed?]}]
  (if succeed? (t/inc-report-counter :feature-succeed) (t/inc-report-counter :feature-failed))
  (t/with-test-out
    (println (str "________________________"))
    (println)))

(defmethod t/report :begin-scenario [m] (t/with-test-out
                                          (t/inc-report-counter :executed-scenarios)
                                          (println (str "Testing scenario : " (:scenario m)))))

(defmethod t/report :begin-step [m] (t/with-test-out (println " " (-> m :step :sentence-keyword name) (-> m :step :sentence name))))

(defmethod t/report :step-succeed [m] (t/with-test-out
                                        (println (str "      =====> " (:state m)))))

(defmethod t/report :step-failed [m] (t/with-test-out
                                       (println (utils/color-str :red "Step failed"))))

(defmethod t/report :scenario-succeed [m] (t/with-test-out
                                            (t/inc-report-counter :scenarios-succeed)
                                            (println (utils/color-str :green (:scenario m) " succeed !"))
                                            (println)))

(defmethod t/report :scenario-failed [m] (t/with-test-out
                                           (reset! *feature-succeed* false)
                                           (t/inc-report-counter :scenarios-failed)
                                           (println (utils/color-str :red (:scenario m) " failed at step " (:executed-steps m) " of " (:total-steps m)))
                                           (println (utils/color-str :red (:ex m)))))

(defmethod t/report :missing-step [{:keys [step-sentence]}] (t/with-test-out
                                                              (println (utils/color-str :red "Missing step for : " (get step-sentence :raw)))
                                                              (println (utils/color-str :red (scenari/generate-step-fn {:sentence (get step-sentence :raw)})))))

(defmethod t/report :features-summary [{:keys [executed-features scenarios-succeed scenarios-failed]}]
  (t/with-test-out
    (println "\nRan" executed-features "features containing"
             (+ scenarios-succeed scenarios-failed) "scenarios.")
    (println scenarios-succeed "success," scenarios-failed "fail.")))

(defn run-feature [feature]
  (when-let [{{:keys [feature-name scenarios]} :feature-ast} (meta feature)]
    (binding [t/*report-counters* (ref initial-report)
              *feature-succeed* (atom true)]
      (t/do-report {:type :begin-feature, :feature feature-name})
      (doseq [scenario scenarios]
        (t/do-report {:type :begin-scenario, :scenario (:name name)})
        (let [scenario-result (loop [state {}
                                     [step & others] (:steps scenario)]
                                (if-not step
                                  true
                                  (do
                                    (t/do-report {:type :begin-step, :step step})
                                    (let [step-result (scenari.v2.core/run-step step state)]
                                      (if (= (:status step-result) :fail)
                                        (do
                                          (t/do-report {:type :step-failed})
                                          false)
                                        (do
                                          (t/do-report {:type :step-succeed, :state (:output-state step-result)})
                                          (recur (:output-state step-result) others)))))))]
          (if scenario-result
            (t/do-report {:type :scenario-succeed, :scenario (:scenario-name scenario)})
            (t/do-report {:type     :scenario-failed
                          :scenario (:scenario-name scenario)}))))
      (t/do-report {:type :end-feature, :feature feature-name :succeed? @*feature-succeed*})
      @t/*report-counters*)))

(defn run-features
  ([] (apply run-features (filter #(some? (:feature-ast (meta %))) (vals (ns-interns *ns*)))))
  ([& features]
   (let [reports (->> features
                      (map run-feature)
                      (apply merge-with +))]
     (t/do-report (assoc reports :type :features-summary))
     reports)))
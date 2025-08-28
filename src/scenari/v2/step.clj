(ns scenari.v2.step
  (:require [instaparse.core :as insta]
            [scenari.v2.parser :as parser]))

(defn- extract-data-as-args [sentence-elements]
  (let [data-count (count (filter (fn [c] (contains? #{:string :tab_params} (first c))) sentence-elements))
        data-args (clojure.string/join " " (for [i (range data-count)] (str "arg" i)))]
    (str "[state " data-args "]")))

(defn generate-step-fn
  "return a string representing a spexec macro call corresponding to the sentence step"
  [step-sentence]
  (let [{:keys [sentence tab_params]} step-sentence
        sentence-ast (parser/step sentence)
        [_ [step-type] & sentence-elements] sentence-ast
        sentence-elements (if tab_params (conj (vec sentence-elements) [:tab_params []]) sentence-elements)]
    (if (insta/failure? sentence-ast)
      (do (prn (insta/get-failure sentence-ast
                                  )) (throw (ex-info (:reason (insta/get-failure sentence-ast)) {:parsed-text step-sentence}))))
    (str (case step-type
           :given "(defgiven \""
           :and   "(defand \""
           :when  "(defwhen \""
           :then  "(defthen \""
           "(defwhen \"")
         (apply str (map (fn [[what? data]]
                           (case what?
                             :words data
                             :string "{string}"
                             :number "{number}"
                             :tab_params ""
                             "test")) sentence-elements))
         "\"  "
         (extract-data-as-args sentence-elements)
         (case step-type
           :given "  (do \"setup or assert correct tested component state\"))"
           :when  "  (do \"something\"))"
           :then  "  (do \"assert the result of when step\"))"
           "  (do \"something\"))"))))

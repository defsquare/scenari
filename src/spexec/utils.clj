(ns spexec.utils)

(defn get-in
  ""
  ([tree ks]
     (get-in tree ks nil))
  ([tree ks default]
     (if (or (empty? ks) (nil? ks))
       (if (empty? tree)
         default
         tree)
       (if (keyword? (first tree))
         ;;mono node root tree
         (if (= (first tree) (first ks)) (recur (rest tree) (rest ks) default) default)
         ;;multi nodes root tree
         (recur (mapcat (fn [node]
                          ;;remove first element and add to the
                          (rest node))
                        (filter (fn [node]
                                  (if (keyword? (first node))
                                    (= (first node) (first ks))
                                    false)) tree))
                (rest ks)
                default)))))

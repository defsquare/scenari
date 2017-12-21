(ns scenari.utils)

(defn contextual-eval [ctx expr]
  (eval
   `(let [~@(mapcat (fn [[k v]] [k `'~v]) ctx)]
      ~expr)))

(defmacro local-context []
  (let [symbols (keys &env)]
    (zipmap (map (fn [sym] `(quote ~sym)) symbols) symbols)))

(defn readr [prompt exit-code]
  (let [input (clojure.main/repl-read prompt exit-code)]
    (if (= input ::tl)
      exit-code
      input)))

(defmacro break-with-repl []
  `(clojure.main/repl
    :prompt #(print "debug=> ")
    :read readr
    :eval (partial contextual-eval (local-context))))

(defn get-whole-in
  ""
  ([tree ks]
   (get-whole-in tree ks nil))
  ([tree ks default]
   (if (or (empty? ks) (nil? ks))
     (if (empty? tree)
       default
       tree)
     (if (keyword? (first tree))
       ;;mono node root tree
       (if (= (first tree) (first ks))
         (if (empty? (rest ks))
           tree
           (recur (rest tree) (rest ks) default))
         default)
       ;;multi nodes root tree
       (recur (mapcat vector
                      (filter (fn [node]
                                (if (keyword? (first node))
                                  (= (first node) (first ks))
                                  false)) tree))
              (rest ks)
              default)))))

(defn get-in-tree
  ""
  ([tree ks]
     (get-in-tree tree ks nil))
  ([tree ks default]
     (if (or (empty? ks) (nil? ks))
       (if (empty? tree)
         default
         tree)
       (if (keyword? (first tree))
         ;;mono node root tree
         (if (= (first tree) (first ks))
           (recur (rest tree) (rest ks) default)
           default)
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

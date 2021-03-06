(ns lein-modules.inheritance
  (:use [lein-modules.common :only (config parent)])
  (:require [leiningen.core.project :as prj]))

(def normalizer (partial map (comp prj/dependency-vec prj/dependency-map)))

(defn normalize-deps
  "Fully-qualifies any dependency vector within the profile map. Only
  required for 2.3.4"
  [m]
  (if (:dependencies m)
    (update-in m [:dependencies] normalizer)
    m))

(defn filter-profiles
  "We don't want to inherit the :user or :leiningen/test profiles"
  [m]
  (let [ignored [:user]
        lein-ns (->> m keys (filter #(= "leiningen" (namespace %))))]
    (apply dissoc m (concat ignored lein-ns))))

(defn compositor
  "Returns a reducing function that turns a non-composite profile into
   a composite, e.g. {:test {:a 1}} becomes {:test
   [:test-foo] :test-foo {:a 1}} for a project named 'foo'. Composite
   profiles are simply concatenated"
  [project]
  (fn [m [k v]]
    (if (prj/composite-profile? v)
      (update-in m [k] (comp vec distinct concat) v)
      (let [n (keyword (format "%s%s-%s" (or (namespace k) "") (name k) (:name project)))]
        (assoc (update-in m [k] #(vec (cons n %))) n (normalize-deps v))))))

(defn compositize-profiles
  "Return a profile map containing all the profiles found in the
  project and its ancestors, resulting in standard profiles,
  e.g. :test and :dev, becoming composite"
  [project]
  (loop [p project, result nil]
    (if (nil? p)
      result
      (recur (parent p)
        (reduce (compositor p) result
          (conj (select-keys (:modules p) [:inherited])
            (filter-profiles (:profiles (meta p)))))))))

(defn inherit
  "Add profiles from parents, setting any :inherited ones if found,
  where a parent profile overrides a grandparent."
  [project]
  (let [compost (compositize-profiles project)]
    (-> (prj/add-profiles project compost)
      (vary-meta update-in [:profiles] merge compost)
      (prj/set-profiles (if (:inherited compost)
                          [:inherited :default]
                          [:default])))))

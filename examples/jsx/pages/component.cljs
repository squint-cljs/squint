(ns pages.component)

(defn my-func [a b c d]
  (* a b c d))

(defn Comp2 [^:js {:keys [x] :as props}]
  #jsx [:<>
        [:p "The prop:" x]
        [:p "Children"]
        (.-children props)])

(defn MyComponent []
  (let [x 1337]
    #jsx [:<>
          [:p "Paragraph" x]
          [:code "(+ 1 2 3)"]
          [:a {:href "foo"} "Link"]
          (my-func x 1 2 3)
          [Comp2 {:x "value"}
           [:pre "Child1"]
           [:pre "Child2"]]
          [:pre [:code (pr-str {:x 1})]]
          [:pre (let [x 1] x)]
          [:<> 1 2 3 4]]))

(ns defclass-test
  (:require [squint.core :refer [defclass]]))

(defclass class-1
  (field -x)
  (field __secret :dude)
  (constructor [this x-arg] (set! -x x-arg))

  Object
  ;; tests munging of method names
  (get-name-separator [_] (str "-" -x))
  )

(defclass Class2
  (extends class-1)
  (field -y 1)
  (constructor [th-is x y-z]
               (super (+ x y-z))
               th-is
               )

  Object
  (dude [_] ;; use this arg
        (str -y
             (super.get-name-separator)
             (.get-name-separator super)))

  (^:async myAsync [_]
   (let [x (js-await (js/Promise.resolve 1))
         y (js-await (let [x (js-await (js/Promise.resolve 2))
                           y (js-await (js/Promise.resolve 3))]
                       (+ x y)))]
     (+ x y)))
  (^:gen myGen [_]
   (js-yield 1)
   (js-yield 2))

  (^:gen ^:async myAsyncGen [_]
   (js-await {})
   (js-yield :foo)
   (js-yield :bar))

  (toString [this] (str "<<<<" (.dude this) ">>>>") ))

(def c (new Class2 1 2))

(^:async
 (fn []
   (let [async-gen-consumer (js/eval "async (gen) => {
let res = [];
for await (const val of gen) {
res.push(val);
}
return res;
} ")]
     [(.toString c) (.dude c) (js-await (.myAsync c)) (vec (.myGen c)) (js-await (async-gen-consumer (.myAsyncGen c)))])))

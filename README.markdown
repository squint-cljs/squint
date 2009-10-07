Scriptjure is a Clojure library for generating javascript from Clojure forms. Its primary goal is to make it simple to embed "glue" javascript in Clojure webapps. Scriptjure code is intended to be readable.

At the moment, Scriptjure is very simple, but is still under active development.

Sample Code
-----------
(use [com.reasonr.scriptjure :only (js)])
(js (fn foo [e]
       (var x 42)
       (return (+ x e))))

results in the string "function foo (e) { x = 42; return (x + e); }"


Rules
------

(js) is a macro that takes one or more sexprs and returns a string that is valid javascript.

* Clojure numbers and strings are converted directly:

   (js 42) => "42"
   (js "foo") => "\"foo\""

* Clojure symbols and keywords are converted to javascript symbols:

   (js foo) => "foo"
   (js :bar) => "bar"

   Since JS is a macro, symbols will not be evaluated, so there is no need to quote them. Actually, (js 'foo) will be interpreted as (js (quote foo)), which is probably not what you want. Scriptjure makes no attempt to verify that a generated symbol is valid.

* Clojure arrays and maps are converted to array literals, and JSON:

   (js [1 2 3]) => "[1, 2, 3]"
   (js {:packages "columnchart"}) => "{packages: \"columnchart\"}"

   Note that JSON map keys aren't necessarily converted to strings. If you want the key to be a string rather than a symbol, use a Clojure string. Yes, this doesn't follow the JSON spec, but some JS libraries require this.

* Lists where the first element is a symbol are converted to function calls, and "special forms." The list of special forms is currently [var . if fn set! return new]. If the head of the list is not one of the special forms, a list returns a normal function call

** Normal Function calls. The head of the list is the name of the function. All remaining items in the list are treated as arguments to the call:

   (js (alert "hello world")) => "alert(\"hello world\")"
   (js (foo x y)) => "foo(x, y)"

** Special Forms. If the head of the list is a symbol in the special forms list, rather than resulting in a normal function call, something else will happen:

*** var
    (var symbol value)
    Var takes two arguments, and defines a new variable

   (js (var x 3)) => "var x = 3;"

*** set!
   (set! symbol value)
   Takes two arguments, assignment.

   (js (set! x 5)) => "x = 5;"

*** return
   (return value)

   Takes one argument, results in a return statement

   (js (return x)) => "return x;"

*** new
   (new Obj & args)

   Results in a new statement. The first argument is the object. All remaining items in the list are treated as arguments to the contructor.
   (js (new google.visualization.Query url)) => "new google.visualization.Query(url)"

*** dot Method calls
    (. method Obj & args)

    Works like the . form in Clojure. If the first item in the list is a dot, calls method on Obj. All remaining items are arguments to the method call
   (js (. bar google.chart :a :b)) => "google.chart.bar(a,b)"

   .method also works:

   (js (.bar google.chart :a :b)) => "google.chart.bar(a,b)"

*** fn
   (fn [args] & body)
   (fn name [args] & body)

   Results in a function expression or statement. Forms in body are separated by semicolons

  (js (fn [e]
       (var x 42)
       (return (+ x e)))) => "function (e) { var x = 42; return (x + e); }"

*** infix operators
   (infix x y)
   If the head of the list is a symbol in the infix operator list, the list results in infix math. The current list is [+ - / * == === < > <= >= !=]. All infix operatations currently only support two operands. All infix expressions are parenthesized to avoid precedence issues.

   (js (> x y)) => "(x > y)"

* Getting data into JS
  
  To get the value of a clojure symbol into a javascript call, use (clj)

  (let [foo 42]
     (js (+ 3 (clj foo)))) => (js (+ 3 42)) => "(3 + 42)"

  clj is a "marker" in the js macro. The contents of the clj form are evaluated according to normal Clojure rules, and the result is passed into (js). Since clj is not a var, it never needs to be qualified. The clj form is only valid inside a (js) form. The clj form is allowed to return anything that scriptjure knows how to handle.

  clj can be use anywhere in a js form:

  (js (fn (clj foo) [x] (return x))) This will return a javascript function, with the name being whatever Clojure value foo resolves to.
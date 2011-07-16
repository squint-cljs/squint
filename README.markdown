Scriptjure is a Clojure library for generating javascript from Clojure forms. Its primary goal is to make it simple to embed "glue" javascript in Clojure webapps. Generated Scriptjure javascript is intended to be readable.

At the moment, Scriptjure is very simple, but is still under active development.

Sample Code
===========
    (use [com.reasonr.scriptjure :only (js)])
    (js (fn foo [e]
         (var x 42)
         (return (+ x e))))

results in the string "function foo (e) { x = 42; return (x + e); }"


Rules
=====

`(js)` is a macro that takes one or more sexprs and returns a string that is valid javascript.

Numbers
-------

Clojure numbers are converted as you would expect:
    (js 42) 
    => "42"

Strings
------
    (js "foo") 
    => "\"foo\""

Symbols 
-------
Clojure symbols and keywords are converted to javascript symbols:

    (js foo) 
    => "foo"
    (js :bar) 
    => "bar"

Since JS is a macro, symbols will not be evaluated, so there is no need to quote them. Actually, (js 'foo) will be interpreted as (js (quote foo)), which is probably not what you want. Scriptjure makes no attempt to verify that a generated symbol is defined in the JS environment.

Arrays, Maps
----------
Clojure arrays and maps are converted to array literals, and JSON:

    (js [1 2 3]) 
    => "[1, 2, 3]"
    (js {:packages "columnchart"}) 
    => "{packages: \"columnchart\"}"

Note that JSON map keys aren't necessarily converted to strings. If you want the key to be a string rather than a symbol, use a Clojure string. Yes, this doesn't follow the JSON spec, but some JS libraries require this.

Lists
----
Lists where the first element is a symbol are converted to function calls, and "special forms." If the head of the list is not one of the special forms, a list returns a normal function call.

Normal Function Calls
-----------------
 The head of the list is the name of the function. All remaining items in the list are treated as arguments to the call:

    (js (alert "hello world")) 
    => "alert(\"hello world\")"
    (js (foo x y)) 
    => "foo(x, y)"

Special Forms
-----------
If the head of the list is a symbol in the special forms list, rather than resulting in a normal function call, something else will happen:

**var**
    (var symbol value)
Var takes two arguments, and defines a new variable

    (js (var x 3)) 
    => "var x = 3;"

**set!**
    (set! symbol value)
Takes two arguments, assignment.

    (js (set! x 5)) 
    => "x = 5;"

**if**
    (if test true-form & false-form)
Returns a javascript if statement. Like Clojure, true-form and false-form take one form each. If you want multiple statements in the body, combine with a do statement.

    (js (if (== foo 3) (foo x) (bar y)))
    => "if ( (foo == 3) ) {
       foo(x);
       }
       else {
       bar(y);
       }"

**try / catch / finally**

    (try expr* catch-clause? finally-clause?)
    catch-clause -> (catch e expr*)
    finally-clause -> (finally expr*)

Returns a JavaScript `try` / `catch` / `finally` block.  All non-`catch` and non-`finally` forms within a `try` form are executed in an implicit `do` statement.  The `catch` clause (if present) generates an unconditional `catch` block (multiple conditional `catch` blocks are not supported at this time), with `e` bound to the exception object. The `finally` clause (if present) is used to generate a `finally` block.  All expressions in the `catch` and `finally` clauses are executed in implicit `do` statements.

    (js (try
          (set! x 5)
        (catch e
          (print (+ "BOOM: " e)))
        (finally
          (print "saved!"))))
    => "try{
        x = 5;
        }
        catch(e){
        print((\"BOOM: \" + e));
        }
        finally{
        print(\"saved!\");
        }"

An Exception will be thrown if there are no `catch` or `finally` clauses, or if there are more than one of either.

**return**
    (return value)

Takes one argument, results in a return statement

    (js (return x)) 
    => "return x;"

**delete**
    (delete value)

Takes one argument, results in a delete statement

    (js (delete x)) 
    => "delete x;"

**new**
    (new Obj & args)

Results in a new statement. The first argument is the object. All remaining items in the list are treated as arguments to the contructor.

    (js (new google.visualization.Query url)) 
    => "new google.visualization.Query(url)"

**aget**
    (aget obj & indexes)

    (js (aget foo 42))
    => "foo[42]"

Array access can also be chained.  This is helpful not only for multidimensional arrays, but for reaching deep into objects using a series of keys (similar to `clojure.core/get-in`)

    (js (aget foo bar "baz"))
    => "foo[bar][\"baz\"]"

To set an array, combine with set!
 
    (js (set! (aget foo 42) 13))

**do**
   (do & exprs)

Returns the series of expressions, separated by semicolons
  
    (js (do
             (var x 3)
             (var y 4)))
    => "var x = 3;
        var y = 4;"

**dot Method calls**
    (. method Obj & args)

Works like the dot form in Clojure. If the first item in the list is a dot, calls method on Obj. All remaining items are arguments to the method call
    (js (. google.chart bar :a :b))
    => "google.chart.bar(a,b)"

   .method also works:

    (js (.bar google.chart :a :b)) 
    => "google.chart.bar(a,b)"

**fn**
    (fn [args] & body)
    (fn name [args] & body)

Results in a function expression or statement. Forms in body are separated by semicolons

    (js (fn [e]
       (var x 42)
       (return (+ x e)))) 
    => "function (e) { var x = 42; return (x + e); }"

**infix operators**
    (infix x y)
If the head of the list is a symbol in the infix operator list, the list results in infix math. The current list is [+ - / * == === < > <= >= !=]. All infix operatations currently only support two operands. All infix expressions are parenthesized to avoid precedence issues.

    (js (> x y)) 
    => "(x > y)"

** Getting data into JS **
  
To get the value of a clojure expression into javascript, use (clj)

    (let [foo 42]
        (js (+ 3 (clj foo)))) 
    => (js (+ 3 42)) => "(3 + 42)"

`clj` is a "marker" in the js macro. The `clj` can contain arbitrary normal Clojure, and the result is passed into `(js)`. The `clj` form is allowed to return anything that scriptjure knows how to handle. Since `clj` is not a var, it never needs to be qualified. The clj form is only valid inside a `(js)` form. 

`clj` can be use anywhere in a `js` form:

    (js (fn (clj foo) [x] (return x))) 

This will return a javascript function, with the name being whatever Clojure value foo resolves to.

** Composing JS in Clojure **

If you want to pass a js form from one clojure function to another, use js*

    (let [extra-js (js* (do (baz x) (var y 4)))]
         (defn gen-js [extra-js]
             (js (fn foo [x]
                      (bar x)
                      (clj extra-js)))))
    => "function foo(x) {
              bar(x);
              baz(x);
              var y = 4;
         }"

`cljs` and `cljs*` are shortcuts for `(js (clj ...))` and `(js* (clj ..))` respectively. Note that both only take one form. 

License
=======
Scriptjure is licensed under the EPL, the same as Clojure core. See epl-v10.html in the root directory for more information.
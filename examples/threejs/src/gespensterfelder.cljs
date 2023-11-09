(ns gespensterfelder
  {:clj-kondo/config '{:linters {:unresolved-symbol {:exclude [js-await]}}}}
  (:require ["squint-cljs/core.js" :as squint]
            ["three" :as three]
            ["three/addons/postprocessing/EffectComposer.js" :refer [EffectComposer]]
            ["three/addons/postprocessing/RenderPass.js" :refer [RenderPass]]
            ["three/addons/postprocessing/UnrealBloomPass.js" :refer [UnrealBloomPass]]
            ["three/addons/postprocessing/FilmPass.js" :refer [FilmPass]]
            ["easing" :as easing]
            ["bezier-easing" :as BezierEasing]
            ["dat.gui" :as dat]
            ["stats.js" :as Stats]))

#_(warn-on-lazy-reusage!)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; controls

(def params
  {:magnitude 0.1
   :x-scale 1.0
   :y-scale 0.5
   :sin-x-scale 0.0
   :cos-y-scale 1.0})

(def gui
  (doto (dat/GUI.)
    (.add params "magnitude" 0.0 0.5)
    (.add params "x-scale" 0.0 2.0)
    (.add params "y-scale" 0.0 2.0)
    (.add params "sin-x-scale" 0.0 2.0)
    (.add params "cos-y-scale" 0.0 2.0)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; basic three.js setup

(def scene
  (three/Scene.))

(def origin (three/Vector3.))

(def camera
  (doto (three/PerspectiveCamera. 75 (/ (.-innerWidth js/window) (.-innerHeight js/window)) 0.1 1000)
    (-> :position ((fn [pos] (.set pos 0 0 70))))
    (.lookAt origin)))

(def renderer
  (doto (three/WebGLRenderer. (clj->js {:antialias true}))
    (.setPixelRatio (.-devicePixelRatio js/window))
    (.setSize (.-innerWidth js/window) (.-innerHeight js/window))
    (-> (assoc! :toneMapping three/ReinhardToneMapping
                :toneMappingExposure (Math/pow 1.4 5.0))
        (-> :domElement (->> (.appendChild (.-body js/document)))))))

;; effects composer for after effects
(def composer 
  (let [w (get js/window :innerWidth)
        h (get js/window :innerHeight)]
    (doto (EffectComposer. renderer)
      (.addPass (RenderPass. scene camera))
      (.addPass (UnrealBloomPass. (three/Vector2. w h) ; viewport resolution
                                        0.3   ; strength
                                        0.2   ; radius
                                        0.8)) ; threshold
      (.addPass (assoc! (FilmPass. 0.25  ; noise intensity
                                   0.26  ; scanline intensity
                                   648   ; scanline count
                                   false); grayscale
                          :renderToScreen true)))))

;; animation helpers
(def current-frame (atom 0))

(def linear (easing 76 "linear" {:endToEnd true}))

(def bezier
  (mapv (BezierEasing. 0.74 -0.01 0.21 0.99)
        (range 0 1 (/ 1.0 76))))

(defn fix-zero [n]
  (if (zero? n) 1 n))

(def mesh
  (let [m (three/Points. (three/SphereGeometry. 11 64 64)
                         (three/PointsMaterial. {:vertexColors true
                                                 :size 0.7
                                                 :transparent true
                                                 :alphaTest 0.5
                                                 :blending three/AdditiveBlending}))]
    (.add scene m)
    m))

(defn render []
  (let [num-frames 76]
    (swap! current-frame #(mod (inc %) num-frames))
    (let [sphere (three/SphereGeometry. 11 64 64)
          sphere-verts (.getAttribute sphere "position")
          points (mapv #(let [sv (.fromBufferAttribute (three/Vector3.) sphere-verts %)]
                          (.addScaledVector sv sv (* (:magnitude params)
                                                     (fix-zero (* (:x-scale params) (.-x sv)))
                                                     (fix-zero (* (:y-scale params) (.-y sv)))
                                                     (fix-zero (* (:sin-x-scale params) (Math/sin (.-x sv))))
                                                     (fix-zero (* (:cos-y-scale params) (Math/cos (.-y sv))))
                                                     (nth linear @current-frame))))
                       (range (:count sphere-verts)))
          dists (mapv #(.distanceTo origin %) points)
          min-dist (apply min dists)
          max-dist (- (apply max dists) min-dist)]
      (doto (.-geometry mesh)
        (.setFromPoints points)
        (.setAttribute "color" (three/Float32BufferAttribute.
                                (mapcat #(let [c (three/Color.)]
                                           ;; mutate the color object and return the resulting RGB values
                                           (.setHSL c
                                                    (+ 0.3 (* 0.5 (max (min 1.0 (/ (- (get dists %) min-dist) max-dist)) 0)))
                                                    0.8
                                                    0.2)
                                           [(.-r c) (.-g c) (.-b c)])
                                        (range (:count sphere-verts)))
                                3))))
    (squint/assoc-in! mesh [:rotation :y] (* 1.5 Math/PI (nth bezier @current-frame))))
  (.render composer (nth bezier @current-frame))) ; render from the effects composer

(defn animate [s]
  (.begin s)
  (render)
  (.end s)
  (.requestAnimationFrame js/window #(animate s)))

(defn ^:async init []
  (let [s (Stats. [])]
    (js/document.body.appendChild (:dom s))
    (animate s)))

(init)

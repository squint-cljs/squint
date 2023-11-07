(ns gespensterfelder
  {:clj-kondo/config '{:linters {:unresolved-symbol {:exclude [js-await]}}}}
  (:require ["three-full" :as three]
            ["easing" :as easing]
            ["bezier-easing" :as BezierEasing]
            ["dat.gui" :as dat]
            ["squint-cljs/core.js" :as squint]
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

(def renderer
  (doto (three/WebGLRenderer. (clj->js {:antialias true}))
    (.setPixelRatio (.-devicePixelRatio js/window))
    (.setSize (.-innerWidth js/window) (.-innerHeight js/window))
    (-> (assoc! :physicallyCorrectLights true
                :antialias true
                :gammaInput true
                :gammaOutput true
                :toneMapping three/ReinhardToneMapping
                :toneMappingExposure (Math/pow 1.4 5.0))
        (-> :domElement (->> (.appendChild (.-body js/document)))))))

(def scene
  (three/Scene.))

(def camera
  (doto (three/PerspectiveCamera. 75 (/ (.-innerWidth js/window) (.-innerHeight js/window)) 0.1 1000)
    (-> :position ((fn [pos]
                     (.set pos 0 0 70))))
    (.lookAt (three/Vector3.))))

;; effects composer for after effects
(def composer 
  (let [w (get js/window :innerWidth)
        h (get js/window :innerHeight)]
    (doto (three/EffectComposer. renderer)
      (.addPass (three/RenderPass. scene camera))
      (.addPass (three/UnrealBloomPass. (three/Vector2. w h) ; viewport resolution
                                        0.3   ; strength
                                        0.2   ; radius
                                        0.8)) ; threshold
      (.addPass (assoc! (three/FilmPass. 0.25  ; noise intensity
                                           0.26  ; scanline intensity
                                           648   ; scanline count
                                           false); grayscale
                          :renderToScreen true)))))

;; so we can update the mesh while livecoding
(def mesh (atom nil))

(defn set-mesh [m]
  (when (not (nil? @mesh))
    (.remove scene @mesh))
  (reset! mesh m)
  (.add scene m))

;; animation helpers
(def last-tick (atom (.now js/Date)))
(def current-frame (atom 0))

(def bezier
  (mapv (BezierEasing. 0.74 -0.01 0.21 0.99)
        (range 0 1 (/ 1.0 76))))

(def linear (easing 76 "linear" {:endToEnd true}))

;; used to calculate offsets for deformed sphere
(def sphere-vertices
  (.-vertices (three/SphereGeometry. 11 64 64)))

(def origin (three/Vector3. 0 0 0))

(defn fix-zero [n]
  (if (zero? n) 1 n))

(defn render []
  (let [fps 24.0
        num-frames 76
        frame-duration (/ 1000 fps)
        time (.now js/Date)]
    (when (and @mesh (>= time (+ @last-tick frame-duration)))
      (reset! last-tick time)
      (swap! current-frame #(mod (inc %) num-frames))
      (when-let [m (first (.-children @mesh))]
        (doseq [[v sv] (map vector (.-vertices (.-geometry m)) sphere-vertices)]
          (.copy v sv)
          (.addScaledVector v sv (* (:magnitude params)
                                    (fix-zero (* (:x-scale params) (.-x sv)))
                                    (fix-zero (* (:y-scale params) (.-y sv)))
                                    (fix-zero (* (:sin-x-scale params) (Math/sin (.-x sv))))
                                    (fix-zero (* (:cos-y-scale params) (Math/cos (.-y sv))))
                                    (nth linear @current-frame))))
        (let [dists (mapv #(.distanceTo origin %) (.-vertices (.-geometry m)))
              min-dist (apply min dists)
              max-dist (- (apply max dists) min-dist)]
          (doseq [[v c] (map vector (.-vertices (.-geometry m)) (.-colors (.-geometry m)))]
            (.setHSL c
                     (+ 0.4 (* 0.5 (max (min 1.0 (/ (- (.distanceTo origin v) min-dist) max-dist)) 0)))
                     0.8
                     0.2)))
        (-> m (squint/assoc-in! [:rotation :y] (* 1.5 Math/PI (nth bezier @current-frame)))
              (squint/assoc-in! [:geometry :verticesNeedUpdate] true)
              (squint/assoc-in! [:geometry :colorsNeedUpdate] true)))))
  (.render composer (nth bezier @current-frame))) ;; render from the effects composer

(defn animate [s]
  (.begin s)
  (render)
  (.end s)
  (.requestAnimationFrame js/window #(animate s)))

;; where we store any assets loaded with e.g. load-texture
(def assets (atom {}))

(defn load-texture [file]  
  (js/Promise. (fn [resolve _reject]
                 (.load (three/TextureLoader.)
                        file
                        (fn [texture]
                          #_:clj-kondo/ignore
                          (assoc! texture
                                    :wrapS three/RepeatWrapping
                                    :wrapT three/RepeatWrapping)
                          (swap! assets assoc file texture)
                          (resolve))))))


(defn ^:async init []
  (let [s (Stats. [])]
    (js/document.body.appendChild  (:dom s))
    (js-await (load-texture "wisp.png"))
    (set-mesh
     (doto (three/Group.)
       (.add (three/Points. (let [geo (three/SphereGeometry. 11 64 64)]
                              (assoc! geo :colors (vec (repeatedly (count (.-vertices geo)) #(three/Color. 0xffffff)))))
                            (three/PointsMaterial. {
                                                    :vertexColors three/VertexColors
                                                    :size 0.7
                                                    :transparent true
                                                    :alphaTest 0.5
                                                    :map (doto (get @assets "wisp.png")
                                                           js/console.log)
                                                    :blending three/AdditiveBlending})))))
    (animate s)))

(init)

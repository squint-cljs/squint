;; based on https://matthias-research.github.io/pages/tenMinutePhysics/index.html

;; #_:clj-kondo/ignore
;; (warn-on-lazy-reusage!)

(defn element [tag id child-of prepend?]
  (or (js/document.getElementById id)
      (let [elt (js/document.createElement tag)
            parent (if child-of (js/document.querySelector child-of)
                       js/document.body)]
        (set! elt -id id)
        (if prepend?
          (.prepend parent elt)
          (.appendChild parent elt))
        elt)))

(def orange "#F26D00") ;; the one color for the whole table: outline, bumpers, flippers, lambda, score and restart
(def orange-rgb [242 109 0]) ;; the same orange, as parts, for blending
(def outline-alpha 0.5)
;; the table outline's color: the same orange at half opacity
(def outline-color
  (let [[r g b] orange-rgb]
    (str "rgba(" r "," g "," b "," outline-alpha ")")))
(def hit-rgb [252 214 184]) ;; the color right after a hit: that orange most of the way to white
(def mono "ui-monospace, SFMono-Regular, Menlo, Consolas, monospace")

;; condensed face for the bumper numbers, falling back to plain Arial and sans-serif
(def bumper-font "\"Arial Narrow\", \"Avenir Next Condensed\", \"Helvetica Neue Condensed\", Arial, sans-serif")

;; The score and the restart button share this pill geometry, type and spacing. The score is
;; the same kind of button with an invisible border, so the baselines line up.
(def pill-css
  (str "box-sizing:border-box;margin:0;padding:0.35em 1em;white-space:nowrap;"
       "font-family:" mono ";font-weight:500;letter-spacing:-0.04em;line-height:1;"
       "border-style:solid;border-radius:9999px;"))

(defn style! [elt css]
  (set! (.. elt -style -cssText) css)
  elt)

;; Squint Playground convention:
;; DOM always goes into the element with the id "app".
;; #app > #pinball > (#pinball-header > (score, restart button), #pinball-table > canvas)
(def pinball
  (style! (element "div" "pinball" "#app" false)
          "display:flex;flex-direction:column;align-items:center;gap:8px;"))

(def header-el
  (style! (element "div" "pinball-header" "#pinball" false)
          (str "display:flex;align-items:center;justify-content:space-between;gap:28px;"
               ;; if the table gets too narrow for both, the row keeps its content width and
               ;; overflows evenly on both sides, because #pinball centers its children
               "min-width:max-content;")))

(def score-el
  (style! (element "button" "pinball-score" "#pinball-header" false)
          (str pill-css "cursor:default;pointer-events:none;background:transparent;"
               "color:" orange ";border-color:transparent;")))

;; "Score:" and the number next to it, as separate spans
(def score-label
  (doto (style! (element "span" "pinball-score-label" "#pinball-score" false) "")
    (set! -innerText "Score:")))

(def score-value
  (doto (element "span" "pinball-score-value" "#pinball-score" false)
    (set! -innerText "0")))

(def table-el
  (style! (element "div" "pinball-table" "#pinball" false)
          "position:relative;line-height:0;"))

(def canvas
  (style! (element "canvas" "myCanvas" "#pinball-table" false)
          "display:block;background:transparent;"))

(def c (.getContext canvas "2d"))

(def offset 0.02)
(def flipper-height 1.7)

;; The table is 1.0 wide and flipper-height tall in sim units. The canvas is
;; sized to; exactly that, so the scale only depends on the canvas height.
;; The one stroke width for the lambda, the flipper borders and both bumper rings. It is
;; 2.9px at the default 400px table (5.75px at 800px), scaling with it, never under 1px.
(defn line-w []
  (max 1 (* 0.00719 (.-height canvas))))

;; The table's outline is the same width.
(defn table-line-w []
  (line-w))

;; One table line width is kept free at the bottom, so the stroke along y=0 isn't cut off.
(defn c-scale []
  (/ (- (.-height canvas) (table-line-w)) flipper-height))

(defn sim-width []
  (/ (.-width canvas) (c-scale)))

(defn sim-height []
  (/ (.-height canvas) (c-scale)))

(defn c-x [[pos-x _pos-y]]
  (* pos-x (c-scale)))

(defn c-y [[_pos-x pos-y]]
  (- (.-height canvas)
     (/ (table-line-w) 2)
     (* pos-y (c-scale))))

(defn vdot [[va vb] [na nb]]
  (+ (* va na)
     (* vb nb)))

(defn vlen [[vx vy]]
  (js/Math.sqrt (+ (* vx vx)
                   (* vy vy))))

(defn vscale [[vx vy] s]
  [(* vx s)
   (* vy s)])

(defn vadd [[ax ay] [bx by]]
  [(+ ax bx)
   (+ ay by)])

(defn vsub [a b]
  (vadd a (vscale b -1)))

(defn norm [v]
  (vscale v (/ 1 (vlen v))))

;; the vector 90deg perpendicular to v in counter clockwise direction
(defn vperp [[vx vy]]
  [(* -1 vy) vx])

;; The lambda is two center lines, traced from a reference glyph, drawn as strokes of
;; line-w with round ends. The long leg is a curve: it hooks over at the top left, runs
;; down to the right and curls into a small foot. The short leg is straight, from the
;; bottom left up to the long leg a little over halfway up. Each is a polyline of points
;; so the ball collides with exactly what is drawn.
(defn cubic-points [[x0 y0] [x1 y1] [x2 y2] [x3 y3] n]
  (mapv (fn [i]
          (let [t (/ i n)
                u (- 1 t)
                a (* u u u)
                b (* 3 u u t)
                d (* 3 u t t)
                e (* t t t)]
            [(+ (* a x0) (* b x1) (* d x2) (* e x3))
             (+ (* a y0) (* b y1) (* d y2) (* e y3))]))
        (range (inc n))))

;; Traced in the reference image's pixels (y down), then moved into sim units (y up),
;; centered on [0.525 1.466] and 0.194 tall.
(def lambda-shape
  (let [k (/ 0.194 282) ;; 97% of the original 0.2 height
        sim (fn [[ix iy]] [(+ 0.525 (* (- ix 245) k))
                           (- 1.466 (* (- iy 239) k))])
        long-leg (concat
                  (cubic-points [140 103] [200 88] [222 135] [236 190] 8)
                  (rest (cubic-points [236 190] [262 270] [290 330] [304 346] 8))
                  (rest (cubic-points [304 346] [318 362] [334 366] [350 365] 4)))
        short-leg [[141 380] [236 190]]]
    [(mapv sim long-leg) (mapv sim short-leg)]))

(defonce lambda-canvas (js/document.createElement "canvas"))

(defonce physics-scene (atom {:gravity [0.0 0.0]
                              :dt (/ 1.0 60.0) ;; slow down sim
                              :balls []
                              :obstacles []
                              :flippers []
                              :score 0}))

(defn flipper-tip [flipper]
  (let [{:keys [length sign pos rotation rest-angle]} flipper
        angle (+ rest-angle
                 (* sign rotation))
        tip [(* (js/Math.cos angle) length)
             (* (js/Math.sin angle) length)]
        tip (vadd pos tip)]
    tip))

(defn make-ball [_]
  (let [radius (+ 0.05 (* (rand) 0.1))
        mass (* js/Math.PI radius radius)
        pos [(* (rand) (sim-width))
             (* (rand) (sim-height))]
        vel [(+ -1.0 (* 2.0 (rand)))
             (+ -1.0 (* 2.0 (rand)))]
        restitution 1.0]
    {:radius radius
     :mass mass
     :pos pos
     :vel vel
     :restitution restitution}))

(defn draw-disc [c x y radius]
  (.beginPath c)
  (.arc c
        x
        y
        radius
        0.0
        (* 2.0 js/Math.PI))
  (.closePath c)
  (.fill c))

;; Shiny balls
(defn draw-ball [c x y radius color]
  (set! (.-fillStyle c) color)
  (draw-disc c x y radius)
  (let [shade (.createRadialGradient c
                                     x (- y (* 0.3 radius)) (* 0.15 radius)
                                     x y radius)]
    (.addColorStop shade 0 "rgba(0,0,0,0)")
    (.addColorStop shade 0.5 "rgba(0,0,0,0.15)")
    (.addColorStop shade 1 "rgba(0,0,0,0.25)") ;; the rim: the orange halfway to black
    (set! (.-fillStyle c) shade)
    (draw-disc c x y radius))
  (let [hx x
        hy (- y (* 0.4 radius))
        glow (.createRadialGradient c hx hy 0 hx hy (* 0.6 radius))]
    (.addColorStop glow 0 "rgba(255,255,255,1)")
    (.addColorStop glow 0.3 "rgba(255,255,255,0.9)")
    (.addColorStop glow 1 "rgba(255,255,255,0)")
    (set! (.-fillStyle c) glow)
    (draw-disc c x y radius)))

;; 1 right after a hit, fading to 0 over ms. Hits are recorded by key in bumper-hits.
(defn hit-fade [hit-key ms]
  (if-let [t (get @bumper-hits hit-key)]
    (max 0 (- 1 (/ (- (js/performance.now) t) ms)))
    0))

;; the orange, blended toward the hit color by k (0 to 1)
(defn hit-color [k]
  (let [[r0 g0 b0] orange-rgb
        [r1 g1 b1] hit-rgb
        lerp (fn [a b] (js/Math.round (+ a (* k (- b a)))))]
    (str "rgb(" (lerp r0 r1) "," (lerp g0 g1) "," (lerp b0 b1) ")")))

;; The points label: the same color as the rings, so the hit color blending back to orange
;; over flash-ms, while its opacity fades out over number-fade-ms.
(defn draw-points-label [c x y r color-k alpha txt]
  (let [[r0 g0 b0] orange-rgb
        [r1 g1 b1] hit-rgb
        lerp (fn [a b] (js/Math.round (+ a (* color-k (- b a)))))]
    (set! (.-fillStyle c) (str "rgba(" (lerp r0 r1) "," (lerp g0 g1) "," (lerp b0 b1) "," alpha ")"))
    (set! (.-font c) (str "600 " (max 14 (* 0.0678 (c-scale))) "px " bumper-font))
    (set! (.-textAlign c) "center")
    (set! (.-textBaseline c) "middle")
    (.fillText c txt x (+ y (* 0.04 r)))))

(defn draw []
  (let [ps @physics-scene]

    (.clearRect c 0 0 (.-width canvas) (.-height canvas))

    ;; border, with corners rounded to the flippers' radius
    (set! (.-strokeStyle c) outline-color)
    (set! (.-lineWidth c) (table-line-w))
    (let [pts (mapv (fn [p] [(c-x p) (c-y p)]) (:border ps))
          n (count pts)
          mid (fn [[ax ay] [bx by]] [(/ (+ ax bx) 2) (/ (+ ay by) 2)])
          r (* (or (:radius (first (:flippers ps))) 0.03) (c-scale))
          [sx sy] (mid (last pts) (first pts))]
      (.beginPath c)
      (.moveTo c sx sy)
      (dotimes [i n]
        (let [[px py] (nth pts i)
              [nx ny] (mid (nth pts i) (nth pts (mod (inc i) n)))]
          (.arcTo c px py nx ny r)))
      (.closePath c)
      (.stroke c))
    (set! (.-lineWidth c) 1)

    ;; The lambda has the bumpers' rhythm: orange, gap, orange, each line-w thick. It is
    ;; three layers of the same center lines with round ends and joins: 5 line-w wide in
    ;; orange, 3 line-w wide erased from that, then line-w wide in orange. That is done on
    ;; a scratch canvas, so the erasing cannot cut into anything else already drawn.
    (let [lw (line-w)
          lc lambda-canvas
          lctx (.getContext lc "2d")
          trace! (fn [width]
                   (set! (.-lineWidth lctx) width)
                   (.beginPath lctx)
                   (doseq [[first-pt & more] (:poly-obstacle ps)]
                     (.moveTo lctx (c-x first-pt) (c-y first-pt))
                     (doseq [pt more]
                       (.lineTo lctx (c-x pt) (c-y pt))))
                   (.stroke lctx))]
      (when (or (not= (.-width canvas) (.-width lc)) (not= (.-height canvas) (.-height lc)))
        (set! (.-width lc) (.-width canvas))
        (set! (.-height lc) (.-height canvas)))
      (.clearRect lctx 0 0 (.-width lc) (.-height lc))
      (set! (.-lineCap lctx) "round")
      (set! (.-lineJoin lctx) "round")
      (set! (.-strokeStyle lctx) (hit-color (hit-fade "lambda" flash-ms)))
      (trace! (* 5 lw))
      (set! (.-globalCompositeOperation lctx) "destination-out")
      (trace! (* 3 lw))
      (set! (.-globalCompositeOperation lctx) "source-over")
      (trace! lw)
      (.drawImage c lc 0 0))

    ;; balls, all the same light gray
    (doseq [ball (:balls ps)]
      (draw-ball c
                 (c-x (:pos ball))
                 (c-y (:pos ball))
                 (* (:radius ball) (c-scale))
                 orange))

    ;; A bumper is two concentric rings with nothing inside. The rings are line-w thick
    ;; with a gap of line-w between them, like the lambda's stroke. A soft glow
    ;; around the outer ring appears on a hit. A hit lights both rings (fading over flash-ms) and
    ;; shows the points (fading over number-fade-ms). A new hit resets the animation.
    (doseq [obstacle (:obstacles ps)]
      (let [x (c-x (:pos obstacle))
            y (c-y (:pos obstacle))
            r (* (:radius obstacle) (c-scale))
            lw (line-w)
            glow (max 4 (* 0.4 r))
            hit-key (str (:pos obstacle))
            number-k (hit-fade hit-key number-fade-ms)]
        ;; glow: only after a hit. A ring from the outer edge out to glow px, in the light
        ;; hit color at 45%, fading out with the hit (over flash-ms) and with distance.
        (let [k (hit-fade hit-key flash-ms)]
          (when (> k 0)
            (let [[hr hg hb] hit-rgb
                  grad (.createRadialGradient c x y r x y (+ r glow))]
              (.addColorStop grad 0 (str "rgba(" hr "," hg "," hb "," (* 0.45 k) ")"))
              (.addColorStop grad 1 (str "rgba(" hr "," hg "," hb ",0)"))
              (set! (.-fillStyle c) grad)
              (.beginPath c)
              (.arc c x y (+ r glow) 0 (* 2.0 js/Math.PI))
              (.arc c x y r 0 (* 2.0 js/Math.PI) true)
              (.fill c "evenodd"))))
        (set! (.-strokeStyle c) (hit-color (hit-fade hit-key flash-ms)))
        (set! (.-lineWidth c) lw)
        ;; outer ring: its outer edge is the bumper's radius. Inner ring: one gap further in.
        (doseq [ring-r [(- r (/ lw 2)) (- r (* 2.5 lw))]]
          (when (> ring-r 0)
            (.beginPath c)
            (.arc c x y ring-r 0 (* 2.0 js/Math.PI))
            (.stroke c)))
        (set! (.-lineWidth c) 1)
        ;; the points are left out on small tables (smaller screen sizes), where
        ;; they wouldn't be legible.
        (when (and (> number-k 0) (>= (.-height canvas) 400))
          (draw-points-label c x y r (hit-fade hit-key flash-ms) number-k
                             (str (get obstacle :points 100))))))
    (set! (.-textAlign c) "start")
    (set! (.-textBaseline c) "alphabetic")

    ;; flippers
    (set! (.-fillStyle c) "#FF0000")
    (doseq [flipper (:flippers ps)]
      (let [flipper-color "#FF0000"]
        (set! (.-fillStyle c) flipper-color)
        )

      (let [flipper-pos (:pos flipper)]
        (.translate c
                    (c-x flipper-pos)
                    (c-y flipper-pos))
        (.rotate c
                 (- (* -1.0 (:rest-angle flipper))
                    (* (:sign flipper) (:rotation flipper))))
        ;; Pointed flippers are wide at the hinge, thinner at the tip.
        ;; The fill is white in the light theme and rgb(24,24,24) in the dark theme.
        (let [rh (* 1.35 (:radius flipper) (c-scale))
              rt (* 0.7 (:radius flipper) (c-scale))
              len (* (:length flipper) (c-scale))
              phi (js/Math.acos (/ (- rh rt) len))]
          (set! (.-fillStyle c) (if (= "dark" js/document.documentElement.dataset.theme) "rgb(24,24,24)" "white"))
          (set! (.-strokeStyle c) orange)
          (set! (.-lineWidth c) (line-w))
          (.beginPath c)
          (.arc c len 0 rt (- phi) phi false)
          (.arc c 0 0 rh phi (- phi) false)
          (.closePath c)
          (.fill c)
          (.stroke c)
          (set! (.-lineWidth c) 1))
        (.resetTransform c)))

    ;; score lives above the table, outside the border
    (let [txt (str (:score ps))]
      (when (not= txt (.-textContent score-value))
        (set! (.-textContent score-value) txt)))))



(defn setup-scene-alter [ps]
  (->
   ps
   (assoc
    :balls
    (let [radius 0.03
          mass (* js/Math.PI radius radius)
          restitution 0.2
          ]
      [{:radius radius
        :mass mass
        :pos [0.92 0.5]
        :vel [-0.2 3.5]
        :restitution restitution
        :color "gray"}
       {:radius radius
        :mass mass
        :pos [0.08 0.5]
        :vel [0.2 3.5]
        :restitution restitution
        :color "black"}
       ]))
   (assoc
    :border
    [[0.74 0.25]
     [(- 1.0 offset) 0.4]
     [(- 1.0 offset) (- flipper-height offset)]
     [offset (- flipper-height offset)]
     [offset 0.4]
     [0.26 0.25]
     [0.26 0.0]
     [0.74 0.0]
     ])
   (assoc :obstacles
          [{:radius 0.1
            :pos [0.25 0.6]
            :push-vel 2.0
            :points 100}
           {:radius 0.1
            :pos [0.75 0.5]
            :push-vel 2.0
            :points 100}
           {:radius 0.12
            :pos [0.7 1.0]
            :push-vel 2.0
            :points 300}
           {:radius 0.1
            :pos [0.2 1.2]
            :push-vel 2.0
            :points 500}
           ])
   (assoc :flippers
          (let [radius 0.03
                length 0.2
                max-rotation 1.0
                rest-angle 0.5
                angular-velocity 15.0 ;; rad/s. The swing is max-rotation / this: 1.0 / 15 = about 0.067s
                restitution 0.0]
            [{:id :left
              :pos [0.26 0.22]
              :radius radius
              :length length
              :rest-angle (* -1.0 rest-angle)
              :max-rotation max-rotation
              :angular-velocity angular-velocity
              :restitution restitution
              :sign 1.0
              :rotation 0.0
              :current-angular-velocity 0.0}
             {:id :right
              :pos [0.74 0.22]
              :radius radius
              :length length
              :rest-angle (+ js/Math.PI rest-angle)
              :max-rotation max-rotation
              :angular-velocity angular-velocity
              :restitution restitution
              :sign -1.0
              :rotation 0.0
              :current-angular-velocity 0.0}]))
   (assoc :poly-obstacle lambda-shape)
   (assoc :gravity [0.0 -3.0])
   (assoc :score 0)))



(defn setup-scene []
  (swap!
   physics-scene
   setup-scene-alter))

(defn simulate-flipper [flipper dt active]
  (let [{:keys [rotation angular-velocity max-rotation sign]} flipper
        prev-rotation rotation
        rotation (if active
                   (min (+ prev-rotation
                           (* dt angular-velocity))
                        max-rotation)
                   (max (- prev-rotation
                           (* dt angular-velocity))
                        0.0))
        current-angular-velocity (/ (* sign
                                       (- rotation prev-rotation))
                                    dt)]
    (assoc flipper
           :rotation rotation
           :current-angular-velocity current-angular-velocity)))

(defn simulate-flippers [ps]
  (let [{:keys [flippers dt flippers-active]} ps]
    (mapv
     (fn [flipper]
       (let [active (get flippers-active (:id flipper))]
         (simulate-flipper flipper dt active)))
     flippers)))


(defn simulate-ball [ball dt gravity]
  (let [ball (update ball :vel (fn [vel]
                                 (vadd vel (vscale gravity dt))))
        ball (update ball :pos (fn [pos]
                                 (vadd pos (vscale (:vel ball) dt))))]
    ball))

(defn closest-point-on-segment [p a b]
  ;; p,a,b are all vectors
  ;; find point c on line ab where dist p to c is smallest
  ;; this is where line pc is 90degrees to line ab
  ;; the length of line ac is vdot ap times normal of ab
  ;; (with the rule 'project line on normal for side length')
  ;; to find point c from length ac can do a+ratio c vs ab * ab
  ;; c = a + t(b - a)
  ;; n = (b - a)/|b - a|
  ;; ac = (p - a) dot n
  ;; ab = (b - a) dot n
  ;; t = ac / ab
  ;; t = (p - a) dot (b - a)/|b-a| / (b - a) dot (b - a)/|b - a|
  ;; the lenght |b - a| is a scalar in numerator and denominator, so they cancel out
  ;; t = (p - a)dot(b - a) / (b - a)dot(b - a)
  ;; also note that point c must lie on the line between a and b, so t is clamped between 0 and 1
  (let [bmina (vsub b a)
        tden (vdot bmina bmina)
        t (if (zero? tden) ;; don't div zero
            0.0
            (let [tnum (vdot (vsub p a)
                             bmina)]
              (/ tnum
                 tden)))
        t (min 1.0 (max 0.0 t))
        c (vadd a (vscale bmina t))]
    c))


(defn handle-segments-collision [ball segments]
  ;; if incoming vel vector is d then outgoing vector r is r = d - 2(d dot n)n
  ;; where n is normal from c r is draw the velocity vector through the border,
  ;; fold it along the border. then the result is twice the side of the triangle
  ;; side (d dot n) (a number), in the direction of n (a vector)
  (let [ball-pos (:pos ball)
        ball-radius (:radius ball)
        ball-vel (:vel ball)
        ball-restitution (:restitution ball)

        closest-point+pc-length-seq
        (mapv (fn [[a b]]
                (let [c (closest-point-on-segment ball-pos a b)
                      dist (vlen (vsub ball-pos c))]
                  [c dist a b]))
              segments)]
    (if (zero? (.-length closest-point+pc-length-seq))
      ball ;; no border defined

      (let [closest (apply min-key
                           (fn [[_point dist]]
                             dist)
                           closest-point+pc-length-seq)

            [point-on-border dist a b] closest

            cp (vsub ball-pos point-on-border)
            n (vscale cp (/ 1 dist))

            [dist cp n] (if (zero? dist) ;; the center of the ball is precisely on the border
                          (let [bmina (vsub b a)
                                n (vperp bmina)
                                dist (vlen n)]
                            [0.000001 n (vscale n (/ 1 dist))])
                          [dist cp n])

            ;; the border point are ordered such that nperpab should always
            ;; point towards the playing area (from a to b the perp normal, is
            ;; 90deg counter clock wise pointing into playing field)
            abn (let [bmina (vsub b a)]
                  (vscale bmina
                          (/ 1 (vlen bmina))))
            abnperp (vperp abn)
            abnperpdotcp (vdot abnperp cp)

            ball-outside (neg? abnperpdotcp)

            r (if (or
                   ;; no matter how far beyond border turn the velocity inwards again
                   ball-outside
                   ;; only handle ball hit if actually hitting from within the field
                   (and (not ball-outside)
                        (< dist ball-radius))
                   )
                (let [n (if ball-outside
                          (vscale n -1.0)
                          n)

                      ;; without restitution fac is 2.0, 1.0 to the border plus 1.0 from the border to mirror the velocity
                      ;; we need full mirroring (1.0) and restitution in the infield side
                      fac (+ 1.0
                             (* 1.0 ball-restitution))

                      d ball-vel
                      r (vsub d
                              (vscale n (* fac (vdot d n))))
                      ]
                  r)
                ball-vel)

            ;; if ball in border due to timestep, push it out
            new-pos (if ball-outside
                      ;; ball center outside field
                      ;; this doesn't do a full reflection, but places ball back on border
                      (vadd ball-pos
                            (vscale n (* -1  (+ ball-radius dist))))

                      (if (< dist ball-radius)
                        ;; ball center inside field but partially through border
                        (vsub ball-pos
                              (vscale n (* -1 (- ball-radius dist))))

                        ball-pos))]
        (assoc ball
               :vel r
               :pos new-pos)))))

(defn handle-border-collision [ball border]
  (let [all-border-segments (partition 2         ;; n
                                       1         ;; step
                                       [(first border)] ;; pad seq
                                       border)]
    (handle-segments-collision ball all-border-segments)))

(defn orientation [[px py] [qx qy] [rx ry]]
  ;; from p to q do we need to turn clockwise or counterclockwise to get to r
  ;; math represents comparing the slope of the line p to q to the slope q to r
  (let [val (- (* (- qy py)
                  (- rx qx))
               (* (- qx px)
                  (- ry qy)))]
    (cond
      (= val 0) :colinear
      (> val 0) :clockwise
      :else :counterclockwise)))

;; based on https://stackoverflow.com/a/28390934
(defn cross= [or1 or2]
  (or (and (= or1 :clockwise)
           (= or2 :counterclockwise))
      (and (= or1 :counterclockwise)
           (= or2 :clockwise))))

(defn crossing-segment [ball [a b]]
  (or
   ;; ball overlaps with radius on line a b
   (let [ball-pos (:pos ball)
         c (closest-point-on-segment ball-pos a b)
         pos-to-c (vsub ball-pos c)
         dist (vlen pos-to-c)]
     (and (< dist (:radius ball))
          ;; is the ball coming from outside?
          (<= 0.0 (vdot pos-to-c
                        (vperp (vsub b a))))))

   ;; crossing through segment but only from outside
   (when-let [last-vel-change-pos (:last-vel-change-pos ball)]
     (let [pos (:pos ball)
           c last-vel-change-pos
           d pos]
       (and (cross= (orientation a b c)
                    (orientation a b d))
            (cross= (orientation c d a)
                    (orientation c d b)))))))

(defn vcross [[vx vy] [wx wy]]
  (- (* vx wy)
     (* vy wx)))

(defn distance-to-cross-line [[a b] [c d]]
  (let [;; find distance to segment
        ;; https://stackoverflow.com/a/565282
        p a
        pr b
        r (vsub pr p)
        q c
        qs d
        s (vsub qs q)
        u (vcross (vsub p q)
                  (vscale
                   r
                   (/ 1.0 (vcross s r))))]
    ;; from 0 to 1 where on line from c to d
    u))

(defn handle-poly-obstacle-collision [ball polylines]
  (let [segments (vec (mapcat (fn [line] (partition 2 1 line)) polylines))
        crossing-segments (vec (filter
                                (partial crossing-segment ball)
                                segments))]
    (if (seq crossing-segments)
      (let [ball-line [(:last-vel-change-pos ball) (:pos ball)]
            nearest-segment (apply min-key
                             (fn [segment]
                               (distance-to-cross-line segment ball-line))
                             crossing-segments)
            ball (assoc ball :restitution 0.6)]
        (swap! bumper-hits assoc "lambda" (js/performance.now))
        (-> (handle-segments-collision ball [nearest-segment])
            (update :points (fnil + 0) 800)))
      ball)))

;; When each bumper was last hit, by position. The draw reads it to flash the bumper.
(defonce bumper-hits (atom {}))
(def flash-ms 200) ;; a hit's color fades out over this long, and a new hit restarts it
(def number-fade-ms 1000) ;; a bumper's points fade out more slowly

(defn handle-obstacle-collision [ball obstacle]
  (let [ball-pos (:pos ball)
        ball-radius (:radius ball)
        ball-vel (:vel ball)
        obs-pos (:pos obstacle)
        obs-radius (:radius obstacle)
        obs-push-vel (:push-vel obstacle)
        dir (vsub ball-pos obs-pos)

        dist (vlen dir)]
    (if (< (+ ball-radius obs-radius) dist)
      ball ;; no collision
      (let [n (vscale dir (/ 1.0 dist))
            ;; pushout
            corr (- (+ ball-radius obs-radius)
                    dist)
            new-pos (vadd ball-pos (vscale n corr))
            ;; v is vel component in direction of n
            v (vdot ball-vel n)
            new-vel (vadd ball-vel
                          (vscale n (- obs-push-vel v)))]
        (swap! bumper-hits assoc (str obs-pos) (js/performance.now))
        (-> ball
            (assoc
             :vel new-vel
             :pos new-pos)
            (update :points (fnil + 0) (get obstacle :points 100)))))))


(defn handle-obstacles-collision [ball obstacles]
  (reduce
   handle-obstacle-collision
   ball
   obstacles))


(defn handle-ball-collision [ball-i ball-j]
  (let [restitution (min (:restitution ball-i)
                         (:restitution ball-j))
        dir (vsub
             (:pos ball-j)
             (:pos ball-i))

        dir-len (vlen dir) ;; distance between the 2 centers of ball-i and
                           ;; ball-j
        ]
    (if (or (== dir-len 0.0)
            (>= dir-len (+ (:radius ball-i) (:radius ball-j))) ;; not colliding, space between balls
            )
      [ball-i ball-j]

      ;; balls are colliding
      (let [norm-dir (vscale dir (/ 1.0 dir-len))

            corr (/ (- (+ (:radius ball-i) (:radius ball-j))
                       dir-len)
                    2.0)

            ;; undo the overlap created by doing simulation in time steps
            ball-i (update ball-i :pos vadd (vscale norm-dir (* -1 corr)))
            ball-j (update ball-j :pos vadd (vscale norm-dir corr))

            v1 (vdot (:vel ball-i) norm-dir)
            v2 (vdot (:vel ball-j) norm-dir)

            m1 (:mass ball-i)
            m2 (:mass ball-j)

            new-v1 (/ (- (+ (* m1 v1) (* m2 v2))
                         (* m2 (- v1 v2) restitution))
                      (+ m1 m2))

            new-v2 (/ (- (+ (* m1 v1) (* m2 v2))
                         (* m1 (- v2 v1) restitution))
                      (+ m1 m2))

            ball-i (update ball-i :vel vadd (vscale norm-dir (- new-v1 v1)))
            ball-j (update ball-j :vel vadd (vscale norm-dir (- new-v2 v2)))]
        [ball-i ball-j]))))


(defn handle-flipper-collision [ball flipper]
  (let [flipper-pos (:pos flipper)
        flipper-tip-pos (flipper-tip flipper)
        flipper-radius (:radius flipper)

        ball-pos (:pos ball)
        ball-radius (:radius ball)
        closest (closest-point-on-segment ball-pos flipper-pos flipper-tip-pos)

        dir (vsub ball-pos closest)
        dist (vlen dir)]
    (if (< (+ ball-radius flipper-radius) dist)
      ball
      (let [impact-norm (vscale dir (/ 1.0 dist))
            pos-corr (vscale impact-norm
                             (- (+ ball-radius flipper-radius)
                                dist))

            {:keys [current-angular-velocity]} flipper
            ball-vel (:vel ball)

            contact-pos (vadd closest
                              (vscale impact-norm
                                      flipper-radius))
            radius (vsub contact-pos
                         flipper-pos)
            surface-vel (-> (vperp radius)
                            (vscale current-angular-velocity))
            v (vdot ball-vel impact-norm)
            vnew (vdot surface-vel impact-norm)

            vchange (vscale impact-norm
                            (- vnew v))]
        (-> ball
            (update :pos vadd pos-corr)
            (update :vel vadd vchange))))))

(defn handle-flippers-collision [ball flippers]
  (reduce
   handle-flipper-collision
   ball
   flippers))

(defn simulate-balls [{:keys [dt gravity balls border obstacles flippers poly-obstacle]}]
  (reduce
   (fn [balls i]
     (let [old-i-ball-vel (get-in balls [i :vel])
           balls (update balls i simulate-ball dt gravity)
           balls (reduce
                  (fn [balls j]
                    (let [ball-i (get balls i)
                          ball-j (get balls j)
                          [ball-i ball-j] (handle-ball-collision ball-i ball-j)]
                      (-> balls
                          (assoc i ball-i)
                          (assoc j ball-j))))
                  balls
                  (range (inc i) ;; note: going from i forward can miss
                                 ;; collisions if our simulate-ball makes us now
                                 ;; hit a ball <i
                         (count balls)))

           ;; not needed, the wall is the black rectangle, now we use the orange machine border
           ;;balls (update balls i handle-wall-collision)
           balls (update balls i handle-obstacles-collision obstacles)

           balls (update balls i handle-poly-obstacle-collision poly-obstacle)

           balls (update balls i handle-flippers-collision flippers)

           balls (update balls i handle-border-collision border)

           ;; track last velocity change position, so we can track if a ball crossed a line
           new-i-ball-val (get-in balls [i :vel])
           balls (if (not= old-i-ball-vel new-i-ball-val)
                   (assoc-in balls [i :last-vel-change-pos] (get-in balls [i :pos]))
                   balls)]
       balls))
   balls
   (range (count balls))))

;; The lambda turns once every lambda-spin-seconds, around the middle of its box. The
;; shape as first set up is kept as :poly-base, and each step the turned copy goes into
;; :poly-obstacle, which both the collision and the drawing read. It is about 25 points, so cheap.
(def lambda-spin-seconds 7)
(def lambda-pivot [0.525 1.466])

(defn spin-lambda [ps dt]
  (let [base (or (:poly-base ps) (:poly-obstacle ps))
        angle (+ (or (:poly-angle ps) 0)
                 (* 2.0 js/Math.PI (/ dt lambda-spin-seconds)))
        cs (js/Math.cos angle)
        sn (js/Math.sin angle)
        [px py] lambda-pivot
        turn (fn [[x y]]
               (let [dx (- x px)
                     dy (- y py)]
                 [(+ px (- (* dx cs) (* dy sn)))
                  (+ py (* dx sn) (* dy cs))]))]
    (assoc ps
           :poly-base base
           :poly-angle angle
           :poly-obstacle (mapv (fn [line] (mapv turn line)) base))))

(defn simulate []
  (swap! physics-scene
         (fn [ps]
           (let [ps (spin-lambda (assoc ps :flippers (simulate-flippers ps)) (:dt ps))
                 balls (simulate-balls ps)
                 points (apply + 0 (keep #(:points %) balls))
                 balls (mapv
                        (fn [b] (dissoc b :points))
                        balls)]
             (-> ps
                 (assoc :balls balls)
                 (update :score + points))))))

;; The frame id is kept on window, so a new evaluation can cancel the old loop
;; instead of running a second one beside it.
(defn sim-update []
  (simulate)
  (draw)
  (set! js/window.pinballFrame (js/window.requestAnimationFrame sim-update)))


(defn on-touch-start [event]
  (.preventDefault event) ;; fix android ontouchend event touches empty
  (let [rect (.getBoundingClientRect canvas)
        mid-x (/ (.-width rect) 2.0)]
    (dotimes [i (.. event -touches -length)]
      (let [touch (aget (.-touches event) i)
            ;; clientX is from the viewport's left edge, and the canvas sits in
            ;; the middle of the frame, so measure from the canvas's own left edge
            is-left (< (- (.-clientX touch) (.-left rect))
                       mid-x)
            flipper-id (if is-left
                         :left
                         :right)
            touch-identifier (.-identifier touch)]
        (swap! physics-scene
               assoc-in [:flippers-active flipper-id] touch-identifier)))))

(defn on-touch-end [event]
  (dotimes [i (.. event -changedTouches -length)]
    (let [touch (.item (.-changedTouches event) i)
          touch-identifier (.-identifier touch)]
      (when-let [flipper-id (some (fn [[flipper-id touch-id]]
                                    (when (= touch-id touch-identifier)
                                      flipper-id))
                                  (get @physics-scene :flippers-active))]
        (swap! physics-scene
               update :flippers-active dissoc flipper-id)))))

(defn flipper-id-from-key [k]
  (cond
    (or (= k "z")
        (= k ".")
        (= k "ArrowLeft"))
    :left

    (or (= k "x")
        (= k "/")
        (= k "ArrowRight"))
    :right))

;; Pill-shaped restart button, to the right of the score above the table. Font size and
;; (invisible) border width are set in place-restart!, so they follow the table.
(def button-restart
  (doto (style! (element "button" "button-restart" "#pinball-header" false)
                (str pill-css "cursor:pointer;"
                     ;; a faint orange fill instead of an outline. The border stays, but is
                     ;; invisible, so the geometry matches the score's.
                     "background:color-mix(in srgb, " orange " 15%, transparent);"
                     "color:" orange ";border-color:transparent;"))
    (set! -innerText "Restart")))

;; Score and restart share one text size, kept between 12px and 24px.
(defn text-px []
  (min 18 (max 12 (* 0.05 (.-height canvas)))))

(defn place-restart! []
  (let [st (.-style button-restart)]
    (set! (.-fontSize st) (str (text-px) "px"))
    ;; always the same width as the table's outline
    (set! (.-borderWidth st) (str (table-line-w) "px"))
    (set! (.-borderWidth (.-style score-el)) (str (table-line-w) "px"))
    ;; pull the score left by its padding and border, so its text lines up with the table edge
    (set! (.-marginLeft (.-style score-el)) (str "calc(-1em - " (table-line-w) "px)"))
    (set! (.-fontSize (.-style score-el)) (str (text-px) "px"))))

;; Fit the table to #app. If the frame has a set height, fill it. Otherwise use a
;; default height. Sim units don't depend on pixels, so resizing keeps the game going.
(def default-height 555)

;; One fitting pass. Returns true if the canvas size changed.
(defn fit! []
  (let [app (js/document.getElementById "app")
        cs (js/getComputedStyle app)
        pad-x (+ (js/parseFloat (.-paddingLeft cs)) (js/parseFloat (.-paddingRight cs)))
        pad-y (+ (js/parseFloat (.-paddingTop cs)) (js/parseFloat (.-paddingBottom cs)))
        _ (place-restart!) ;; first, so the header row has its current height
        frame-fixed? (not= "" (.. (js/document.getElementById "dom-frame") -style -height))
        avail-w (- (.-clientWidth app) pad-x)
        ;; The score and restart row above the table takes its height plus the 8px gap.
        ;; These heights are fractional. clientHeight and offsetHeight round them, and a
        ;; canvas that is a fraction too tall makes #app show a scrollbar for nothing. So
        ;; measure exactly, and round the canvas down.
        avail-h (if frame-fixed?
                  (- (.-height (.getBoundingClientRect app))
                     pad-y
                     (.-height (.getBoundingClientRect header-el))
                     8)
                  default-height)
        h (js/Math.floor (max 100 (min avail-h (* avail-w flipper-height))))
        w (js/Math.round (/ h flipper-height))
        changed? (or (not= w (.-width canvas)) (not= h (.-height canvas)))]
    (when changed?
      (set! (.-width canvas) w)
      (set! (.-height canvas) h))
    (place-restart!)
    ;; the score and restart row spans the table's outline, edge to edge
    (set! (.. header-el -style -width)
          (str (js/Math.round (* (c-scale) (- 1.0 (* 2.0 offset)))) "px"))
    changed?))

;; The row's text size follows the canvas height, so its own height can change once the
;; canvas does. 2 additional passes to keep things from being left 1 pixel too tall.
(defn resize! []
  (loop [pass 0]
    (when (and (fit!) (< pass 2))
      (recur (inc pass)))))

(when js/window.pinballResizeObserver
  (.disconnect js/window.pinballResizeObserver))
(set! js/window.pinballResizeObserver (js/ResizeObserver. (fn [] (resize!))))
(.observe js/window.pinballResizeObserver (js/document.getElementById "app"))

;; Every listener is tied to one AbortController kept on window. A new evaluation
;; aborts it, which removes the old listeners, so they never stack up.
(defn reg-listeners []
  (when js/window.pinballListeners
    (.abort js/window.pinballListeners))
  (set! js/window.pinballListeners (js/AbortController.))
  (let [opts #js {:signal (.-signal js/window.pinballListeners)}]
    (.addEventListener canvas "touchstart"
                       on-touch-start opts)
    (.addEventListener canvas "touchend"
                       on-touch-end opts)

    (.addEventListener js/document.body "keydown"
                       (fn [event]
                         (let [k (.-key event)]
                           (when-let [flipper-id (flipper-id-from-key k)]
                             (swap! physics-scene
                                    assoc-in [:flippers-active flipper-id] k))))
                       opts)

    (.addEventListener js/document.body "keyup"
                       (fn [event]
                         (let [k (.-key event)]
                           (when-let [flipper-id (flipper-id-from-key k)]
                             (swap! physics-scene
                                    update :flippers-active dissoc flipper-id))
                           (when (= k "r")
                             (setup-scene))))
                       opts)

    (.addEventListener (js/document.getElementById "button-restart") "click"
                       (fn [event]
                         (setup-scene)
                         ;; Let go of the focus ring on clicked restart button.
                         (.blur (.-currentTarget event)))
                       opts)))

(defn start []
  (when js/window.pinballFrame
    (js/window.cancelAnimationFrame js/window.pinballFrame))
  (resize!)
  (reg-listeners)
  (setup-scene)
  (sim-update))

(start)

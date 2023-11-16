;; based on https://matthias-research.github.io/pages/tenMinutePhysics/index.html

;; #_:clj-kondo/ignore
;; (warn-on-lazy-reusage!)

(defn element [tag id child-of prepend?]
  (or (js/document.getElementById id)
      (let [elt (js/document.createElement tag)
            parent (if child-of (js/document.querySelector child-of)
                       js/document.body)]
        (set! (.-id elt) id)
        (if prepend?
          (.prepend parent elt)
          (.appendChild parent elt))
        elt)))

(defonce create-canvas
  (element "canvas" "myCanvas" "body" true))

(def canvas (js/document.getElementById "myCanvas"))

(def c (.getContext canvas "2d"))

(set! (.-width canvas) (- js/window.innerWidth 20))
(set! (.-height canvas) (- js/window.innerHeight 100))

(def offset 0.02)
(def flipper-height 1.7)

(def c-scale (/ (.-height canvas)
                flipper-height))

(def sim-width (/ (.-width canvas) c-scale))
(def sim-height (/ (.-height canvas) c-scale))

(defn c-x [[pos-x _pos-y]]
  (* pos-x c-scale))

(defn c-y [[_pos-x pos-y]]
  (- (.-height canvas)
     (* pos-y c-scale)))

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
        pos [(* (rand) sim-width)
             (* (rand) sim-height)]
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

(defn draw []
  (let [ps @physics-scene]

    (.clearRect c 0 0 (.-width canvas) (.-height canvas))

    ;; border
    (set! (.-strokeStyle c) "#009900")
    (set! (.-lineWidth c) 5)
    (.beginPath c)
    (let [[first-bp & rest-bl] (:border ps)]
      (.moveTo c (c-x first-bp) (c-y first-bp))
      (doseq [bp rest-bl]
        (.lineTo c (c-x bp) (c-y bp)))
      (.lineTo c (c-x first-bp) (c-y first-bp))
      (.stroke c))
    (set! (.-lineWidth c) 1)

    ;; poly-segments
    (set! (.-strokeStyle c) "orange")
    (set! (.-lineWidth c) 5)
    (.beginPath c)
    (let [[first-po & rest-po] (:poly-obstacle ps)]
      (.moveTo c (c-x first-po) (c-y first-po))
      (doseq [po rest-po]
        (.lineTo c (c-x po) (c-y po)))
      (.lineTo c (c-x first-po) (c-y first-po))
      (.stroke c))
    (set! (.-lineWidth c) 1)

    ;; balls
    (set! (.-fillStyle c) "#FF0000")

    (doseq [ball (:balls ps)]
      (let [orig-fill-style (.-fillStyle c)]
        (set! (.-fillStyle c) (get ball :color "#FF0000"))
        (draw-disc c
                   (c-x (:pos ball))
                   (c-y (:pos ball))
                   (* (:radius ball) c-scale))
        (set! (.-fillStyle c) orig-fill-style)))

    ;; obstacles
    (set! (.-fillStyle c) "#FF8000")
    (doseq [obstacle (:obstacles ps)]
      (draw-disc c
                 (c-x (:pos obstacle))
                 (c-y (:pos obstacle))
                 (* (:radius obstacle)
                    c-scale)))

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
        (.fillRect c
                   0.0
                   (* -1.0 (:radius flipper) c-scale)
                   (* (:length flipper) c-scale)
                   (* 2.0 (:radius flipper) c-scale))
        (draw-disc c 0 0 (* (:radius flipper) c-scale))
        (draw-disc c (* (:length flipper) c-scale) 0 (* (:radius
                                                         flipper) c-scale))
        (.resetTransform c)))

    ;; score
    (set! (.-fillStyle c) "black")
    (set! (.-font c) "24px sans-serif")
    (.fillText c
               (str "score: " (:score ps))
               10
               30)))



(defn setup-scene-alter [ps]
  (->
   ps
   (assoc
    :balls
    (let [radius 0.03
          mass (* js/Math.PI radius radius)
          restitution 0.2
          ]
      [#_{:radius radius
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
            :push-vel 2.0}
           {:radius 0.1
            :pos [0.75 0.5]
            :push-vel 2.0}
           {:radius 0.12
            :pos [0.7 1.0]
            :push-vel 2.0}
           {:radius 0.1
            :pos [0.2 1.2]
            :push-vel 2.0}
           ])
   (assoc :flippers
          (let [radius 0.03
                length 0.2
                max-rotation 1.0
                rest-angle 0.5
                angular-velocity 10.0
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
   (assoc :poly-obstacle (let [tl [0.45 1.6]
                               bl [0.55 1.4]
                               br [0.60 1.4]
                               tr [0.50 1.6]]
                           [tr
                            br
                            bl
                            [0.52 1.46]
                            [0.50 1.4]
                            [0.45 1.4]
                            [0.49 1.52]
                            tl]))
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
  (let [all-border-segments (clj->js (vec (partition 2         ;; n
                                                    1         ;; step
                                                    [(first border)] ;; pad seq
                                                    border)))]
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

(defn handle-poly-obstacle-collision [ball points]
  (let [segments (partition 2         ;; n
                            1         ;; step
                            [(first points)] ;; pad seq
                            points)
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
        (-> (handle-segments-collision ball [nearest-segment])
            (update :points (fnil + 0) 5)))
      ball)))

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
        (-> ball
            (assoc
             :vel new-vel
             :pos new-pos)
            (update :points (fnil + 0) 1))))))


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
  #_(js/console.log "border" (clj->js border))
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

           ;; not needed, the wall is the black rectangle, now we use the green machine border
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

(defn simulate []
  (swap! physics-scene
         (fn [ps]
           (let [ps (assoc ps :flippers (simulate-flippers ps))
                 balls (simulate-balls ps)
                 points (apply + 0 (keep #(:points %) balls))
                 balls (mapv
                        (fn [b] (dissoc b :points))
                        balls)]
             (-> ps
                 (assoc :balls balls)
                 (update :score + points))))))

(defn sim-update []
  (simulate)
  (draw)
  (js/window.requestAnimationFrame sim-update))


(defn on-touch-start [event]
  (.preventDefault event) ;; fix android ontouchend event touches empty
  (let [rect (.getBoundingClientRect canvas)
        mid-x (/ (.-width rect) 2.0)]
    (dotimes [i (.. event -touches -length)]
      (let [touch (aget (.-touches event) i)
            is-left (< (.-clientX touch)
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

(defonce button-restart (doto (element "button" "button-restart" "body" true)
                          (aset "innerText" "restart")))

(defn reg-listeners []
  (.addEventListener canvas "touchstart"
                     on-touch-start false)
  (.addEventListener canvas "touchend"
                     on-touch-end false)

  (.addEventListener js/document.body "keydown"
                     (fn [event]
                       (let [k (.-key event)]
                         (when-let [flipper-id (flipper-id-from-key k)]
                           (swap! physics-scene
                                  assoc-in [:flippers-active flipper-id] k)))))

  (.addEventListener js/document.body "keyup"
                     (fn [event]
                       (let [k (.-key event)]
                         (when-let [flipper-id (flipper-id-from-key k)]
                           (swap! physics-scene
                                  update :flippers-active dissoc flipper-id))
                         (when (= k "r")
                           (setup-scene)))))

  (.addEventListener (js/document.getElementById "button-restart") "click"
                     (fn []
                       (setup-scene))))

(defn start []
  (reg-listeners)
  (setup-scene)
  (sim-update))

(start)


(def css
  (or (js/document.querySelector "#css")
    (doto (js/document.createElement :div)
      (set! -id :css)
      (set! -innerHTML #html
               [:link {:href "https://cdn.jsdelivr.net/npm/quill@2.0.0/dist/quill.snow.css"
                       :rel "stylesheet"}])
      (js/document.head.prepend))))

;; Snow hardcodes dark gray (#444) icons and light gray borders, so it is unreadable on a dark
;; page. These overrides use currentColor and translucent gray instead, so the toolbar follows
;; the page's text color in both themes, with no theme check.
(js/document.head.append
  (doto (js/document.createElement :style)
    (set! -textContent "
.ql-snow .ql-stroke { stroke: currentColor; }
.ql-snow .ql-fill, .ql-snow .ql-stroke.ql-fill { fill: currentColor; }
.ql-snow .ql-picker, .ql-snow .ql-picker-options, .ql-snow.ql-toolbar button { color: inherit; }
.ql-snow .ql-picker-options { background: var(--bg, canvas); }
.ql-snow.ql-toolbar button:hover .ql-stroke,
.ql-snow.ql-toolbar button.ql-active .ql-stroke,
.ql-snow .ql-picker-label:hover .ql-stroke,
.ql-snow .ql-picker-item:hover .ql-stroke { stroke: currentColor; opacity: 0.7; }
.ql-snow.ql-toolbar button:hover, .ql-snow .ql-picker-label:hover,
.ql-snow.ql-toolbar button.ql-active, .ql-snow .ql-picker-item:hover { color: inherit; opacity: 0.7; }
.ql-toolbar.ql-snow, .ql-container.ql-snow,
.ql-snow .ql-picker-options { border-color: rgb(128 128 128 / 40%); }
.ql-snow .ql-tooltip { background: var(--bg, canvas); color: inherit; border-color: rgb(128 128 128 / 40%); box-shadow: none; }
.ql-snow .ql-tooltip input[type=text] { color: inherit; }
.ql-editor.ql-blank::before { color: rgb(128 128 128 / 70%); }
")))

;; Squint Playground convention:
;; DOM always goes into the element with the id "app".
(def app (or (js/document.querySelector "#app")
           (doto (js/document.createElement :div)
             (set! -id :app)
             (js/document.body.prepend))))

(require '["https://esm.sh/quill$default" :as Quill])
(Quill. "#app" {:theme "snow"})

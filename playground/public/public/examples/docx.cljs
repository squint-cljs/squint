(ns my-docx
  (:require ["https://esm.sh/docx" :as docx
             :refer [Document Paragraph TextRun
                     Packer]]))

;; Squint Playground convention:
;; DOM always goes into the element with the id "app".
(def app (or (js/document.getElementById "app")
             (doto (js/document.createElement "div")
               (set! -id "app")
               (js/document.body.prepend))))

(def doc
  (Document.
    {:sections
     [{:properties {}
       :children
       [(Paragraph.
          {:children
           [(TextRun. "Hello World")
            (TextRun.
              {:text "Foo Bar"
               :bold true})
            (TextRun.
              {:text "\tGithub is the best"
               :bold true})]})]}]}))

(defn ^:async download []
  (let [bytes (js-await (Packer.toBuffer doc))
        blob (js/Blob. [bytes] {:type "application/docx"})
        link (.createElement js/document "a")]
    (set! (.-href link) (js/URL.createObjectURL blob))
    (set! (.-download link) "My word doc.docx")
    (set! (.-innerText link) "Download your word document!")
    (.replaceChildren app link)))

(download)

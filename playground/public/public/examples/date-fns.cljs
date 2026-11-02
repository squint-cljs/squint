(require '["https:/esm.sh/date-fns@3.0.0/index.mjs" :as d])

(d/formatRelative (d/subDays (js/Date.) 2) (js/Date.))

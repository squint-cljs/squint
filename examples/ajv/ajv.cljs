(ns ajv
  (:require ["ajv$default" :as Ajv]
            ["ajv-formats$default" :as addFormats]))

;; Initialize AJV with formats
(def ajv (doto (Ajv. {:allErrors true :strict false})
           (addFormats)))

;; Define the user schema
(def user-schema {:type "object"
                  :properties {:id {:type "integer"}
                               :name {:type "string"}
                               :email {:type "string" :format "email"}
                               :phone {:type "string"}
                               :website {:type "string"}}
                  :required ["id" "name" "email"]
                  :additionalProperties false})

;; Compile the schema
(def validate (.compile ajv user-schema))

;; Example of valid user data
(def valid-user {:id 1
                 :name "Leanne Graham"
                 :email "Sincere@april.biz"
                 :phone "1-770-736-8031 x56442"
                 :website "hildegard.org"})

;; Example of invalid user data (missing 'email')
(def invalid-user {:id 2
                   :name "Ervin Howell"
                   :phone "010-692-6593 x09125"
                   :website "anastasia.net"})

;; Validate valid user
(def is-valid (validate valid-user))
(js/console.log "Valid user:" (if is-valid "✅ Valid" "❌ Invalid") (.-errors validate))

;; Validate invalid user
(def is-invalid (validate invalid-user))
(js/console.log "Invalid user:" (if is-invalid "✅ Valid" "❌ Invalid") (.-errors validate))

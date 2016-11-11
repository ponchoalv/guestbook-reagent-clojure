(ns guestbook.core
  (:require [reagent.core :as reagent :refer [atom]]
            [ajax.core :refer [GET]]
            [guestbook.ws :as ws]))

(enable-console-print!)

(defn get-messages [messages]
  (GET "/messages"
       {:headers {"Accept" "application/transit+json"}
        :handler #(reset! messages (vec %))}))

(defmulti -ws-handler (fn [{:keys [?data]} _ _ _] 
                        (first ?data)))

(defmethod -ws-handler :default [{:keys [?data]} _ _ _]
  (println "Handler no utilizado por el cliente: " ?data))

(defmethod -ws-handler :guestbook/error [{:keys [?data]} _ _ errors]
  (let [message (second ?data)
        response-errors (:errors message)]
    (reset! errors response-errors)))

(defmethod -ws-handler :guestbook/add-message [{:keys [?data]} messages fields errors]
  (let [message (second ?data)]
    (do
      (reset! errors nil)
      (if (= (:uid message) (:uid @ws/state))
        (reset! fields nil))
      (swap! messages conj message))))

(defn response-handler [messages fields errors]
  (fn [ev-msg]
    (-ws-handler ev-msg messages fields errors)))

(defn errors-component [errors id]
  (when-let [error (id @errors)]
    [:div.alert.alert-danger (clojure.string/join error)]))

(defn message-list [messages]
  [:ul.messages
   (for [{:keys [timestamp message name]} @messages]
     ^{:key timestamp}
     [:li
      [:time (.toLocaleString timestamp)]
      [:p message]
      [:p " - " name]])])

(defn message-form [fields errors]
  [:div.content
   [:div.form-group 
    [errors-component errors :name]
    [:p "Nombre:"
     [:input.form-control
      {:type :text 
       :on-change #(swap! fields assoc :name (-> % .-target .-value))
       :value (:name @fields)}]]]
   [errors-component errors :message] 
   [:p "Mensaje:"
    [:textarea.form-control
     {:rows 4
      :cols 50 
      :on-change #(swap! fields assoc :message (-> % .-target .-value))
      :value (:message @fields)}]]
   [:input.btn.btn-primary
    {:type :submit
     :on-click #(ws/send-message! [:guestbook/add-message @fields] 8000)
     :value "comment"}]])

(defn home []
  (let [messages (atom nil)
        errors (atom nil)
        fields (atom nil)]
    (ws/start-router! (response-handler messages fields errors))
    (get-messages messages)
    (fn []
      [:div
       [:div.row
        [:div.span12
         [message-list messages]]]
       [:div.row
        [:div.span12
         [message-form fields errors]]]])))

(reagent/render
 [home]
 (.getElementById js/document "content"))

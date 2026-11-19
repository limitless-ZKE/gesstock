import React from "react";
import ReactDOM from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import App from "./App";
import { AuthProvider } from "./auth/AuthContext";
import { EntrepriseProvider } from "./entreprise/EntrepriseContext";
import { PanierProvider } from "./panier/PanierContext";
// Police embarquée dans le programme (aucun appel à Internet : le serveur du client peut être hors ligne).
import "@fontsource-variable/atkinson-hyperlegible-next/wght.css";
import "./index.css";

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <BrowserRouter>
      <AuthProvider>
        <EntrepriseProvider>
          <PanierProvider>
            <App />
          </PanierProvider>
        </EntrepriseProvider>
      </AuthProvider>
    </BrowserRouter>
  </React.StrictMode>,
);

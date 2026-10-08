import React from "react";
import ReactDOM from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import App from "./App";
import { AuthProvider } from "./auth/AuthContext";
import { EntrepriseProvider } from "./entreprise/EntrepriseContext";
import "./index.css";

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <BrowserRouter>
      <AuthProvider>
        <EntrepriseProvider>
          <App />
        </EntrepriseProvider>
      </AuthProvider>
    </BrowserRouter>
  </React.StrictMode>,
);

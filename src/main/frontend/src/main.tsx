import React from "react";
import ReactDOM from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import { AuthProvider } from "./auth/AuthContext";
import { ThemeProvider } from "./theme/ThemeContext";
import App from "./App";
import { ViewportDebugBadge } from "./components/ViewportDebugBadge";
import "./index.css";

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <ThemeProvider>
      <BrowserRouter>
        <AuthProvider>
          {/* TEMPORARY (round 6 device diagnostic) — remove once the real-device
              header/tabbar mismatch is diagnosed. */}
          <ViewportDebugBadge />
          <App />
        </AuthProvider>
      </BrowserRouter>
    </ThemeProvider>
  </React.StrictMode>,
);

import { createContext, useContext, useEffect, useState } from "react";

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => {
    const savedUser = localStorage.getItem("user");

    return savedUser
      ? JSON.parse(savedUser)
      : null;
  });

  const login = (userData, token) => {
    sessionStorage.removeItem("session-message");
    localStorage.setItem(
      "user",
      JSON.stringify(userData)
    );

    localStorage.setItem(
      "token",
      token
    );

    setUser(userData);
  };

  const logout = () => {
    localStorage.removeItem("user");
    localStorage.removeItem("token");

    setUser(null);
  };

  useEffect(() => {
    const expire = () => { sessionStorage.setItem("session-message", "Your session has expired. Please log in again."); localStorage.removeItem("user"); localStorage.removeItem("token"); setUser(null); };
    const requireChange = () => setUser(previous => {
      if (!previous) return previous;
      const updated = { ...previous, mustChangePassword: true };
      localStorage.setItem("user", JSON.stringify(updated));
      return updated;
    });
    window.addEventListener("publicfund:session-expired", expire);
    window.addEventListener("publicfund:password-change-required", requireChange);
    return () => {
      window.removeEventListener("publicfund:session-expired", expire);
      window.removeEventListener("publicfund:password-change-required", requireChange);
    };
  }, []);


  useEffect(() => {
    if (!user) return;
    try {
      const token=localStorage.getItem("token");
      const payload=JSON.parse(atob(token.split(".")[1].replaceAll("-","+").replaceAll("_","/")));
      if (!payload.exp) return;
      const timer=setTimeout(()=>window.dispatchEvent(new Event("publicfund:session-expired")),Math.max(0,payload.exp*1000-Date.now()));
      return ()=>clearTimeout(timer);
    } catch { /* Backend still validates every token; malformed local state cannot grant access. */ }
  }, [user]);

  const isAuthenticated = Boolean(
    user && localStorage.getItem("token")
  );

  return (
    <AuthContext.Provider
      value={{
        user,
        login,
        logout,
        isAuthenticated,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  return useContext(AuthContext);
}

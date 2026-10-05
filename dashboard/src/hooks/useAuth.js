import { useEffect, useState } from "react";
import { signOutOperator, subscribeToAuth } from "../integrations/firebaseClient";

// Tracks the signed-in operator. `ready` stays false until Firebase has
// restored any existing session, so the console can avoid flashing the login
// screen on every reload.
export function useAuth() {
  const [state, setState] = useState({ ready: false, user: null, error: null });

  useEffect(() => {
    let cancelled = false;
    const unsubscribe = subscribeToAuth((next) => {
      if (!cancelled) setState(next);
    });
    return () => {
      cancelled = true;
      unsubscribe();
    };
  }, []);

  return {
    ready: state.ready,
    user: state.user,
    error: state.error,
    operator: state.user?.email || null,
    signOut: signOutOperator,
  };
}

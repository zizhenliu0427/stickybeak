import { createSlice } from '@reduxjs/toolkit';
import type { PayloadAction } from '@reduxjs/toolkit';

export type UserRole = 'customer' | 'admin' | 'sysadmin';

export interface AuthUser {
  id: number;
  email: string;
  nickname: string;
  roles: UserRole[];
}

interface AuthState {
  user: AuthUser | null;
  /** true once the initial "who am I" probe has completed */
  initialized: boolean;
}

const initialState: AuthState = {
  user: null,
  initialized: false,
};

const authSlice = createSlice({
  name: 'auth',
  initialState,
  reducers: {
    setUser(state, action: PayloadAction<AuthUser | null>) {
      state.user = action.payload;
      state.initialized = true;
    },
    clearUser(state) {
      state.user = null;
    },
  },
});

export const { setUser, clearUser } = authSlice.actions;
export default authSlice.reducer;

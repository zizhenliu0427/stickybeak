import { createAsyncThunk, createSlice } from '@reduxjs/toolkit';
import type { PayloadAction } from '@reduxjs/toolkit';
import { del, errorMessage, get, patch, post } from '../../lib/api';

export type UserRole = 'customer' | 'admin' | 'sysadmin';

export interface AuthUser {
  id: number;
  email: string;
  nickname: string;
  phone: string | null;
  avatarUrl: string | null;
  roles: UserRole[];
}

/** mirrors backend AddressVO */
export interface Address {
  id: number;
  receiver: string;
  phone: string;
  country: string;
  state: string;
  city: string;
  postcode: string;
  detail: string;
  isDefault: boolean;
}

export interface AddressPayload {
  receiver: string;
  phone: string;
  country?: string;
  state: string;
  city: string;
  postcode: string;
  detail: string;
  isDefault?: boolean;
}

interface AuthState {
  user: AuthUser | null;
  /** true once the initial "who am I" probe has completed */
  initialized: boolean;
  /** last login/register failure message for form display */
  error: string | null;
}

const initialState: AuthState = {
  user: null,
  initialized: false,
  error: null,
};

interface AuthResponse {
  user: AuthUser;
  accessExpiresIn: number;
}

/** App start probe: who am I? 401 (anonymous) resolves to null, never throws. */
export const fetchMe = createAsyncThunk('auth/fetchMe', async () => {
  try {
    return await get<AuthUser>('/users/me');
  } catch {
    return null;
  }
});

export const login = createAsyncThunk<AuthUser, { email: string; password: string }>(
  'auth/login',
  async (body, { rejectWithValue }) => {
    try {
      const resp = await post<AuthResponse>('/auth/login', body);
      return resp.user;
    } catch (e) {
      return rejectWithValue(errorMessage(e));
    }
  },
);

export const register = createAsyncThunk<
  AuthUser,
  { email: string; password: string; nickname?: string }
>('auth/register', async (body, { rejectWithValue }) => {
  try {
    const resp = await post<AuthResponse>('/auth/register', body);
    return resp.user;
  } catch (e) {
    return rejectWithValue(errorMessage(e));
  }
});

export const logout = createAsyncThunk('auth/logout', async () => {
  try {
    await post('/auth/logout');
  } catch {
    // even if the call fails, clear local state
  }
});

export const updateProfile = createAsyncThunk<
  AuthUser,
  { nickname?: string; phone?: string; avatarUrl?: string }
>('auth/updateProfile', async (body, { rejectWithValue }) => {
  try {
    return await patch<AuthUser>('/users/me', body);
  } catch (e) {
    return rejectWithValue(errorMessage(e));
  }
});

/* ---- addresses (Issue 1.5) ---- */

export const fetchAddresses = createAsyncThunk<Address[]>('auth/fetchAddresses', () =>
  get<Address[]>('/users/me/addresses'),
);

export const createAddress = createAsyncThunk<Address, AddressPayload>(
  'auth/createAddress',
  async (body, { rejectWithValue }) => {
    try {
      return await post<Address>('/users/me/addresses', body);
    } catch (e) {
      return rejectWithValue(errorMessage(e));
    }
  },
);

export const updateAddress = createAsyncThunk<Address, { id: number; body: AddressPayload }>(
  'auth/updateAddress',
  async ({ id, body }, { rejectWithValue }) => {
    try {
      return await patch<Address>(`/users/me/addresses/${id}`, body);
    } catch (e) {
      return rejectWithValue(errorMessage(e));
    }
  },
);

export const deleteAddress = createAsyncThunk<number, number>(
  'auth/deleteAddress',
  async (id, { rejectWithValue }) => {
    try {
      await del(`/users/me/addresses/${id}`);
      return id;
    } catch (e) {
      return rejectWithValue(errorMessage(e));
    }
  },
);

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
    clearError(state) {
      state.error = null;
    },
  },
  extraReducers: (builder) => {
    builder
      .addCase(fetchMe.fulfilled, (state, action) => {
        state.user = action.payload;
        state.initialized = true;
      })
      .addCase(login.fulfilled, (state, action) => {
        state.user = action.payload;
        state.error = null;
      })
      .addCase(login.rejected, (state, action) => {
        state.error = (action.payload as string) ?? 'login failed';
      })
      .addCase(register.fulfilled, (state, action) => {
        state.user = action.payload;
        state.error = null;
      })
      .addCase(register.rejected, (state, action) => {
        state.error = (action.payload as string) ?? 'register failed';
      })
      .addCase(logout.fulfilled, (state) => {
        state.user = null;
      })
      .addCase(updateProfile.fulfilled, (state, action) => {
        state.user = action.payload;
      });
  },
});

export const { setUser, clearUser, clearError } = authSlice.actions;
export default authSlice.reducer;

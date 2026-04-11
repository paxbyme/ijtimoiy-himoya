import api from "./api";
import type { User, Department } from "@/types";

export interface Paged<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export const adminApi = {
  managers: {
    list: (page = 0, size = 20): Promise<Paged<User>> =>
      api
        .get(`/admin/managers?page=${page}&size=${size}`)
        .then((r) => r.data.data as Paged<User>),

    create: (payload: {
      displayName: string;
      phone: string;
      password: string;
      departmentId?: string;
    }): Promise<User> =>
      api.post("/admin/managers", payload).then((r) => r.data.data as User),

    get: (id: string): Promise<User> =>
      api.get(`/admin/managers/${id}`).then((r) => r.data.data as User),

    update: (
      id: string,
      payload: { displayName?: string; isActive?: boolean }
    ): Promise<User> =>
      api
        .put(`/admin/managers/${id}`, payload)
        .then((r) => r.data.data as User),

    remove: (id: string): Promise<void> =>
      api.delete(`/admin/managers/${id}`).then(() => undefined),
  },

  departments: {
    list: (page = 0, size = 50): Promise<Paged<Department>> =>
      api
        .get(`/admin/departments?page=${page}&size=${size}`)
        .then((r) => r.data.data as Paged<Department>),

    create: (payload: { name: string; managerId?: string }): Promise<Department> =>
      api
        .post("/admin/departments", payload)
        .then((r) => r.data.data as Department),

    update: (
      id: string,
      payload: { name?: string; managerId?: string }
    ): Promise<Department> =>
      api
        .put(`/admin/departments/${id}`, payload)
        .then((r) => r.data.data as Department),

    remove: (id: string): Promise<void> =>
      api.delete(`/admin/departments/${id}`).then(() => undefined),
  },
};

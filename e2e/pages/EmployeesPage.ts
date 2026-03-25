import { Page, expect } from '@playwright/test';

export class EmployeesPage {
  constructor(private page: Page) {}

  async goto() {
    await this.page.goto('/dashboard/employees');
    await this.page.waitForLoadState('networkidle');
  }

  /** Creates a staff member and returns the generated display name used. */
  async createStaff(opts: {
    phone: string;
    displayName: string;
    password: string;
  }): Promise<string> {
    await this.page.getByRole('button', { name: /add staff|new staff|create/i }).click();

    const dialog = this.page.getByRole('dialog');
    await dialog.getByLabel(/phone/i).fill(opts.phone);
    await dialog.getByLabel(/name/i).fill(opts.displayName);
    await dialog.getByLabel(/password/i).fill(opts.password);
    await dialog.getByRole('button', { name: /create|save|submit/i }).click();

    // Toast confirmation
    await expect(this.page.getByText(/staff created|created successfully/i))
        .toBeVisible({ timeout: 10_000 });

    return opts.displayName;
  }

  async staffExists(displayName: string) {
    await expect(this.page.getByText(displayName)).toBeVisible();
  }

  async openStaffDetail(displayName: string) {
    await this.page.getByText(displayName).click();
    await this.page.waitForLoadState('networkidle');
  }
}

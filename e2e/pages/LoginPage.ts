import { Page, expect } from '@playwright/test';

export class LoginPage {
  constructor(private page: Page) {}

  async goto() {
    await this.page.goto('/login');
  }

  async loginAsManager(phone: string, password: string) {
    await this.page.getByLabel(/phone/i).fill(phone);
    await this.page.getByLabel(/password/i).fill(password);
    await this.page.getByRole('button', { name: /sign in|login/i }).click();
    // Wait for redirect to dashboard
    await expect(this.page).toHaveURL(/\/dashboard/, { timeout: 15_000 });
  }

  async isOnLoginPage() {
    await expect(this.page).toHaveURL(/\/login/);
  }
}

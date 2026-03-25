import { Page, expect } from '@playwright/test';

export class KpiPage {
  constructor(private page: Page) {}

  async goto() {
    await this.page.goto('/dashboard/kpi');
    await this.page.waitForLoadState('networkidle');
  }

  /** Assert that a staff member appears somewhere in the rankings table. */
  async staffAppearsInRankings(displayName: string) {
    await expect(this.page.getByText(displayName)).toBeVisible({ timeout: 10_000 });
  }

  /** Assert a score > 0 is shown for a given staff member row. */
  async staffHasNonZeroScore(displayName: string) {
    const row = this.page.locator('tr, [data-testid="kpi-row"]').filter({ hasText: displayName });
    await expect(row).toBeVisible();
    // The row should contain a number that isn't 0
    await expect(row.getByText(/^[1-9]/)).toBeVisible();
  }

  async selectPeriod(period: string) {
    await this.page.getByLabel(/period/i).click();
    await this.page.getByRole('option', { name: period }).click();
    await this.page.waitForLoadState('networkidle');
  }
}

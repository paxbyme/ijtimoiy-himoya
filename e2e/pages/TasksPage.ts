import { Page, expect } from '@playwright/test';

export class TasksPage {
  constructor(private page: Page) {}

  async goto() {
    await this.page.goto('/dashboard/tasks');
    await this.page.waitForLoadState('networkidle');
  }

  /** Creates a task and returns the title used. */
  async createTask(opts: {
    title: string;
    description?: string;
    assigneeName: string;
    priority?: 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT';
    deadlineDaysFromNow?: number;
  }): Promise<string> {
    await this.page.getByRole('button', { name: /add task|new task|create task/i }).click();

    const dialog = this.page.getByRole('dialog');
    await dialog.getByLabel(/title/i).fill(opts.title);

    if (opts.description) {
      await dialog.getByLabel(/description/i).fill(opts.description);
    }

    // Select assignee
    const assigneeSelect = dialog.getByLabel(/assign|assignee/i);
    await assigneeSelect.click();
    await this.page.getByRole('option', { name: opts.assigneeName }).click();

    if (opts.priority) {
      const prioritySelect = dialog.getByLabel(/priority/i);
      await prioritySelect.click();
      await this.page.getByRole('option', { name: opts.priority }).click();
    }

    if (opts.deadlineDaysFromNow !== undefined) {
      const deadline = new Date();
      deadline.setDate(deadline.getDate() + opts.deadlineDaysFromNow);
      const formatted = deadline.toISOString().split('T')[0]; // yyyy-mm-dd
      await dialog.getByLabel(/deadline/i).fill(formatted);
    }

    await dialog.getByRole('button', { name: /create|save|submit/i }).click();
    await expect(this.page.getByText(/task created|created successfully/i))
        .toBeVisible({ timeout: 10_000 });

    return opts.title;
  }

  async taskExists(title: string) {
    await expect(this.page.getByText(title)).toBeVisible();
  }
}

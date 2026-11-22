import { expect, test } from '@playwright/test';

// Пользовательские сценарии (ТЗ 13). Запуск при поднятой системе: BASE_URL=https://localhost:8443 npm run e2e
test.use({ ignoreHTTPSErrors: true, baseURL: process.env.BASE_URL ?? 'https://localhost:8443' });

test('гость видит витрину и форму входа', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Витрина туров' })).toBeVisible();
  await page.getByRole('link', { name: 'Войти' }).click();
  await expect(page.getByRole('button', { name: 'Войти' })).toBeVisible();
});

test('неверный пароль показывает понятную ошибку', async ({ page }) => {
  await page.goto('/login');
  await page.getByLabel('Email').fill('client@tourflow.local');
  await page.getByLabel('Пароль').fill('wrong-password-1');
  await page.getByRole('button', { name: 'Войти' }).click();
  await expect(page.getByRole('alert')).toContainText('Неверный email или пароль');
});

test('менеджер: демо-тур с конфликтами показывает ошибки валидации', async ({ page }) => {
  await page.goto('/login');
  await page.getByLabel('Email').fill('manager@tourflow.local');
  await page.getByLabel('Пароль').fill('Manager123!');
  await page.getByRole('button', { name: 'Войти' }).click();
  await page.getByRole('link', { name: 'Конструктор' }).click();
  await page.getByRole('button', { name: 'Создать демо-туры' }).click();
  await page.getByRole('link', { name: /с конфликтами/ }).first().click();
  await expect(page.getByText('Конфликты')).toBeVisible();
  await expect(page.locator('.conflict.ERROR').first()).toBeVisible();
});

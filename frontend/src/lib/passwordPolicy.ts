// Client-side mirror of the backend PasswordPolicy
// (com.webappfoundation.security.PasswordPolicy): min 10 chars incl. a letter and a
// digit, and not a well-known common password. The backend stays authoritative — this
// only gives the user an inline hint before submit so the form never promises something
// the server will reject with a 400.

export const PASSWORD_MIN_LENGTH = 10;

/** At least 10 chars, including at least one letter and one digit. */
export const STRONG_PASSWORD = /^(?=.*[A-Za-z])(?=.*\d).{10,}$/;

// Keep in sync with PasswordPolicy.COMMON (compared case-insensitively).
const COMMON = new Set([
  'password', 'password1', 'password123', 'passw0rd', '1234567890', '12345678',
  '123456789', 'qwerty123', 'qwertyuiop', 'admin123', 'changeme123', 'letmein123',
  'welcome123', 'iloveyou1', 'abc123456', 'football1',
]);

export function isCommonPassword(password: string): boolean {
  return COMMON.has(password.toLowerCase());
}

/** Returns a user-facing error message if the password violates policy, else null. */
export function validatePassword(password: string): string | null {
  if (!STRONG_PASSWORD.test(password)) {
    return `Password must be at least ${PASSWORD_MIN_LENGTH} characters and include a letter and a digit.`;
  }
  if (isCommonPassword(password)) {
    return 'Password is too common; choose a less guessable password.';
  }
  return null;
}

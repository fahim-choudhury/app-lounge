package app.lounge.users

/**
 * Handle user login session in the file.
 * This should be the only file which communicate with application regarding user data
 *
 * 1. Application facing api will be part of this file.
 * 2. Manage user session for Anonymous, Google, PWA users.
 * 3. In case of invalid user data, timeout etc. inform application about session failure.
 * 4. If possible add session api testcases.
 */
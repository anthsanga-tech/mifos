/**
 * Searchable activities — same idea as web-app/src/app/home/activities.ts,
 * limited to routes that exist in the training app.
 */
const activities: { activity: string; path: string }[] = [
  { activity: 'home', path: '/home' },
  { activity: 'users', path: '/users' },
  { activity: 'create user', path: '/users/create' },
  { activity: 'clients', path: '/clients' },
  { activity: 'create client', path: '/clients/create' },
  { activity: 'savings accounts', path: '/savings-accounts' },
  { activity: 'savings products', path: '/savings-products' },
  { activity: 'create savings account', path: '/savings-accounts/create' },
  { activity: 'create savings product', path: '/savings-products/create' }
];

export { activities };

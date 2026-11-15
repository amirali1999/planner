// Run date tests in a non-UTC zone (UTC+03:30) so any UTC/local mix-up shows up as an off-by-one.
process.env.TZ = 'Asia/Tehran';

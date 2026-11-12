# Exercise pack

Builds the app's built-in exercise list and its pictures. `selection.json` is the
list to edit: each entry names an exercise from
[free-exercise-db](https://github.com/yuhonas/free-exercise-db) (`source`) and how
the app shows it.

Run `npm install` once, then `node build-pack.mjs`. It needs the network to
download photos the first time.

The two outputs, `app/src/main/assets/exercises.json` and
`app/src/main/assets/exercises/*.webp`, are committed. The app build never runs
this script. After changing the list, raise `Seeder.SEED_VERSION` so installed
apps pick up the new list.

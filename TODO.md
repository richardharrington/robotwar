-- change the robots associative structure from an array to a map, with keys like :0, :1, :2 etc., so that they can be removed during the battle without messing things up.

-- change the robots so they have access to their own id keys, the same way the registers do, so that the robots can also have a write method which will know how to access itself in the world (and will return a world), and so that other functions won't have to provide id keys when they need to reference the robot and also alter the world -- they can just provide the robot and the world.

-- pull a lot of that preliminary stuff out of brain-test and use it as the basis for robot and world. get robot and world going.

-- (possibly optional, but it keeps being annoying the more I put it off:) change strings to keywords early on, and change all the map indexing to use the key as the function

-- start writing other read/write methods on the registers, starting with random and index/data

-- fix tests for random, and index/data

-- write the logic for world-ticking

-- write the logic for shells

-- write the logic for collisions

-- physics and trigonometry

-- various other logic (radar, damage, etc.)

-- add ability to read robot src-code from files

-- graphics

-- UI

-- ClojureScript in the browser

-- tournament systems with user registration, stored robots and scoring, stored on a database

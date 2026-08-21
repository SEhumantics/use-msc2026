-- Enumerates every distinct civil-status combination across exactly 2
-- Persons, using the dedicated [scrolling] section (the default [small]
-- section is too large to fully enumerate -- confirmed by actually running
-- it: still unfinished after 60s. See CivilStatus.properties.).
mv -scrollingAll CivilStatus.properties scrolling

quit

-- Single-step scrolling: `mv -scrolling` returns exactly one solution at a
-- time (as opposed to scrollingAll.cmd, which enumerates every solution up
-- front). The first call supplies the properties file and finds the first
-- solution; subsequent calls step through the search space using the
-- follow-up arguments `next`, `previous`, and `show(n)` (see doc/Usage.pdf,
-- section "modelvalidator -scrolling", and the "mv -scrolling" alias in
-- useplugin.xml).
--
-- Each step below is followed by a plain USE OCL query (`?`, not `mv ?`)
-- against the actually reconstructed object diagram, to show the solution
-- really changes underneath -- not just that the log says so.
--
-- Aside, confirmed by running this file: the plugin's OWN query mechanism
-- (`mv ? enable` + `mv ? <expr>`) does NOT track `previous`/`show(n)`
-- correctly -- it always answers against the last *searched* solution
-- (i.e. whatever a `next` last computed), not the currently *displayed*
-- one, because UseScrollingKodkodModelValidator.previousSolution() and
-- .showSolution() only call createObjectDiagram(...) and never refresh
-- the query evaluator that `mv ?` reads from. Plain USE `?` queries
-- against the reconstructed system state are unaffected by that quirk,
-- which is why they are used here instead of `mv ?`.

mv -scrolling Employee.properties
? Employee.allInstances()->collect(e | e.salary)

-- Step forward to a second, distinct solution.
mv -scrolling next
? Employee.allInstances()->collect(e | e.salary)

-- Step forward again, to a third solution.
mv -scrolling next
? Employee.allInstances()->collect(e | e.salary)

-- Step back: reproduces the second solution's salary exactly (no new
-- search is performed -- note the absence of a "Searching solution..."
-- log line here, compared to the `next` calls above).
mv -scrolling previous
? Employee.allInstances()->collect(e | e.salary)

-- Step back again: reproduces the first solution's salary exactly.
mv -scrolling previous
? Employee.allInstances()->collect(e | e.salary)

-- Jump directly to the third solution found earlier, by index.
mv -scrolling show(3)
? Employee.allInstances()->collect(e | e.salary)

quit

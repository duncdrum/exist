# exist-architecture-tests

ArchUnit rules over the production code of exist-core and the index modules. The module holds test code only and is
not published. Its test classpath holds all modules under the rules, so a rule can see across module boundaries
(e.g. a `RewritableExpression.replace` call in the range index).

## Layout

- `ArchitectureTest` imports the classes once (`@AnalyzeClasses`) and includes each rule set with `ArchTests.in(...)`.
- One class per rule set, e.g. `OptimizerRules`. Add a set by writing a class of `@ArchTest` fields and including it in
  `ArchitectureTest`.
- To put another module's classes under the rules, add it as a `test` dependency in `pom.xml`, and to
  `ignoredUnusedDeclaredDependencies` if no rule references its classes by name.

## Frozen rules

Rules with existing violations are wrapped in `FreezingArchRule.freeze(...)`. Today's violations are recorded in
`src/test/resources/archunit_store`; only new violations fail.

- Give every frozen rule a fixed description with `.as("...")`: the store is keyed by it, and renaming a rule
  orphans its recorded violations.
- Keep violation messages free of line numbers, so that unrelated edits do not change the store.
- Fixing a recorded violation removes it from the store on the next run; commit the smaller store.
- A change that adds violations needs a reason in its pull request. Record them deliberately:

```bash
mvn test -pl exist-architecture-tests -am -Dtest=ArchitectureTest -Dsurefire.failIfNoSpecifiedTests=false \
  -Darchunit.freeze.refreeze=true -Ddependency-check.skip=true -Ddocker=false
```

- A new frozen rule needs a new store file; create it once with `-Darchunit.freeze.store.default.allowStoreCreation=true`
  (off in `archunit.properties`, so a missing store fails instead of silently recording everything).

Review a store diff like code: every added line is a violation someone accepted.

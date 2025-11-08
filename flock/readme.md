# Circular Economy Network Migration – Assignment Brief

## 1. Business Context

GreenLoop is upgrading its pilot circular-economy marketplace into a multi-partner network. Manufacturers (e.g., `AcmeCorp`), retailers (e.g., `RetailerOne`), and consumers (e.g., `Alice`, `Bob`) now expect:

- Unified views of all actors and transactions.
- Loyalty tiers derived from purchase/return activity.
- Facility performance dashboards with water/energy/CO₂ savings.
- Shipment and return events that logistics teams can audit.

This transformation is useful because it turns scattered, manufacturer-centric records into a structure that supports sustainability audits and carbon-aware logistics planning. Without it, the platform cannot surface combined KPIs or automate compliance reporting.

To launch this “Circular Economy Network,” you must infer the appropriate target metamodel from the requirements below, implement the migration script, which will produce the migrated instance model.

## 2. Learning Outcomes

By completing this assignment you should be able to:

1. Interpret metamodel diffs and justify why migrations are required in a realistic use-case.
2. Apply Flock constructs to restructure data.
3. Trace how migrated instances satisfy new business requirements (loyalty tiers, shipment events, facility profiles).
4. Communicate migrations through natural-language requirements, pre/post conditions, and verification steps.

## 3. Provided Assets

| File | Purpose |
| --- | --- |
| `original.emf` | Source metamodel describing the legacy CircularEconomy structure. |
| `original.flexmi` | Sample instance model to migrate (GreenLoop data). |
| `original-diagram.png` | Visual diagram of the original metamodel. |
| `original-diagram-epsilon.png` | Epsilon-generated diagram of the original metamodel. |
| `program.mig` | Starter Flock migration script (you must modify/replace this). |
| `migrated.xmi` | Expected migrated instance model (for verification). |
| `migrated-diagram.png` | Visual diagram of the expected migrated model (for verification). |
| `migrated-diagram-epsilon.png` | Epsilon-generated diagram of the expected migrated model (for verification). |
| `assignment description` (this README) | Requirements describing the desired target capabilities. |

You must develop the target metamodel from the requirements, implement your own `program.mig`, and generate a corresponding `migrated.xmi`. Do not rename the original namespace.

## 4. Migration Objectives

Base every implementation decision to the following objectives. They describe the target capabilities you must implement for model migration.

1. Replace the legacy `CircularEconomy` root with `CircularEconomyNetwork`, exposing a unified `actors` collection.  
2. Introduce an abstract `CircularActor` plus concrete `Producer`, `DistributionPartner`, and `Customer` types. Retype manufacturers, retailers, and consumers to these roles
3. Customers must carry a `LoyaltyTier` derived from their activity: at least one order or return → `PREMIUM`, otherwise `BASIC`.
4. Rename `SustainabilityGoal` to `SustainabilityMetric`, rename `standard` to `threshold`, and add a `unit` string attribute. Apply the rule `ReduceWaste → ppm`, every other metric → `wpm`.  
5. Facilities store references to these metrics rather than to goals.
6. Extend `Facility` with `operationalSince` (string, optional, default value: '10-12-1999') and a contained `FacilityProfile` summarizing category, water saved, energy saved, and CO₂ emissions aggregated from all the attached processes. Note that CO₂ emissions are computed as the sum of all shipments `co2Emitted` values associated with the processes'.
7. The facility category mirrors the original enum values.
8. Products keep their identifiers but expose `name`, `basePrice`, `assemblyDate`, `retailer` (now a `DistributionPartner`), and `parts` references to components.  
9. Components expose `identifier`, `name`, `recycled`, and `lifecycleState`. Map `READY → READY`, `ASSEMBLED → ASSEMBLED`, `IN_CIRCULAR_PROCESS → IN_LOOP`, defaulting to `IN_LOOP` when uncertain.
10. Refactor `Order` into an abstract superclass with `Purchase` (still referencing a product) and `ReturnOrder` (referencing the originating purchase and its circular processes).  
11. All orders own exactly one `LogisticsEvent`. Purchases spawn `PurchaseEvent`s (ID, date, delivered flag, optional delivery date, `date`, `deliveryDate`, `delivered` attributes are set with values as per the `migrated.xmi`). Returns create `ReturnEvent`s (ID, reason fixed as `Bad Quality`, `ReturnType`, `date`, `deliveryDate`, `delivered` attributes are set with values as per the `migrated.xmi`). Circular processes emit `ShipmentEvent`s (ID, human-readable name, shipment type default `AIR`, CO₂ value fixed at `20.0`, `date`, `deliveryDate`, `delivered` attributes are set with values as per the `migrated.xmi`) so logistics teams can audit each loop.
12. Preserve `RepairProcess`, `RefurbishProcess`, and `RecycleProcess` specializations. Each must reference the facility, the new shipment event, water/energy savings, and for recycle processes the migrated components. This guarantees every loop is traceable end-to-end.

**Precondition:** The input model conforms to `original.emf` and describes at least one manufacturer, retailer, consumer, order, return, and circular process (see `original.flexmi`). No loyalty tiers, facility profiles, or logistics events exist.

Output:

```bash
== Pre-migration snapshot ==
Consumers (no loyalty tier yet): 2
Orders missing logistics events: 1
Facilities without profiles: 3
Event counts (original model):
Shipments: 3
```

**Postcondition:** The migrated model conforms to the target specification in Section 4, with:
`CircularEconomyNetwork.actors` containing Producers, DistributionPartners, and Customers. Each Customer classified into a `LoyaltyTier`, every Order linked to a `PurchaseEvent`, every ReturnOrder linked to both a `ReturnEvent` and its originating Purchase. Facilities referencing `SustainabilityMetric`s and owning a `FacilityProfile`. Circular processes referencing `ShipmentEvent`s rather than raw `Shipment`s.

Output:

```bash
== Post-migration snapshot ==
Customers with loyalty tiers: 2
Total events: 5
Facilities enriched with profiles: 0
Event counts (migrated model):
PurchaseEvents: 4
ReturnEvents: 1
ShipmentEvents: 3
```

## 6. How to Approach the Assignment

1. **Analyze Artifacts**  
   - Study `original.emf` and `original.flexmi` to capture the original model structure and data.  
   - Sketch the target metamodel implied by Section 4.
   - Identify key transformation patterns (retyping, attribute mapping, new element creation, relationship restructuring).

2. **Design & Implement Migration**  
   - Author your own `program.mig` that satisfies the stated objectives; document any assumptions you make.  
   - Instrument the script with `pre {}` and `post {}` blocks that log state snapshots, including event counts by type before and after migration, so stakeholders can see why the transformation is useful.

3. **Execute Migration**  
   - Run your migration using Epsilon Flock (Eclipse, Maven `mvn compile exec:java`, or Epsilon Playground).  
   - Produce a migrated instance model conforming to the target you defined and include it in your submission.

4. **Verify Outcomes**  
   - Validate your migrated model against `migrated.xmi` or `migrated-diagram.png` or `migrated-diagram-epsilon.png`

5. **Reflect & Extend (Optional Bonus)**  
   - Suggest issues with the current transformations or areas for improvement.
   - Propose an additional KPI or logistics enhancement and describe how you would implement it in Flock (no need to code, but be specific).

## 7. Deliverables

| Item | Description |
| --- | --- |
| Migration Script(s) | Your authored `program.mig`, committed with explanatory comments. |
| Migrated Model | `.xmi` or `.flexmi` conforming to your inferred target metamodel. |
| Assignment Report | PDF/Markdown summarizing objectives, verification evidence, and reflections (include pre/post discussion). |
| Assets | Diagrams (e.g., `migrated-diagram.png`) or queries used for validation. |

## 8. Assessment Criteria

1. **Correctness (6)** – Model conforms to target metamodel; objectives 1–7 satisfied.  
2. **Completeness (2)** – All specified elements and relationships are migrated as per requirements.
3. **Quality of Code (1)** – Code is clean, readable and modularized (hint: try to use `operation` whereever useful).  
4. **Documentation (1)** – Clear explanations of design decisions, assumptions, and verification steps.
5. **Bonus (up to 1)** – Meaningful proposal for an additional KPI or logistics enhancement with clear rationale and implementation design.

## 9. Tooling & Execution Notes

- Requires Java 11+ and Maven (see `pom.xml`). Run `mvn compile exec:java` to execute the migration runner.  
- Ensure Epsilon dependencies match the provided POM; do not upgrade without documenting the rationale.  
- If running in Eclipse, register both metamodels (nsURI `ce`) before executing the Flock script.

## 10. Reference Links

- [Review Epsilon Flock docs](https://www.eclipse.org/epsilon/doc/flock/)
- [Epsilon Object Language (EOL) reference](https://www.eclipse.org/epsilon/doc/eol/)
- [Epsilon Flock Playground](https://eclipse.dev/epsilon/playground/?flock)

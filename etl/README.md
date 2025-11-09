# ETL Model Transformations

## 1. Context

This assignment introduces model-to-model transformations using the [Epsilon Transformation Language (ETL)](https://eclipse.dev/epsilon/doc/etl/). You will work with two independent scenarios that reflect common transformation patterns: (1) extracting traceability information, and (2) generating aggregated sustainability reports. Each scenario provides source and target metamodels, example source models, and expected target models.

Your task is to design, implement, and execute ETL transformations that convert the given source models into the corresponding target models.


## 2. Learning Outcomes

By completing this assignment you should be able to:
1. Understand and analyse EMF-based metamodels.
2. Derive transformation rules and helper operations for ETL.
3. Implement model-to-model transformations following structural and semantic mapping requirements.
4. Execute and validate ETL transformations using Maven or the Epsilon Playground.
5. Compare produced artefacts against expected results and refine transformation logic.


## 3. Provided Assets

Two transformation scenarios are provided: `circularFlowTrace/` and `sustainabilityReport/`.

Each scenario contains the following files:

| File                          | Purpose                                                                 |
|------------------------------|--------------------------------------------------------------------------|
| `source.emf`                 | Source metamodel (i.e., CircularEconomy).                                |
| `source.flexmi`              | Source model instance conforming to the source metamodel.                |
| `target.emf`                 | Target metamodel.                                                         |
| `target_expected.xmi`        | Expected target model after applying the transformation.                 |
| `target-model-diagram_expected.png` | Visual representation of the expected target model.              |
| `program.etl`                | ETL transformation file to be completed by you.                          |


After executing your transformation, a `target.xmi` file is produced. You can compare it with the expected output file (e.g. using `diff`).



## 4. Transformation Objectives

### 4.1 Circular Flow Trace

This scenario focuses on producing a traceability document describing circular processes triggered by product returns. The transformation extracts returns, processes, shipment data, participants, and component details. It also aggregates global counts into a summary section. The task requires mapping hierarchical structures, handling optional data, and generating human-readable labels.

| Source Concept | Target Concept | Description |
| --- | --- | --- |
| `CircularEconomy` | `TraceDocument` + `Summary` | The trace document inherits the source `name` and appends the “_FlowTrace” suffix. The contained summary aggregates global counts of repair, refurbish, and recycle processes by counting the occurrences of each process type across the model. |
| `Return` | `FlowRecord` | Each return produces one flow record with an identifier (`id`) derived from the return id, the consumer’s name, and the product’s serial number. The record copies the return reason or inserts a default placeholder if it is absent. The flow record includes the corresponding circular process and collects participant references for the consumer, retailer, manufacturer, and facility involved. |
| `CircularProcess` (`RepairProcess`, `RefurbishProcess`, `RecycleProcess`) | `ProcessTrace` (`RepairTrace`, `RefurbishTrace`, `RecycleTrace`) | Each process contributes a corresponding process trace. They share  attributes such as `facilityName`, `energySaved`, `waterSaved`, and  `delivered`. Specialized traces introduce process-specific labels. Recycle traces additionally collect component traces and compute counts of recycled vs. nonRecycled components for the label. |
| `Component` | `ComponentTrace` | Each component referenced by a recycle process is transformed into a component trace. The trace records the `componentId`, `componentName`, and `recycled` flag. The component state is expressed as a formatted string that normalises the enumeration. |
| `Actor` (`Consumer`, `Retailer`, `Manufacturer`) and `Facility` | `ParticipantReference` | Each participant is represented by a participant reference containing the entity’s `name` and a `type` tag indicating whether it is a consumer, retailer, manufacturer, or facility. |


### 4.2 Sustainability Report

The second scenario aggregates sustainability-related indicators per facility and per product. Processes, shipments, and sustainability goals must be analysed to compute facility-level metrics, evaluate compliance, and summarise product characteristics. The transformation involves numerical aggregation, conditional evaluation, and ID generation.

| Source Concept | Target Concept | Description |
| --- | --- | --- |
| `CircularEconomy` | `Report` | The report inherits the source name, assigns a timestamp to `generatedOn` (using the current date), and sets a fixed `version` value (“v1.0”). The attribute `totalCO2` is computed by summing the `co2Emission` values of all shipments (including not shipped) in the model. |
| `Facility` | `FacilityReport` | Each facility produces one facility report identified by a generated and auto-incremented `facilityReportId`. The `facilityName` combines the facility’s name and type. All circular processes referencing the facility contribute to aggregated metrics: total CO₂ emissions from associated shipments (`co2`), total energy saved (`energySavedKWh`), and total water saved (`waterSavedL`). In addition shipment counts (total, delivered, not delivered) are computed. |
| `SustainabilityGoal` | `GoalResult` | For each goal referenced by a facility, a goal result is created. It records the goal’s `name`, `standard`, and `comparator`. The `actual` value is derived by selecting the relevant aggregated metric (energy, water, or CO₂) based on the goal’s naming convention. Compliance is evaluated by applying the semantic of the comparator (`MIN` or `MAX`) to the `standard` and the computed `actual` value. |
| `Product` | `ProductSummary` | Each product yields a summary containing a generated and auto-incremented `productSummaryId`, the product’s `serialNumber`, `price` expressed as a string with a currency suffix. In addition the number of associated components is recorded (`componentCount`) and how many are recycled (`recycledComponentCount`). |


## 5. How to Approach the Assignment

1. **Analyze Artifacts**: Study both the source and target metamodels to understand the structural differences. Examine the source model and identify the elements that must be transferred, aggregated, or reformatted.

2. **Design & Implement Transformation**: Implement your ETL logic in `program.etl`. Use helper operations where appropriate. Ensure that rule mappings reflect all required structural correspondences.

3. **Execute Transformation**: Run the transformation via the provided Maven script (`mvn compile exec:java`). Alternatively, use the [Epsilon Playground](https://eclipse.dev/epsilon/playground/) for interactive testing. Verify that a `target.xmi` file is produced.

4. **Verify Transformation**: Compare your generated output with the expected target model. Minor differences (e.g. in ordering or with current dates) are acceptable.


## 6. Deliverables

| Deliverable                     | Description                                                     |
|--------------------------------|-----------------------------------------------------------------|
| Transformation Scripts         | Completed `program.etl` files for both scenarios.               |
| Transformed Model              | Generated `target.xmi` instances conforming to the target metamodels. |
| Assets                         | Optional diagrams such as `target-model-diagram.png`. |

## 7. Tooling & Execution Notes

The assignment requires Java 11+ and Maven.   

To run a scenario, navigate into either `circularFlowTrace/` or `sustainabilityReport/` and execute:

```bash
# install dependencies
mvn install

# compile and execute transformation
mvn compile exec:java
```

Note that the build script must be executed from within the respective scenario directory.


## 8. References

- [Emfatic](https://eclipse.dev/emfatic/)
- [Epsilon Object Language (EOL)](https://eclipse.dev/epsilon/doc/eol/)
- [Epsilon Transformation Language (ETL)](https://eclipse.dev/epsilon/doc/etl/)



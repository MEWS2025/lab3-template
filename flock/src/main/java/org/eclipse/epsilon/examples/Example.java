package org.eclipse.epsilon.examples;

import java.io.File;

import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.emfatic.core.EmfaticResourceFactory;
import org.eclipse.epsilon.XmiVisualizer;
import org.eclipse.epsilon.emc.emf.EmfModel;
import org.eclipse.epsilon.flexmi.FlexmiResourceFactory;
import org.eclipse.epsilon.flock.FlockModule;

public class Example {

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");

        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("flexmi", new FlexmiResourceFactory());
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("emf", new EmfaticResourceFactory());

        FlockModule module = new FlockModule();
        module.parse(new File("program.mig"));

        EmfModel original = new EmfModel();
        original.setName("Original");
        original.setModelFile(new File("original.flexmi").getAbsolutePath());
        original.setMetamodelFile(new File("original.emf").getAbsolutePath());
        original.setReadOnLoad(true);
        original.setStoredOnDisposal(false);
        original.load();

        EmfModel migrated = new EmfModel();
        migrated.setName("Migrated");
        migrated.setModelFile(new File("migrated.xmi").getAbsolutePath());
        migrated.setMetamodelFile(new File("migrated.emf").getAbsolutePath());
        migrated.setReadOnLoad(false);
        migrated.setStoredOnDisposal(true);
        migrated.load();

        module.getContext().getModelRepository().addModel(original);
        module.getContext().getModelRepository().addModel(migrated);
        module.getContext().setOriginalModel(original);
        module.getContext().setMigratedModel(migrated);

        module.execute();
        migrated.getResource().save(null);
        module.getContext().getModelRepository().dispose();

        File pngFile = new File("migrated-diagram.png");
        File plantUmlFile = new File("migrated-diagram.puml");
        XmiVisualizer.render(new File("migrated.emf"), new File("migrated.xmi"), pngFile, plantUmlFile);

        System.out.println("Diagram exported to: " + pngFile.getAbsolutePath());
        System.out.println("PlantUML exported to: " + plantUmlFile.getAbsolutePath());
    }
}

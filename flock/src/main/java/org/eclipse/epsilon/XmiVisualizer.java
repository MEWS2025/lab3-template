package org.eclipse.epsilon;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.imageio.ImageIO;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;

public final class XmiVisualizer {

    private static final int H_PADDING = 18;
    private static final int V_PADDING = 14;
    private static final int HEADER_GAP = 10;
    private static final int LINE_SPACING = 4;
    private static final int LEVEL_SPACING = 90;
    private static final int SIBLING_SPACING = 70;
    private static final int ROOT_SPACING = 90;
    private static final int MARGIN = 60;

    private XmiVisualizer() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("Usage: XmiVisualizer <metamodel.ecore> <model.xmi> <diagram.png> [diagram.puml]");
            return;
        }

        File ecoreFile = new File(args[0]);
        File xmiFile = new File(args[1]);
        File pngFile = new File(args[2]);
        File plantUmlFile = args.length > 3 ? new File(args[3]) : derivePlantUmlFile(pngFile);

        render(ecoreFile, xmiFile, pngFile, plantUmlFile);
        System.out.println("Diagram exported to: " + pngFile.getAbsolutePath());
        System.out.println("PlantUML exported to: " + plantUmlFile.getAbsolutePath());
    }

    public static void render(File ecoreFile, File xmiFile, File pngFile) throws Exception {
        render(ecoreFile, xmiFile, pngFile, derivePlantUmlFile(pngFile));
    }

    public static void render(File ecoreFile, File xmiFile, File pngFile, File plantUMLFile) throws Exception {
        Objects.requireNonNull(ecoreFile, "ecoreFile");
        Objects.requireNonNull(xmiFile, "xmiFile");
        Objects.requireNonNull(pngFile, "pngFile");
        Objects.requireNonNull(plantUMLFile, "plantUMLFile");

        ResourceSet resourceSet = new ResourceSetImpl();
        registerFactories(resourceSet);
        registerPackages(resourceSet, ecoreFile);

        Resource modelResource = resourceSet.getResource(URI.createFileURI(xmiFile.getAbsolutePath()), true);

        Map<EObject, DiagramNode> nodeIndex = new IdentityHashMap<>();
        Map<String, Color> colorIndex = new HashMap<>();
        List<DiagramNode> roots = new ArrayList<>();
        for (EObject rootObject : modelResource.getContents()) {
            roots.add(buildNode(rootObject, nodeIndex, colorIndex));
        }

        List<DiagramEdge> containmentEdges = collectContainmentEdges(roots);
        List<DiagramEdge> crossReferences = collectCrossReferences(nodeIndex);

        renderDiagram(roots, containmentEdges, crossReferences, pngFile);
        writePlantUml(nodeIndex.values(), containmentEdges, crossReferences, plantUMLFile);
    }

    private static void registerFactories(ResourceSet resourceSet) {
        resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("ecore", new XMIResourceFactoryImpl());
        resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("xmi", new XMIResourceFactoryImpl());
    }

    private static void registerPackages(ResourceSet resourceSet, File ecoreFile) throws IOException {
        Resource ecoreResource = resourceSet.getResource(URI.createFileURI(ecoreFile.getAbsolutePath()), true);
        for (EObject root : ecoreResource.getContents()) {
            if (root instanceof EPackage ePackage) {
                registerPackage(resourceSet, ePackage);
            }
        }
    }

    private static void registerPackage(ResourceSet resourceSet, EPackage ePackage) {
        if (ePackage.getNsURI() != null) {
            resourceSet.getPackageRegistry().put(ePackage.getNsURI(), ePackage);
        }
        for (EPackage sub : ePackage.getESubpackages()) {
            registerPackage(resourceSet, sub);
        }
    }

    private static DiagramNode buildNode(EObject eObject, Map<EObject, DiagramNode> index,
            Map<String, Color> colorIndex) {
        if (index.containsKey(eObject)) {
            return index.get(eObject);
        }

        DiagramNode node = new DiagramNode();
        node.eObject = eObject;
        node.title = ":" + eObject.eClass().getName();
        node.fillColor = lookupColor(eObject.eClass().getName(), colorIndex);

        for (EAttribute attribute : eObject.eClass().getEAllAttributes()) {
            Object value = eObject.eGet(attribute);
            if (value == null) {
                continue;
            }
            String rendered = renderAttributeValue(value);
            if (rendered.isEmpty()) {
                continue;
            }
            node.lines.add(attribute.getName() + " = " + rendered);
        }

        index.put(eObject, node);
        for (EObject child : eObject.eContents()) {
            DiagramNode childNode = buildNode(child, index, colorIndex);
            childNode.parent = node;
            childNode.containmentName = child.eContainmentFeature() != null ? child.eContainmentFeature().getName() : "";
            node.children.add(childNode);
        }

        return node;
    }

    private static String renderAttributeValue(Object value) {
        if (value instanceof Collection<?> collection) {
            List<String> parts = new ArrayList<>();
            for (Object element : collection) {
                if (element != null) {
                    parts.add(element.toString());
                }
            }
            return String.join(", ", parts);
        }
        return value.toString();
    }

    private static List<DiagramEdge> collectContainmentEdges(List<DiagramNode> roots) {
        List<DiagramEdge> edges = new ArrayList<>();
        for (DiagramNode root : roots) {
            collectContainmentEdges(root, edges);
        }
        return edges;
    }

    private static void collectContainmentEdges(DiagramNode node, List<DiagramEdge> edges) {
        for (DiagramNode child : node.children) {
            edges.add(DiagramEdge.of(node, child, child.containmentName, false));
            collectContainmentEdges(child, edges);
        }
    }

    private static List<DiagramEdge> collectCrossReferences(Map<EObject, DiagramNode> index) {
        List<DiagramEdge> edges = new ArrayList<>();
        for (Map.Entry<EObject, DiagramNode> entry : index.entrySet()) {
            EObject source = entry.getKey();
            DiagramNode sourceNode = entry.getValue();
            EClass eClass = source.eClass();

            for (EReference reference : eClass.getEAllReferences()) {
                if (reference.isContainment()) {
                    continue;
                }
                Object value = source.eGet(reference);
                if (value instanceof EObject target) {
                    DiagramNode targetNode = index.get(target);
                    if (targetNode != null) {
                        edges.add(DiagramEdge.of(sourceNode, targetNode, reference.getName(), true));
                    }
                } else if (value instanceof Collection<?> collection) {
                    for (Object element : collection) {
                        if (element instanceof EObject target) {
                            DiagramNode targetNode = index.get(target);
                            if (targetNode != null) {
                                edges.add(DiagramEdge.of(sourceNode, targetNode, reference.getName(), true));
                            }
                        }
                    }
                }
            }
        }
        return edges;
    }

    private static void renderDiagram(List<DiagramNode> roots, List<DiagramEdge> containments,
            List<DiagramEdge> references, File output) throws IOException {
        if (roots.isEmpty()) {
            throw new IllegalArgumentException("No root objects found to render.");
        }

        BufferedImage scratch = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D scratchGraphics = scratch.createGraphics();
        configureGraphics(scratchGraphics);

        Font titleFont = new Font("SansSerif", Font.BOLD, 18);
        Font bodyFont = new Font("SansSerif", Font.PLAIN, 16);
        FontMetrics titleMetrics = scratchGraphics.getFontMetrics(titleFont);
        FontMetrics bodyMetrics = scratchGraphics.getFontMetrics(bodyFont);

        for (DiagramNode root : roots) {
            measureNode(root, titleMetrics, bodyMetrics);
        }

        int totalWidth = 0;
        int maxHeight = 0;
        for (DiagramNode root : roots) {
            totalWidth += root.subtreeWidth;
            maxHeight = Math.max(maxHeight, root.subtreeHeight);
        }
        totalWidth += ROOT_SPACING * (roots.size() - 1);

        int imageWidth = totalWidth + MARGIN * 2;
        int imageHeight = maxHeight + MARGIN * 2;

        BufferedImage image = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        configureGraphics(g);
        g.setColor(Color.decode("#F5F5F5"));
        g.fillRect(0, 0, imageWidth, imageHeight);

        int cursorX = MARGIN;
        int startY = MARGIN;
        for (DiagramNode root : roots) {
            layoutNode(root, cursorX, startY);
            cursorX += root.subtreeWidth + ROOT_SPACING;
        }

        g.setStroke(new BasicStroke(2f));
        g.setColor(new Color(120, 120, 120));
        for (DiagramEdge edge : containments) {
            drawContainment(g, edge);
        }

        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, new float[] { 10f, 10f }, 0f));
        g.setColor(new Color(90, 120, 160));
        for (DiagramEdge edge : references) {
            drawReference(g, edge);
        }

        for (DiagramNode root : roots) {
            drawNode(g, root, titleFont, bodyFont, titleMetrics, bodyMetrics);
        }

        g.dispose();
        ImageIO.write(image, "PNG", output);
    }

    private static void measureNode(DiagramNode node, FontMetrics titleMetrics, FontMetrics bodyMetrics) {
        int titleWidth = titleMetrics.stringWidth(node.title);
        int maxLineWidth = titleWidth;
        for (String line : node.lines) {
            maxLineWidth = Math.max(maxLineWidth, bodyMetrics.stringWidth(line));
        }
        node.width = maxLineWidth + H_PADDING * 2;

        int titleHeight = titleMetrics.getHeight();
        int bodyHeight = node.lines.isEmpty() ? 0
                : node.lines.size() * bodyMetrics.getHeight() + (node.lines.size() - 1) * LINE_SPACING;
        node.height = V_PADDING * 2 + titleHeight + (node.lines.isEmpty() ? 0 : HEADER_GAP + bodyHeight);

        int childrenWidth = 0;
        int maxChildHeight = 0;
        for (DiagramNode child : node.children) {
            measureNode(child, titleMetrics, bodyMetrics);
            childrenWidth += child.subtreeWidth;
            maxChildHeight = Math.max(maxChildHeight, child.subtreeHeight);
        }
        if (!node.children.isEmpty()) {
            childrenWidth += SIBLING_SPACING * (node.children.size() - 1);
        }

        node.subtreeWidth = Math.max(node.width, childrenWidth);
        node.subtreeHeight = node.height;
        if (!node.children.isEmpty()) {
            node.subtreeHeight += LEVEL_SPACING + maxChildHeight;
        }
    }

    private static void layoutNode(DiagramNode node, int x, int y) {
        node.x = x + (node.subtreeWidth - node.width) / 2;
        node.y = y;

        if (node.children.isEmpty()) {
            return;
        }

        int childX = x + (node.subtreeWidth - totalChildrenWidth(node)) / 2;
        int childY = node.y + node.height + LEVEL_SPACING;
        for (DiagramNode child : node.children) {
            layoutNode(child, childX, childY);
            childX += child.subtreeWidth + SIBLING_SPACING;
        }
    }

    private static int totalChildrenWidth(DiagramNode node) {
        if (node.children.isEmpty()) {
            return 0;
        }
        int width = 0;
        for (DiagramNode child : node.children) {
            width += child.subtreeWidth;
        }
        width += SIBLING_SPACING * (node.children.size() - 1);
        return width;
    }

    private static void drawContainment(Graphics2D g, DiagramEdge edge) {
        DiagramNode parent = edge.source();
        DiagramNode child = edge.target();
        drawArrow(g, parent.getBottomCenterX(), parent.getBottomCenterY(), child.getTopCenterX(), child.y);
    }

    private static void drawReference(Graphics2D g, DiagramEdge edge) {
        DiagramNode source = edge.source();
        DiagramNode target = edge.target();
        int x1 = source.getRightCenterX();
        int y1 = source.getRightCenterY();
        int x2 = target.getLeftCenterX();
        int y2 = target.getLeftCenterY();

        g.drawLine(x1, y1, x2, y2);
        drawArrowHead(g, x1, y1, x2, y2);

        int labelX = (x1 + x2) / 2;
        int labelY = (y1 + y2) / 2 - 6;
        Font original = g.getFont();
        g.setFont(original.deriveFont(Font.PLAIN, 14f));
        g.drawString(edge.label(), labelX, labelY);
        g.setFont(original);
    }

    private static void drawNode(Graphics2D g, DiagramNode node, Font titleFont, Font bodyFont, FontMetrics titleMetrics,
            FontMetrics bodyMetrics) {
        Color base = node.fillColor != null ? node.fillColor : new Color(232, 242, 250);
        Color bodyColor = lighten(base, 0.25);
        Color headerColor = darken(base, 0.15);
        Color borderColor = darken(base, 0.35);
        Color headerTextColor = readableTextColor(headerColor);
        Color bodyTextColor = readableTextColor(bodyColor);

        g.setColor(bodyColor);
        g.fillRect(node.x, node.y, node.width, node.height);

        int titleBottom = node.y + V_PADDING + titleMetrics.getHeight();
        int headerHeight = Math.min(node.height, titleBottom - node.y);
        if (headerHeight > 0) {
            g.setColor(headerColor);
            g.fillRect(node.x, node.y, node.width, headerHeight);
        }

        g.setColor(borderColor);
        g.setStroke(new BasicStroke(1.8f));
        g.drawRect(node.x, node.y, node.width, node.height);

        if (!node.lines.isEmpty()) {
            g.drawLine(node.x, titleBottom, node.x + node.width, titleBottom);
        }

        g.setFont(titleFont);
        int titleBaseline = node.y + V_PADDING + titleMetrics.getAscent();
        g.setColor(headerTextColor);
        g.drawString(node.title, node.x + H_PADDING, titleBaseline);

        g.setFont(bodyFont);
        g.setColor(bodyTextColor);
        int bodyBaseline = titleBottom + HEADER_GAP + bodyMetrics.getAscent();
        for (String line : node.lines) {
            g.drawString(line, node.x + H_PADDING, bodyBaseline);
            bodyBaseline += bodyMetrics.getHeight() + LINE_SPACING;
        }

        for (DiagramNode child : node.children) {
            drawNode(g, child, titleFont, bodyFont, titleMetrics, bodyMetrics);
        }
    }

    private static void drawArrow(Graphics2D g, int x1, int y1, int x2, int y2) {
        g.drawLine(x1, y1, x2, y2);
        drawArrowHead(g, x1, y1, x2, y2);
    }

    private static void drawArrowHead(Graphics2D g, int x1, int y1, int x2, int y2) {
        double angle = Math.atan2(y2 - y1, x2 - x1);
        int arrowLength = 14;
        int sideAngle = 30;

        Polygon arrowHead = new Polygon();
        arrowHead.addPoint(x2, y2);
        arrowHead.addPoint((int) Math.round(x2 - arrowLength * Math.cos(angle - Math.toRadians(sideAngle))),
                (int) Math.round(y2 - arrowLength * Math.sin(angle - Math.toRadians(sideAngle))));
        arrowHead.addPoint((int) Math.round(x2 - arrowLength * Math.cos(angle + Math.toRadians(sideAngle))),
                (int) Math.round(y2 - arrowLength * Math.sin(angle + Math.toRadians(sideAngle))));
        g.fillPolygon(arrowHead);
    }

    private static void configureGraphics(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }

    private static Color lookupColor(String classifierName, Map<String, Color> colorIndex) {
        return colorIndex.computeIfAbsent(classifierName, _ -> paletteColor(colorIndex.size()));
    }

    private static Color lighten(Color color, double factor) {
        int red = (int) Math.round(color.getRed() + (255 - color.getRed()) * factor);
        int green = (int) Math.round(color.getGreen() + (255 - color.getGreen()) * factor);
        int blue = (int) Math.round(color.getBlue() + (255 - color.getBlue()) * factor);
        return new Color(clamp(red), clamp(green), clamp(blue));
    }

    private static Color darken(Color color, double factor) {
        int red = (int) Math.round(color.getRed() * (1.0 - factor));
        int green = (int) Math.round(color.getGreen() * (1.0 - factor));
        int blue = (int) Math.round(color.getBlue() * (1.0 - factor));
        return new Color(clamp(red), clamp(green), clamp(blue));
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static Color readableTextColor(Color background) {
        double luminance = (0.299 * background.getRed() + 0.587 * background.getGreen()
                + 0.114 * background.getBlue()) / 255d;
        if (luminance > 0.6) {
            return new Color(45, 45, 45);
        } else {
            return new Color(245, 245, 245);
        }
    }

    private static File derivePlantUmlFile(File pngFile) {
        String baseName = stripExtension(pngFile.getName());
        File directory = pngFile.getParentFile() != null ? pngFile.getParentFile() : new File(".");
        return new File(directory, baseName + ".puml");
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot == -1 ? name : name.substring(0, dot);
    }

    private static void writePlantUml(Collection<DiagramNode> nodesCollection, List<DiagramEdge> containments,
            List<DiagramEdge> references, File output) throws IOException {
        List<DiagramNode> nodes = new ArrayList<>(nodesCollection);
        nodes.sort((left, right) -> left.title.compareToIgnoreCase(right.title));

        Map<DiagramNode, String> ids = new IdentityHashMap<>();
        for (int i = 0; i < nodes.size(); i++) {
            ids.put(nodes.get(i), "C" + i);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("@startuml\n");
        sb.append("skinparam shadowing false\n");
        sb.append("skinparam classAttributeIconSize 0\n");
        sb.append("hide empty members\n");

        for (DiagramNode node : nodes) {
            sb.append("class ").append(ids.get(node)).append(" as \"")
                    .append(node.title.replace("\"", "\\\"")).append("\\n");
            for (String line : node.lines) {
                sb.append(line.replace("\"", "\\\"")).append("\\n");
            }
            sb.append("\" {\n}\n");
        }

        for (DiagramEdge edge : containments) {
            sb.append(ids.get(edge.source())).append(" --> ").append(ids.get(edge.target()));
            if (!edge.label().isEmpty()) {
                sb.append(" : ").append(edge.label());
            }
            sb.append("\n");
        }

        for (DiagramEdge edge : references) {
            sb.append(ids.get(edge.source())).append(edge.dashed() ? " ..> " : " --> ")
                    .append(ids.get(edge.target()));
            if (!edge.label().isEmpty()) {
                sb.append(" : ").append(edge.label());
            }
            sb.append("\n");
        }

        sb.append("@enduml\n");

        if (output.getParentFile() != null) {
            output.getParentFile().mkdirs();
        }
        try (var writer = new java.io.FileWriter(output)) {
            writer.write(sb.toString());
        }
    }

    private static Color paletteColor(int index) {
        if (index < PALETTE.length) {
            return PALETTE[index];
        }
        double goldenRatioConjugate = 0.6180339887498949;
        double hue = (index - PALETTE.length) * goldenRatioConjugate;
        float h = (float) (hue - Math.floor(hue));
        return Color.getHSBColor(h, 0.42f, 0.92f);
    }

    private static final Color[] PALETTE = new Color[] { new Color(157, 212, 218), new Color(253, 243, 196),
            new Color(209, 224, 180), new Color(215, 205, 233), new Color(252, 219, 203), new Color(201, 229, 242) };

    private static final class DiagramNode {
        EObject eObject;
        final List<String> lines = new ArrayList<>();
        final List<DiagramNode> children = new ArrayList<>();
        String title;
        String containmentName = "";
        DiagramNode parent;
        Color fillColor;
        int width;
        int height;
        int subtreeWidth;
        int subtreeHeight;
        int x;
        int y;

        int getBottomCenterX() {
            return x + width / 2;
        }

        int getBottomCenterY() {
            return y + height;
        }

        int getTopCenterX() {
            return x + width / 2;
        }

        int getLeftCenterX() {
            return x;
        }

        int getLeftCenterY() {
            return y + height / 2;
        }

        int getRightCenterX() {
            return x + width;
        }

        int getRightCenterY() {
            return y + height / 2;
        }
    }

    private record DiagramEdge(DiagramNode source, DiagramNode target, String label, boolean dashed) {
        static DiagramEdge of(DiagramNode source, DiagramNode target, String label, boolean dashed) {
            return new DiagramEdge(source, target, label == null ? "" : label, dashed);
        }
    }
}
